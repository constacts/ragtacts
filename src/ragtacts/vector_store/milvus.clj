(ns ragtacts.vector-store.milvus
  (:require  [cheshire.core :as json]
             [milvus-clj.core :as milvus]
             [ragtacts.embedding.base :refer [embed text->doc]]
             [ragtacts.vector-store.base :refer [save search]]
             [ragtacts.splitter.base :refer [split]]))

(defn- make-field [[key value]]
  (merge {:name (name key)}
         (cond
           (double? value) {:data-type :double}
           (boolean? value) {:data-type :bool}
           (instance? Integer value) {:data-type :int32}
           (instance? Long value) {:data-type :int64}
           (string? value) {:data-type :var-char
                            :max-length 65535}
           :else {:data-type :var-char
                  :max-length 65535})))

(defn- create-collection [client collection-name docs embeddings]
  (let [metadata (:metadata (first docs))
        metadata-fields (map make-field metadata)]
    (milvus/create-collection client {:collection-name collection-name
                                      :field-types (concat [{:primary-key? true
                                                             :auto-id? true
                                                             :data-type :int64
                                                             :name "pk"}
                                                            {:data-type :var-char
                                                             :max-length 65535
                                                             :name "id"}
                                                            {:data-type :float-vector
                                                             :name "vector"
                                                             :dimension (count (first embeddings))}
                                                            {:data-type :var-char
                                                             :max-length 65535
                                                             :name "text"}]
                                                           metadata-fields)})))

(defn- create-index [client collection-name]
  (milvus/create-index client {:collection-name collection-name
                               :field-name "vector"
                               :index-type :hnsw
                               :index-name "vector"
                               :metric-type :l2
                               :extra-param (json/generate-string {:M 8 :efConstruction 60})})
  (milvus/load-collection client {:collection-name collection-name}))

(defn- insert-all [client collection-name docs embeddings]
  (let [metadata (:metadata (first docs))]
    (milvus/insert client {:collection-name collection-name
                           :fields (concat [{:name "id" :values (map :id docs)}
                                            {:name "text" :values (map :text docs)}
                                            {:name "vector" :values embeddings}]
                                           (map (fn [[key value]]
                                                  {:name (name key)
                                                   :values (repeat (count docs) value)})
                                                metadata))})))

(defn milvus [{:keys [host port db collection]}]
  {:type :milvus
   :collection collection
   :params {:host (or host "localhost")
            :port (or port 19530)
            :database (or db "default")}})

(defmethod save :milvus [{:keys [embedding splitter db]} texts-or-docs]
  (let [docs (map text->doc texts-or-docs)
        chunked-docs (split splitter docs)
        embeddings (embed embedding (map :text chunked-docs))
        collection (-> db :collection)]
    (with-open [client (milvus/client (:params db))]
      (try
        (create-collection client collection chunked-docs embeddings)
        (create-index client collection)
        (insert-all client collection chunked-docs (map #(map float %) embeddings))
        (catch Exception e
          (.printStackTrace e))))))

(defmethod search :milvus
  ([db query]
   (search db query {}))
  ([{:keys [embedding db]} query {:keys [top-k expr]}]
   (let [embeddings (embed embedding [query])
         collection (-> db :collection)]
     (with-open [client (milvus/client  (:params db))]
       (let [results (milvus/search client {:collection-name collection
                                            :metric-type :l2
                                            :vectors (map #(map float %) embeddings)
                                            :expr expr
                                            :vector-field-name "vector"
                                            :out-fields ["text" "vector"]
                                            :top-k (int (or top-k 5))})]
         (->> results
              first
              (map :entity)
              (map #(get % "text"))))))))

