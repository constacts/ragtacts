(ns ragtacts.util-test
  (:require [clojure.test :refer :all]
            [ragtacts.util :refer :all]))

(deftest test-f-string
  (is (= "Question: Hello!"
         (f-string "Question: { question }" {:question "Hello!"})))
  (is (= "Question: Hello!"
         (f-string "Question: {  question  }" {:question "Hello!"})))
  (is (= "Question: Hello!"
         (f-string "Question: {question}" {:question "Hello!"}))))