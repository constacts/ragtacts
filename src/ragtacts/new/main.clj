(ns ragtacts.new.main
  (:require [ragtacts.new.core :refer [ask vector-store save search prompt embed]]
            [ragtacts.new.vector-store.milvus :refer [milvus]]
            [ragtacts.new.embedding.open-ai :refer [open-ai-embedding]]
            [clojure.string :as str]))

(comment
  (ask "ragtacts는 무엇인가요?")

  (let [question "ragtacts는 무엇인가요?"
        db (vector-store {:db (milvus {:collection "test6"})})]
    (save db ["ragtacts로 llm 응용 프로그램을 쉽게 만들 수 있습니다."])
    (ask (prompt "You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question. If you don't know the answer, just say that you don't know. Use three sentences maximum and keep the answer concise.
Question: {{ question }}
Context: {{ context }} 
Answer:"
                 {:question question
                  :context (str/join "\n" (search db question))})))
  ;;
  )