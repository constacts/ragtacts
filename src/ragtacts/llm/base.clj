(ns ragtacts.llm.base)

(defrecord Answer [text tool-calls])

(defn make-answer [params]
  (map->Answer params))

(defprotocol Llm
  (query [this args]))