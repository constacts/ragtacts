(ns ragtacts.vector-store.in-memory
  (:require [ragtacts.logging :as log]
            [ragtacts.splitter.base :refer [make-chunk]]
            [ragtacts.vector-store.base :refer [insert search VectorStore make-vectors]])
  (:import [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.store.embedding EmbeddingSearchRequest]
           [dev.langchain4j.store.embedding.inmemory InMemoryEmbeddingStore]))

(defrecord InMemoryVectorStore [store]
  VectorStore
  (insert [_ vectors]
    (.addAll store
             (map (fn [{:keys [vectors]}]
                    (Embedding. (float-array (map float vectors))))
                  vectors)
             (map :text vectors)))

  (delete-by-id [_ id]
    ;; InMemoryEmbeddingStore does not support deletion and metadata
    (throw (ex-info "Not Supported" {:id id})))

  (search [_ vectors {:keys [top-k expr]}]
    (let [embedding (Embedding. (float-array (map float (:vectors (first vectors)))))
          result (.search store
                          (EmbeddingSearchRequest.
                           embedding
                           (int (or top-k 5))
                           0.0
                           nil))]
      (map #(make-chunk (.embedded %)) (.matches result)))))

(defn make-in-memory-vector-store [_]
  (->InMemoryVectorStore (InMemoryEmbeddingStore.)))

(comment
  (let [s (make-in-memory-vector-store nil)]
    (insert s
            [(make-vectors nil "hello" [1 2 3] nil)
             (make-vectors nil "world" [4 5 6] nil)
             (make-vectors nil "foo" [7 8 9] nil)
             (make-vectors nil "bar" [10 11 12] nil)])
    (search s [(make-vectors [100 200 300])] {}))
  ;; 
  )