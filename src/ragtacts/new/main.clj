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
     :desc "해시 값을 구할 단어입니다."}
   word]
  (reverse word))

(defn ^{:desc "ragtact 카운터 함수입니다."}
  word-count
  [^{:type "string"
     :desc "단어입니다."}
   word]
  (count word))

(comment

  (apply #'ragtacts-hash ["대한민국"])

  (ask "스페인의 수도는?")

  ;;
  )