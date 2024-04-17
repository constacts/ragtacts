(ns ragtacts.prompt-template.default
  (:require [ragtacts.prompt-template.base :refer [PromptTemplate]]))

#_(def ^:private chat-system-prompt
    "Given a chat history and the latest user question 
which might reference context in the chat history, formulate a standalone question 
which can be understood without the chat history. Do NOT answer the question, 
just reformulate it if needed and otherwise return it as is.")

(def ^:private chat-system-prompt
  "You are an assistant for question-answering tasks. 
Use the following pieces of retrieved context to answer the question. 
If you don't know the answer, just say that you don't know. 
Use three sentences maximum and keep the answer concise.")

(def ^:private context-prompt
  "Use the following pieces of context to answer the user's question. 
If you don't know the answer, just say that you don't know, don't try to make up an answer.
----------------
%s")

(defrecord DefaultPromptTemplate []
  PromptTemplate
  (prompt [_ opts]
    (let [context (:context opts)]
      (format (str chat-system-prompt "\n\n" context-prompt) context))))

(defn make-default-prompt-template [_]
  (->DefaultPromptTemplate))