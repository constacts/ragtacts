(ns ragtacts.vector-store.base)

(defprotocol VectorStore
  (save [this vectors])
  (search [this vector expr]))