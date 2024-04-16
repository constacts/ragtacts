(ns ragtacts.prompt-template.default
  (:require [ragtacts.prompt-template.base :refer [PromptTemplate]]))

(defrecord DefaultPromptTemplate []
  PromptTemplate
  (prompt [_ prompt opts]
    (let [context (:context opts)]
      (format "System: Use the following pieces of context to answer the user's question. 
      If you don't know the answer, just say that you don't know, don't try to make up an answer.
      ----------------
      %s
      Human: %s" context prompt))))

(defn make-default-prompt-template []
  (->DefaultPromptTemplate))