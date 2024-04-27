(ns ragtacts.vector-store.milvus
  (:require [cheshire.core :as json]
            [milvus-clj.core :as milvus]
            [ragtacts.splitter.base :refer [make-chunk]]
            [ragtacts.vector-store.base :refer [VectorStore]]))

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

(defn- create-collection [client collection-name embeddings]
  (let [metadata (:metadata (first embeddings))
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
                                                             :dimension (count (:vectors (first embeddings)))}
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

(defn- delete-all-by-doc-id [client collection-name doc-id]
  (milvus/delete client {:collection-name collection-name
                         :expr (str "id == \"" doc-id "\"")}))

(defn- insert-all [client collection-name embeddings]
  (let [metadata (:metadata (first embeddings))]
    (milvus/insert client {:collection-name collection-name
                           :fields (concat [{:name "id" :values (map :doc-id embeddings)}
                                            {:name "text" :values (map :text embeddings)}
                                            {:name "vector" :values (map :vectors embeddings)}]
                                           (map (fn [[key value]]
                                                  {:name (name key)
                                                   :values (repeat (count embeddings) value)})
                                                metadata))})))


(defrecord MilvusVectorStore [collection host port db]
  VectorStore
  (insert [_ embeddings]
    (with-open [client (milvus/client {:host (or host "localhost")
                                       :port (or port 19530)
                                       :database db})]
      (try
        (create-collection client collection embeddings)
        (create-index client collection)
        (insert-all client collection embeddings)
        (catch Exception e
          (.printStackTrace e)))))

  (delete-by-id [_ id]
    (with-open [client (milvus/client {:host (or host "localhost")
                                       :port (or port 19530)
                                       :database db})]
      (delete-all-by-doc-id client collection id)))

  (search [_ embeddings expr]
    (with-open [client (milvus/client {:host (or host "localhost")
                                       :port (or port 19530)
                                       :database db})]
      (let [results (milvus/search client {:collection-name collection
                                           :metric-type :l2
                                           :vectors (map #(map float (:vectors %)) embeddings)
                                           :expr expr
                                           :vector-field-name "vector"
                                           :out-fields ["text" "vector"]
                                           :top-k 3})]
        (->> results
             first
             (map :entity)
             (map #(make-chunk (get % "text"))))))))

(defn make-milvus-vector-store [opts]
  (map->MilvusVectorStore opts))