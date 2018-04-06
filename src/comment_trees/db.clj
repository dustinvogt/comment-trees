(ns comment-trees.db
  (require [yesql.core :refer [defqueries]]
           [clojure.java.jdbc :as jdbc]))

(def db-settings
  {:classname   "org.h2.Driver"
   ;:subprotocol "h2:mem"
   ;:subname     "comment_tree;DB_CLOSE_DELAY=-1"
   :subprotocol "h2:file"
   :subname     (str (System/getProperty "user.home") "/" "comment_tree")
   :user        "sa"
   :password    ""})

(defqueries "sql/create_tables.sql" {:connection db-settings})
(defqueries "sql/user.sql" {:connection db-settings})
(defqueries "sql/reply.sql" {:connection db-settings})
(defqueries "sql/post.sql" {:connection db-settings})

(defn do-transaction
  [f]
  (jdbc/with-db-transaction [tx db-settings]
    (f tx)))

(defn create-tables! []
  (sql-create-user-table!)
  (sql-create-reply-table!)
  (sql-add-replies-constraint!)
  (sql-create-post-table!))

(defn- get-user
  "Looks up a user, creating them if not in the table, and returns their ID.
   Requires a transaction."
  [name tx]
    (let [lookup (sql-user-by-name {:name name} {:connection tx})]
      (if (empty? lookup)
        (first (vals (sql-add-user<! {:name name} {:connection tx})))
        (:id (first lookup)))))

(defn- check-reply-relationship
  [user-to user-from tx]
  (when (empty? (sql-has-reply-relationship {:user_to   user-to
                                             :user_from user-from}
                                            {:connection tx}))
    (sql-create-reply-relationship! {:user_to   user-to
                                     :user_from user-from}
                                    {:connection tx})))

(defn- get-totals
  [user sql-fn subject-key predicate-key]
  (map #(hash-map (-> (sql-user-by-id {:id (get % predicate-key)})
                      first
                      :name)
                  (:total %))
       (sql-fn {subject-key user})))

(defn get-reply-from-totals
  [user]
  (get-totals user sql-reply-from-totals :user_to :user_from))

(defn get-reply-to-totals
  [user]
  (get-totals user sql-reply-to-totals :user_from :user_to))

(defn is-post-registered
  [id tx]
  (-> (sql-get-post {:reddit_id id}
                    {:connection tx})
      empty?
      not))

(defn register-post
  [id tx]
  (sql-add-post<! {:reddit_id id}
                  {:connection tx}))

(defn deregister-post
  [id tx]
  (sql-remove-post! {:reddit_id id}
                    {:connection tx}))

(defn update-user-total
  [user total]
  (jdbc/with-db-transaction
    [tx db-settings]
    (let [id (get-user user tx)]
      (sql-update-user-total! {:user_to id :total total}
                              {:connection tx}))))

(defn update-reply-totals
  [to-user from-user total]
  (jdbc/with-db-transaction
    [tx db-settings]
    (let [to-id (get-user to-user tx)
          from-id (get-user from-user tx)]
      (check-reply-relationship to-id from-id tx)
      (sql-update-reply-totals! {:user_to   to-id
                                 :user_from from-id
                                 :total     total}
                                {:connection tx}))))

(defn- update-total
  [to-user [from-user total]]
  (when-not (= from-user :total)
    (update-reply-totals to-user from-user total)))

(defn- store-totals
  [[to-user replies]]
  (update-user-total to-user (:total replies))
  (run! #(update-total to-user %) replies))

(defn store-all-totals
  [all-totals]
  (run! store-totals all-totals))
