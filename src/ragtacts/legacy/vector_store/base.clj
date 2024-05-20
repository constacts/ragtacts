(ns ragtacts.legacy.vector-store.base)

(defprotocol VectorStore
  (insert [this embeddings])
  (delete-by-id [this id])
  (search [this embeddings opts]))