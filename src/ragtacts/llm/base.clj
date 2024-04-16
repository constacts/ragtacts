(ns ragtacts.llm.base)

(defprotocol Llm
  (query [this prompt]))