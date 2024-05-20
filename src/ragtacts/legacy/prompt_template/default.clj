(ns ragtacts.legacy.prompt-template.default
  (:require [ragtacts.legacy.prompt-template.base :refer [PromptTemplate]]))

(def ^:private context-prompt
  "You are an assistant for question-answering tasks. Use the following pieces of context to answer the user's question. 
If you don't know the answer, just say that you don't know, don't try to make up an answer.
----------------
%s")

(defrecord DefaultPromptTemplate []
  PromptTemplate
  (prompt [_ opts]
    (let [context (:context opts)]
      (format context-prompt context))))

(defn make-default-prompt-template [_]
  (->DefaultPromptTemplate))