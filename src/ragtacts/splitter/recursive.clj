(ns ragtacts.splitter.recursive
  (:require [ragtacts.splitter.base :refer [split]])
  (:import [dev.langchain4j.data.document DocumentSplitter]
           [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.splitter DocumentSplitters]
           [dev.langchain4j.data.segment TextSegment]
           [dev.langchain4j.model.openai OpenAiTokenizer]))

(defn recursive-splitter
  "Return a recursive splitter.
   
   Args:
   - opts: A map with the following keys:
     - `:size`: An integer with the size of the chunks.
     - `:overlap`: An integer with the overlap of the chunks."
  [opts]
  {:type :recursive
   :opts opts})

(defmethod split :recursive [splitter docs]
  (flatten
   (mapv (fn [{:keys [id text metadata]}]
           (let [{:keys [size overlap]} (:opts splitter)
                 ^DocumentSplitter splitter (DocumentSplitters/recursive size overlap (OpenAiTokenizer.))
                 text-segments (.split splitter (Document. text))]
             (mapv (fn [[i ^TextSegment segment]]
                     {:id id
                      :text (.text segment)
                      :metadata (assoc metadata :seq i)})
                   (map-indexed vector text-segments))))
         docs)))