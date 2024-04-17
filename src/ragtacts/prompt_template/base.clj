(ns ragtacts.prompt-template.base)

(defprotocol PromptTemplate
  (prompt [this opts]))