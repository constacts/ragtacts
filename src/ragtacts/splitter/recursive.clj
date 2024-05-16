(ns ragtacts.splitter.recursive
  (:require [ragtacts.splitter.base :refer [split]])
  (:import [dev.langchain4j.data.document DocumentSplitter]
           [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.splitter DocumentSplitters]
           [dev.langchain4j.data.segment TextSegment]))

(defn recursive-splitter [opts]
  {:type :recursive
   :opts opts})

(defmethod split :recursive [splitter docs]
  (flatten
   (mapv (fn [{:keys [id text metadata]}]
           (let [{:keys [size overlap]} (:opts splitter)
                 ^DocumentSplitter splitter (DocumentSplitters/recursive size overlap)
                 text-segments (.split splitter (Document. text))]
             (mapv (fn [^TextSegment segment]
                     {:id id
                      :text (.text segment)
                      :metadata metadata})
                   text-segments)))
         docs)))