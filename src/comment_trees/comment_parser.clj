(ns comment-trees.comment-parser
  (:require [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.walk :refer [walk]]
            [config.core :refer [env]]
            [clojure.tools.logging :as log]))

(def headers {"User-agent" (:user-agent env)})

(defn- get-json-content
  "Retrieves the JSON at the given URL, and parses it into a map."
  [url]
  ;(log/debug "getting URL" url)
  (let [response (client/get url {:headers headers})]
    (if (= (:status response) 200)
      (-> response :body (cheshire/parse-string true))
      (do
        (log/error "failed to read:" url)
        (log/error "read failure cause:" (:body response))))))

(defn get-posts-in-sub
  [sub-url]
  (let [posts (-> sub-url get-json-content (get-in [:data :children]))]
    (when (some? posts)
      (map #(:data %) posts))))

(defn- url->json-url
  "Takes a post url, extracts the relevant info, and returns a full URL
   to fetch the post JSON."
  [url]
  (str (subs url 0 (- (count url) 1)) ".json"))

(defn get-comments-json
  "Given a post url, gets the JSON for the comments."
  [url]
  ; The body is returned as an array, with the first element containing the
  ; post data and the comments in the second
  (let [results (get-json-content (url->json-url url))]
    (when (some? results)
      (second results))))

; structure of fields of note in comment JSON:
;
; Each comment root:
; :data -> :children -> [] of maps, one map per comment
;   each comment:
;     :kind -> "Listing"
;     :data -> {:id (unique string),
;               :author,
;               :body,
;               :replies -> another comment root or nil,
;               :children, (may not be present)
;               :parent_id (may not be useful) }
;
; If kind is listed as "more", reddit didn't return all comments. There are two
; additonal cases:
; 1. Comment :data -> :id is "_" ... This indiciates a "continue thread" link,
;    where the parent_id can be used to create a permalink to get more
;    comments.
; 2. :data -> :children -> [] of strings ... This indicates a "load more
;    comments" situation, where each element is an ID that can be used to create
;    a permalink to get more comments.
;
; After the transformation, each comment looks like:
; { [unique id as a keyword] -> { :author  -> [author string]
;                                 :body    -> [comment content]
;                                 :replies -> [map of reply comments or nil] }
;
; So a trivial example of three comments might look like:
; {:c1 {:author  "Alice",
;       :replies {:c2 {:author  "Bob",
;                      :replies nil}
;                 :c3 {:author "Carol",
;                      :replies nil}}}}
;
; Keying by ID makes computing the totals a bit interesting, but it makes it
; fairly easy to merge successive scans of the comment tree in a post, so that
; [deleted] and [removed] can be accounted for. If the above example was scanned
; a second time and Alice deleted their comment, but now it's also found that
; Craig replied to Carol, the complete thread structure can still be recreated
; by a conditional deep-merge of the scans, as :c1 still exists in the tree with
; an author of [deleted].

(defn- merge-map
  [f col]
  (apply merge (map f col)))

(defn- continue-thread
  [f url parent-id]
  (-> (str url (subs parent-id 3 (count parent-id)) "/")  ; TODO: make removing t1_ use index-of
      get-comments-json
      ; Discard the first comment; it's already been retrieved/processed
      (get-in [:data :children])
      first
      (get-in [:data :replies])
      (f url))
  )

(defn- load-more-comments
  [f col url]
  (merge-map
    ; TODO: make get-comments-json smarter so it only subtracts / if present
    (fn [child]
      (-> (str url child "/")
          get-comments-json
          (f url)))
    col))

(defn- restructure-comments
  "Takes a giant JSON blob from a post and reduces it to the info of interest,
   with each comment keyed by its unique ID. This function will perform
   additional GETs if it runs into places where the data specifies reddit has
   more comments it hasn't returned."
  [{{children :children} :data} url]
  (merge-map
    (fn [{:keys [data kind]}]
      (let [{:keys [id author body replies children parent_id]} data]
        (cond
          (not= kind "more") {(keyword id) {:author  author
                                            :body    body
                                            :replies (restructure-comments replies url)}}
          (= id "_") (continue-thread restructure-comments url parent_id)
          :else (load-more-comments restructure-comments children url))))
    children))

(defn- invalid-author?
  [author]
  (or (= author "[deleted]")
      (= author "[removed]")
      (nil? author)))

(defn- merge-comment-trees
  [a b]
  (merge-with
    (fn [x y]
      (cond (map? y) (merge-comment-trees x y)
            (invalid-author? y) x
            :else y))
    a b))

(defn get-and-merge-comments
  [url comments]
  (-> url
      get-comments-json
      (restructure-comments url)
      (merge-comment-trees comments)))

(defn- combine-totals
  [& totals]
  (apply merge-with + totals))

(defn- compute-reply-totals
  [{:keys [replies]}]
  (loop [totals (transient {:total 1})
         keys (keys replies)]
    (if (empty? keys)
      (persistent! totals)
      (let [key (first keys)
            reply-author (get-in replies [key :author])
            total (get totals reply-author)]
        (recur
          (if (invalid-author? reply-author)
            totals
            (assoc! totals reply-author (if total (inc total) 1)))
          (rest keys))))))

(defn compute-totals
  [comment-root]
  (loop [current-totals {}
         keys (keys comment-root)]
    (if (empty? keys)
      current-totals
      (let [comment (get comment-root (first keys))
            {:keys [author replies]} comment]
        (recur
          (apply merge-with combine-totals
                 (conj
                   (map #(compute-totals {(first %) (second %)}) replies)
                   (if-not (invalid-author? author)
                     {author (compute-reply-totals comment)}
                     {})
                   current-totals))
          (rest keys))))))


