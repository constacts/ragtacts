(ns ragtacts.vector-store.base
  (:require [ragtacts.embedding.base :as embedding]))

(defmulti save
  "  
     Args:
     - db: 
     - docs:
     - params:
   
     Returns:
   
     Example:"
  (fn [{:keys [db]} docs] (:type db)))

(defmulti search (fn [db-or-dbs query & [params]]
                   (if (vector? db-or-dbs)
                     :multi
                     (-> db-or-dbs :db :type))))

(defmethod search :multi
  ([dbs query]
   (search dbs query {}))
  ([dbs query {:keys [raw? weights c] :as params}]
   (let [weights (or weights (repeat (count dbs) 0.5))
         c (or c 60)
         docs (->> dbs
                   (map-indexed (fn [idx db]
                                  (let [result (search db query (assoc params :raw? true))]
                                    (map-indexed #(assoc %2 :idx idx :rank (inc %1)) result))))
                   flatten)
         rrfs (reduce (fn [result {:keys [text idx rank]}]
                        (update result text #(let [rrf (/ (nth weights idx) (+ rank c))]
                                               (if %
                                                 (+ % rrf)
                                                 rrf))))
                      {}
                      docs)

         docs-with-rrf (map #(assoc % :rrf (get rrfs (:text %))) docs)
         sorted-docs (->> (sort-by :rrf docs-with-rrf)
                          reverse
                          (map #(dissoc % :rrf :idx :rank)))]
     (if raw?
       sorted-docs
       (map :text sorted-docs)))))

(defn embed [{:keys [embedding]} texts]
  (embedding/embed embedding texts))

(comment
  ;;
  )