(ns ragtacts.vector-store.in-memory
  (:require [ragtacts.splitter.base :refer [make-chunk]]
            [ragtacts.embedder.base :refer [make-embedding]]
            [ragtacts.vector-store.base :refer [insert search delete-by-id
                                                VectorStore]]
            [clojure.walk :refer [stringify-keys keywordize-keys]])
  (:import [dev.langchain4j.data.document Metadata]
           [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.data.segment TextSegment]
           [dev.langchain4j.store.embedding EmbeddingSearchRequest]
           [dev.langchain4j.store.embedding.inmemory InMemoryEmbeddingStore]
           [java.util HashMap]))

(def ^:private db-file "db.json")

(defn- embedding->text-segment [{:keys [doc-id
                                        text
                                        metadata]}]
  (let [metadata (if doc-id
                   (assoc metadata :id doc-id)
                   metadata)]
    (TextSegment. text (if metadata
                         (Metadata/from (HashMap. (stringify-keys metadata)))
                         (Metadata.)))))

(defn- text-segment->chunk [text-segment]
  (let [metadata (.metadata text-segment)]
    (make-chunk (.get metadata "id") (.text text-segment) (-> (into {} (.asMap metadata))
                                                              keywordize-keys
                                                              (dissoc :id)))))

(defn- get-private [obj field-name]
  (let [field (.getDeclaredField (class obj) field-name)]
    (.setAccessible field true)
    (.get field obj)))

(defrecord InMemoryVectorStore [store]
  VectorStore
  (insert [_ embeddings]
    (.addAll store
             (map (fn [{:keys [vectors]}]
                    (Embedding. (float-array (map float vectors))))
                  embeddings)
             (map embedding->text-segment embeddings))
    (.serializeToFile store db-file))

  (delete-by-id [_ id]
    (let [entries (get-private store "entries")]
      (doseq [entry entries]
        (let [embedded (get-private entry "embedded")]
          (when (= (.get (.metadata embedded) "id") id)
            (.remove entries entry))))
      (.serializeToFile store db-file)))

  (search [_ embeddings {:keys [top-k expr]}]
    (let [embedding (Embedding. (float-array (map float (:vectors (first embeddings)))))
          result (.search store
                          (EmbeddingSearchRequest.
                           embedding
                           (int (or top-k 5))
                           0.0
                           nil))]
      (map #(text-segment->chunk (.embedded %)) (.matches result)))))

(defn make-in-memory-vector-store [_]
  (let [store (try (InMemoryEmbeddingStore/fromFile db-file)
                   (catch Exception _))]
    (->InMemoryVectorStore (or store (InMemoryEmbeddingStore.)))))

(comment

  (let [s (make-in-memory-vector-store nil)]
    #_(insert s
              [(make-embedding "1" "hello" [1 2 3] {:a 1})
               (make-embedding "2" "world" [4 5 6] {:a 1})
               (make-embedding "3" "foo" [7 8 9] {:a 1})
               (make-embedding "4" "bar" [10 11 12] {:a 1})])
    #_(search s [(make-embedding [100 200 300])] {})
    (delete-by-id s "4"))
  ;; 
  )