(ns ragtacts.vector-store.milvus
  (:require [cheshire.core :as json]
            [milvus-clj.core :as milvus]
            [ragtacts.types :refer [make-chunk]]
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

(defn- create-collection [client collection-name vectors]
  (let [metadata (:metadata (first vectors))
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
                                                             :dimension (count (:vectors (first vectors)))}
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

(defn- insert-all [client collection-name vectors]
  (let [metadata (:metadata (first vectors))]
    (milvus/insert client {:collection-name collection-name
                           :fields (concat [{:name "id" :values (map :doc-id vectors)}
                                            {:name "text" :values (map :text vectors)}
                                            {:name "vector" :values (map :vectors vectors)}]
                                           (map (fn [[key value]]
                                                  {:name (name key)
                                                   :values (repeat (count vectors) value)})
                                                metadata))})))


(defrecord MilvusVectorStore [collection host port db]
  VectorStore
  (save [_ vectors]
    (with-open [client (milvus/client {:host (or host "localhost")
                                       :port (or port 19530)
                                       :database db})]
      (try
        (create-collection client collection vectors)
        (create-index client collection)
        (doseq [doc-id (distinct (map :doc-id vectors))]
          (delete-all-by-doc-id client collection doc-id))
        (insert-all client collection vectors)
        (catch Exception e
          (.printStackTrace e)))))

  (search [_ vectors expr]
    (with-open [client (milvus/client {:host (or host "localhost")
                                       :port (or port 19530)
                                       :database db})]
      (let [results (milvus/search client {:collection-name collection
                                           :metric-type :l2
                                           :vectors (map #(map float (:vectors %)) vectors)
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