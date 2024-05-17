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
  ([dbs query params]
   (let [docs (->> dbs
                   (map #(search % query (assoc params :raw? true)))
                   flatten)
         rrfs (reduce (fn [result {:keys [text score]}]
                        (update result text #(let [rrf (/ 0.5 (+ score 60))]
                                               (if %
                                                 (+ % rrf)
                                                 rrf))))
                      {}
                      docs)
         docs-with-rrf (map #(assoc % :rrf (get rrfs (:text %))) docs)
         sorted-docs (->> (sort-by :rrf docs-with-rrf)
                          (map #(dissoc % :rrf)))]
     (if (:raw? params)
       sorted-docs
       (map :text sorted-docs)))))

(defn embed [{:keys [embedding]} texts]
  (embedding/embed embedding texts))

(comment
  (search [] "test" {})

  (reverse (sort-by second {:a 3 :b 1 :c 2}))
  ;;
  )