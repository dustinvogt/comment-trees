(ns comment-trees.util)

;(defn- count-replies
;  [root]
;  (if (nil? root)
;    0
;    (apply + (map
;               (fn [node] (+ (count-replies (:replies (second node))) 1))
;               root))))
;
;(defn- count-comments-in-scan-data
;  [id]
;  (count-replies (get-in @scan-data [id :comments])))
;
;(defn list-scanning-posts []
;  (doseq [keyval @thread-workers]
;    (let [[key val] keyval]
;      (println key (get-in @scan-data [key :scans]) (:desc val)))))
