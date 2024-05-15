(ns ragtacts.new.main
  (:require [ragtacts.new.core :refer [ask vector-store save search prompt embed]]
            [ragtacts.new.vector-store.milvus :refer [milvus]]
            [ragtacts.new.loader.web :refer [web-text]]
            [ragtacts.new.embedding.open-ai :refer [open-ai-embedding]]
            [clojure.string :as str]))

(comment

  (let [question "What is RAG?"
        db (vector-store {:db (milvus {:collection "test6"})})]
    (save db [(web-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")])
    (ask (prompt "You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question. If you don't know the answer, just say that you don't know. Use three sentences maximum and keep the answer concise.
Question: {{ question }}
Context: {{ context }} 
Answer:"
                 {:question question
                  :context (str/join "\n" (search db question))})))
  ;;
  )