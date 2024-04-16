(ns ragtacts.embedder.base)

(defprotocol Embedder
  (embed [this chunks]))
