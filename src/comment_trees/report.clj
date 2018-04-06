(ns comment-trees.report
  (:require [comment-trees.db :as db]
            [config.core :refer [env]]))

(def rows-in-reports (:rows-in-reports env))

(defn- sort-totals
  [totals]
  (reverse (sort-by
             (fn [total] (first (vals total)))
             (vec totals))))

(defn- get-sorted-totals
  [totals-fn user]
  (sort-totals (totals-fn user)))

(defn- calculate-stalk
  [to from]
  (let [to-map (apply merge to)
        from-map (apply merge from)]
    (filter #(> (first (vals %)) 0)
            (map #(hash-map % (- (get to-map %)
                                 (get from-map % 0)))
                 (keys to-map)))))

(defn- user-total->report-row
  [max-length user-total]
  (println (format (str "    %-" max-length "s %d")
                   (first (keys user-total))
                   (first (vals user-total)))))

(defn- max-length
  [& totals]
  (apply max
         (map #(count (first (keys %)))
              (apply concat totals))))


; TODO: overload with version that calculates max length
(defn- totals->report
  [header max-length totals]
  (let [header-length (-> totals first vals first str count (+ max-length 1))]
    (println (str "    " header))
    (println (str "    " (apply str (repeat header-length "-"))))
    (doseq [keyval totals] (user-total->report-row max-length keyval))
    (println)))

(defn user-report
  [user]
  (let [from (get-sorted-totals db/get-reply-from-totals user)
        to (get-sorted-totals db/get-reply-to-totals user)
        from-top (take rows-in-reports from)
        to-top (take rows-in-reports to)
        from-stalk-top (take rows-in-reports (sort-totals (calculate-stalk from to)))
        to-stalk-top (take rows-in-reports (sort-totals (calculate-stalk to from)))
        max-length (+ 1 (max-length to-top from-top from-stalk-top to-stalk-top))]
    (totals->report "Gets replies from:" max-length from-top)
    (totals->report "Replies to:" max-length to-top)
    (totals->report "Stalked by:" max-length from-stalk-top)
    (totals->report "Stalks:" max-length to-stalk-top)))

(defn- get-users-sorted-by-comment-total []
  (->> (db/sql-all-users)
       vec
       (sort-by :total)
       reverse))

(defn top-commenters-report
  [n]
  (let [totals (map #(hash-map (:name %) (:total %))
                    (take n (get-users-sorted-by-comment-total)))
        max-length (+ (max-length totals) 1)]
    (totals->report "Top Commenters:" max-length totals)))
