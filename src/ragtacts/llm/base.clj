(ns ragtacts.llm.base)

(defrecord Answer [text])

(defn make-answer [text]
  (->Answer text))

(defprotocol Llm
  (query [this prompt]))