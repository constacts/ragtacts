(ns ragtacts.util-test
  (:require [clojure.test :refer :all]
            [ragtacts.util :refer :all]))

(deftest test-f-string
  (is (= "x"
         (f-string "{a}" {:a "x"})))
  (is (= " x "
         (f-string " { a } " {:a "x"})))
  (is (= "{x}"
         (f-string "{a}" {:a "{x}"})))
  (is (= "{x} y"
         (f-string "{a} {b}" {:a "{x}" :b "y"})))
  (is (= "x {y}"
         (f-string "{a} {b}" {:a "x" :b "{y}"})))
  (is (= "{x}"
         (f-string "{{a}}" {:a "x"})))
  (is (= "{{{x} {y}}}"
         (f-string "{{{{a}} {{b}}}}" {:a "x" :b "y"}))))