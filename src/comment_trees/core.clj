(ns comment-trees.core
  (:require [comment-trees.db :as db]
            [comment-trees.comment-parser :as parser]
            [comment-trees.comment-parser :as report]
            [overtone.at-at :as atat]
            [config.core :refer [env]]
            [clojure.tools.logging :as log]))

; TODO: next up
; - let each post scanner maintain its own scan-data rather than having one singular atom: 
;   they're not sharing state so there's no reason 12 scanning posts need to keep retrying their swaps
; - look at places MERGE can be used to simplify SQL
; - take another pass at collapsing several serial SQL statements into one compound statement
; - if all else fails, look at H2 serializable mode or clojure agents to guard against race conditions
; - refactor functions into more compact namespaces, reorganize functions within existing namespaces
; - resolve other misc TODOs in the code
; - testing

; TODO: after
; - [removed] detection: stop scanning when a post has been removed and stops getting activity
; - active post detection: do a count of the scan data before merge, do a count after, and if
;   the difference is greater than a certain threshold, keep scanning

(def url-pre "https://www.reddit.com")

(def scan-data
  "The current tree of comment data across multiple posts that future tasks
   will periodically update. Each polling thread for a post must dissoc its
   post data when storing it to the DB."
  (atom {}))
(def post-thread-pool (atat/mk-pool))

; TODO: error checking on sub/feed config values
(def subreddit (:scanned-sub env))
(def feed (:feed env))
(def url-subreddit-feed (str url-pre subreddit feed ".json"))
(def max-scans (:max-scans env))
(def time-between-scans (:time-between-scans-ms env))

(defn compute-and-store-totals
  [comments]
  (db/store-all-totals (parser/compute-totals comments)))

(defn- finish-post
  [id comments-tree]
  (let [id-key (keyword id)]
    (log/info "finishing post:" id-key)
    (compute-and-store-totals comments-tree)
    (swap! scan-data dissoc id-key)))

(defn- stop-scanning
  [id-key]
  (run! atat/stop
        (filter #(= (:desc %) id-key)
                (atat/scheduled-jobs post-thread-pool))))

(defn- scan-post
  [id url]
  (try
    ; TODO: gracefully handle posts that return an error
    ; (because let's face it: reddit fails to return due to load all the time)
    (let [id-key (keyword id)
          existing-comments (get-in @scan-data [id-key :comments] {})
          scans (get-in @scan-data [id-key :scans] 0)]
      (log/infof "scanning post %s [scan %d]: %s" id scans url)
      (let [merged-comments (parser/get-and-merge-comments url existing-comments)]
        (if (< (inc scans) max-scans)
          (swap! scan-data assoc id-key {:scans    (inc scans)
                                         :comments merged-comments})
          (do
            (stop-scanning id-key)
            (finish-post id merged-comments)))))
    (catch Exception e
      (log/error "failure to scan-post:" e))))

(defn- start-post-worker
  [id url]
  (try
    (atat/every time-between-scans
                #(scan-post id url)
                post-thread-pool
                :initial-delay 200
                :desc (keyword id))
    (catch Exception e
      (log/error "failure to start-post-worker:" e))))

(defn- setup-post-worker
  [{:keys [id permalink title]}]
  (db/do-transaction
    (fn [tx]
      (when-not (db/is-post-registered id tx)
        (do
          (log/infof "setting up scans for post [%s]: %s" id title)
          (try
            (db/register-post id tx)
            (start-post-worker id (str url-pre permalink))
            (catch Exception e
              (log/error "failure to setup-post-worker:" e))))))))

(defn- scan-feed []
  (log/infof "scanning %s%s" subreddit feed)
  (try
    (doseq [post (parser/get-posts-in-sub url-subreddit-feed)]
      (try
        (setup-post-worker post)
        (catch Exception e
          (log/error "failed to start post worker for" (:id post) ":" e))))
    (catch Exception e
      (log/error "failed to get posts in subreddit" url-subreddit-feed ":" e))))

(defn- start-scanning-feed []
  (atat/every time-between-scans
              scan-feed
              post-thread-pool
              :initial-delay 500
              :desc "new feed scanner"))

(defn- stop-scanning-new []
  (map atat/stop
       (filter #(= (:desc %) "new feed scanner")
               (atat/scheduled-jobs post-thread-pool))))

(defn- shut-down []
  "Stops scanning a feed, stops all post scanners, and clears registration for posts 
   being scanned so that the next start-up can start scanning them."
  (let [running-posts (keys @scan-data)]
    (stop-scanning-new)
    (run! stop-scanning running-posts)
    (db/do-transaction
      (fn [tx]
        (run! #(db/deregister-post (name %) tx) running-posts)))
    (run! #(swap! scan-data dissoc %) running-posts)))

(defn -main
  [& args]
  (start-scanning-feed))
