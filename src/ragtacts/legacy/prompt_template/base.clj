(ns ragtacts.legacy.prompt-template.base)

(defprotocol PromptTemplate
  (prompt [this opts]))