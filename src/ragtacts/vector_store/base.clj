(ns ragtacts.vector-store.base
  (:require [ragtacts.embedding.base :as embedding]))

(defmulti add
  "Add the documents in the database.
   
   Args:
   - db: A map with the following
     - `:type`: A keyword with the database type.
   - docs: A list of documents.
   
   Example:
   ```clojure
   (add (vector-store) [\"Hello!\" \"World!\"])
   ```
   "
  (fn [{:keys [db]} docs] (:type db)))

(defmulti search
  "Simularity search in the database.
   
   Args:
   - db-or-dbs: A db or a list of dbs that multiple dbs can be searched.
   - query: A string with the query.
   - params: A map with the following
     - `:raw?`: A boolean with the raw result.
     - `:metadata-out-fields`: A list of strings with the metadata fields to output.
     - `:weights`: A list of floats with the weights.
     - `:c`: An integer with the c value.
   
   Returns:
   - A list of texts
   
   Exapmle:
   ```clojure
   (search db \"Hello!\")

   (search db \"Hello!\" {:raw? true})

   (search db \"Hello!\" {:raw? true :metadata-out-fields [\"filename\"]})

   (search [db1 db2] \"Hello!\" {:weights [0.5 0.5] :c 60})
   ```"
  (fn [db-or-dbs query & [params]]
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

(defn embed
  "Return the embedding of a texts.
   
   Args:
   - embedding: A map with the following
     - `:type`: A keyword with the embedding type.
   - texts: A list of strings.
   
   Returns:
   - A list of float embeddings."
  [{:keys [embedding]} texts]
  (embedding/embed embedding texts))

(comment
  ;;
  )