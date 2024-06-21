(ns ragtacts.vector-store.milvus
  (:require [cheshire.core :as json]
            [clojure.walk :refer [keywordize-keys]]
            [milvus-clj.core :as milvus]
            [ragtacts.embedding.base :refer [embed text->doc]]
            [ragtacts.splitter.base :refer [split]]
            [ragtacts.vector-store.base :refer [add search delete]])
  (:import [java.util TreeMap]))

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
    (milvus/create-collection
     client
     {:collection-name collection-name
      :field-types (concat [{:primary-key? true
                             :auto-id? true
                             :data-type :int64
                             :name "pk"}
                            {:data-type :var-char
                             :max-length 65535
                             :name "id"}
                            {:data-type :var-char
                             :max-length 65535
                             :name "text"}]
                           (when (:vectors embeddings)
                             [{:data-type :float-vector
                               :name "vector"
                               :dimension (count (first (:vectors embeddings)))}])
                           (when (:sparse-vectors embeddings)
                             [{:data-type :sparse-float-vector
                               :name "sparse_vector"}])
                           metadata-fields)})))

(defn- create-index [client collection-name embeddings]
  (when (:vectors embeddings)
    (milvus/create-index client {:collection-name collection-name
                                 :field-name "vector"
                                 :index-type :hnsw
                                 :index-name "vector"
                                 :metric-type :l2
                                 :extra-param (json/generate-string {:M 8 :efConstruction 60})}))
  (when (:sparse-vectors embeddings)
    (milvus/create-index client {:collection-name collection-name
                                 :field-name "sparse_vector"
                                 :index-type :sparse-inverted-index
                                 :index-name "sparse_vector"
                                 :metric-type :ip}))
  (milvus/load-collection client {:collection-name collection-name}))

(defn- ->sorted-map [sparse-vector]
  (let [m (TreeMap.)]
    (doseq [{:keys [index value]} sparse-vector]
      (.put m (long index) (float value)))
    m))

(defn- insert-all [client collection-name docs embeddings]
  (let [metadata (:metadata (first docs))]
    (milvus/insert client
                   {:collection-name collection-name
                    :fields (concat [{:name "id" :values (map :id docs)}
                                     {:name "text" :values (map :text docs)}]
                                    (when (:vectors embeddings)
                                      [{:name "vector" :values (map #(map float %) (:vectors embeddings))}])
                                    (when (:sparse-vectors embeddings)
                                      [{:name "sparse_vector" :values (map ->sorted-map (:sparse-vectors embeddings))}])
                                    (map (fn [[key _]]
                                           {:name (name key)
                                            :values (map #(get (:metadata %) key) docs)})
                                         metadata))})))

(defn milvus
  "Return a Milvus vector store.
   
   Args:
   - A map with the following
     - `:host`: A string with the host. Default is `localhost`.
     - `:port`: An integer with the port. Default is `19530`.
     - `:db`: A string with the database name. Default is `default`.
     - `:collection`: A string with the collection name."
  [{:keys [host port db collection]}]
  {:type :milvus
   :collection collection
   :params {:host (or host "localhost")
            :port (or port 19530)
            :database (or db "default")}})

(defmethod add :milvus [{:keys [embedding splitter db]} texts-or-docs]
  (let [docs (map text->doc texts-or-docs)
        chunked-docs (split splitter docs)
        embeddings (embed embedding (map :text chunked-docs))
        collection (-> db :collection)]
    (with-open [client (milvus/client (:params db))]
      (try
        (create-collection client collection chunked-docs embeddings)
        (create-index client collection embeddings)
        (insert-all client collection chunked-docs embeddings)
        (catch Exception e
          (.printStackTrace e))))))

(defn- ->expr [metadata]
  (when metadata
    (reduce
     (fn [expr [k v]]
       (if expr
         (str expr " && " (str (name k) " == " (json/generate-string v)))
         (str (name k) " == " (json/generate-string v))))
     nil
     metadata)))

(defmethod search :milvus
  ([db query]
   (search db query {}))
  ([{:keys [embedding db]} query {:keys [top-k metadata raw? metadata-out-fields weights]}]
   (let [embeddings (embed embedding [query])
         collection (-> db :collection)
         hybrid? (and (seq (:vectors embeddings)) (seq (:sparse-vectors embeddings)))]
     (with-open [client (milvus/client (:params db))]
       (let [results (if hybrid?
                       (milvus/hybrid-search client {:collection-name collection
                                                     :out-fields (vec (set (concat metadata-out-fields
                                                                                   ["text"
                                                                                    "vector"
                                                                                    "sparse_vector"])))
                                                     :top-k (int (or top-k 5))
                                                     :ranker {:type :weighted
                                                              :weights weights}
                                                     :search-requests
                                                     [{:vector-field-name "vector"
                                                       :metric-type :l2
                                                       :float-vectors (map #(map float %) (:vectors embeddings))
                                                       :expr (->expr metadata)
                                                       :top-k (int (or top-k 5))}
                                                      {:vector-field-name "sparse_vector"
                                                       :metric-type :ip
                                                       :sparse-float-vectors (map ->sorted-map (:sparse-vectors embeddings))
                                                       :expr (->expr metadata)
                                                       :top-k (int (or top-k 5))}]})
                       (milvus/search client {:collection-name collection
                                              :metric-type :l2
                                              :vectors (map #(map float %) embeddings)
                                              :expr (->expr metadata)
                                              :vector-field-name "vector"
                                              :out-fields (vec (set (concat metadata-out-fields
                                                                            ["text" "vector"])))
                                              :top-k (int (or top-k 5))}))
             docs (->> results
                       first
                       (map (fn [{:keys [entity]}]
                              (let [result {:text (get entity "text")
                                            :vector  (get entity "vector")
                                            :metadata (->
                                                       (into {} entity)
                                                       keywordize-keys
                                                       (dissoc :vector :text))}]
                                (if hybrid?
                                  (assoc result :sparse-vector (get entity "sparse_vector"))
                                  result)))))]
         (if raw?
           docs
           (map :text docs)))))))

(defmethod delete :milvus [{:keys [db]} metadata]
  (let [collection (-> db :collection)]
    (with-open [client (milvus/client (:params db))]
      (milvus/delete client {:collection-name collection
                             :expr (->expr metadata)}))))

(comment


  ;;
  )