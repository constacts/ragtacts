(ns ragtacts.vector-store.in-memory
  (:require [ragtacts.types :refer [make-chunk make-vectors]]
            [ragtacts.vector-store.base :refer [save search VectorStore]])
  (:import [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.store.embedding EmbeddingSearchRequest]
           [dev.langchain4j.store.embedding.inmemory InMemoryEmbeddingStore]))

(defrecord InMemoryVectorStore [store]
  VectorStore
  (save [_ vectors]
    (.addAll store
             (map (fn [{:keys [vectors]}]
                    (Embedding. (float-array (map float vectors))))
                  vectors)
             (map :text vectors)))

  (search [_ vectors expr]
    (let [result (.search store
                          (EmbeddingSearchRequest.
                           (Embedding. (float-array (map float (:vectors (first vectors)))))
                           (int 5)
                           0.0
                           nil))]
      (map #(make-chunk (.embedded %)) (.matches result)))))

(defn make-in-memory-vector-store [_]
  (->InMemoryVectorStore (InMemoryEmbeddingStore.)))

(comment

  (let [s (make-in-memory-vector-store nil)]
    (save s
          [(make-vectors nil "hello" [1 2 3] nil)
           (make-vectors nil "world" [4 5 6] nil)
           (make-vectors nil "foo" [7 8 9] nil)
           (make-vectors nil "bar" [10 11 12] nil)])
    (search s [1 2 3] {}))
  ;; 
  )