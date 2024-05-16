(ns ragtacts.new.main
  (:require [ragtacts.new.core :refer [ask vector-store save search prompt embed]]
            [ragtacts.new.vector-store.milvus :refer [milvus]]
            [ragtacts.new.loader.web :refer [web-text]]
            [ragtacts.new.loader.doc :refer [doc-text]]
            [ragtacts.new.embedding.open-ai :refer [open-ai-embedding]]
            [clojure.string :as str]))


(defn ^{:desc "ragtacts 해시 값을 구하는 함수입니다."}
  ragtacts-hash
  [^{:type "string"
     :desc "해시 값을 구할 단어입니다."} word]
  (reverse word))

(defn ^{:desc "ragtact 카운터 함수입니다."}
  word-count
  [^{:type "string"
     :desc "단어입니다."} word]
  (count word))

(comment

  (apply #'ragtacts-hash ["대한민국"])

  (-> (ask "수박과 바나나 중 더 맞있는 것은?")
      (conj "그럼 더 긴 단어는?")
      ask
      last)


  (ask "\"대한민국\"이라는 단어의 ragtacts 해시 값을 구해주세요." {:tools [#'ragtacts-hash #'word-count]})

  ;;
  )