(ns ragtacts.llm.open-ai-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [ragtacts.llm.open-ai :refer :all]))

(deftest test-with-images
  (is (= [{:user [{:type "text" :text "Hello"}
                  {:type "image_url" :image_url {:url "http://localhost:3000/image.png"}}]}]
         (with-images "Hello" "http://localhost:3000/image.png")))
  (let [result (with-images "Hello" (io/input-stream "./README.md"))]
    (is (str/starts-with? (-> result first :user second :image_url :url)
                          "data:text/plain;base64,"))))