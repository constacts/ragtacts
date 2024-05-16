(ns ragtacts.legacy.tool.get-word-length
  (:require [ragtacts.legacy.tool.base :refer [Tool]]))

(defrecord GetWordLengthTool []
  Tool
  (run [_ {:keys [word]}]
    {:length (count word)})
  (metadata [_]
    {:name "get_word_length"
     :description "Returns the length of a word."
     :parameters {:type "object"
                  :properties
                  {:word {:type "string"
                          :description "The word to get the length of."}}
                  :required ["word"]}}))

(defn make-get-word-length-tool []
  (->GetWordLengthTool))