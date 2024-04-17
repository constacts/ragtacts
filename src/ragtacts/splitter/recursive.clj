(ns ragtacts.splitter.recursive
  (:refer-clojure :exclude [chunk])
  (:require [ragtacts.splitter.base :refer [Splitter]]
            [ragtacts.types :refer [make-chunk]])
  (:import [dev.langchain4j.data.document DocumentSplitter]
           [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.splitter DocumentSplitters]
           [dev.langchain4j.data.segment TextSegment]))

(defrecord RecursiveSplitter [opts]
  Splitter
  (split [_ docs]
    (flatten
     (mapv (fn [{:keys [id text metadata]}]
             (let [{:keys [size overlap]} opts
                   ^DocumentSplitter splitter (DocumentSplitters/recursive size overlap)
                   text-segments (.split splitter (Document. text))]
               (mapv (fn [^TextSegment segment]
                       (make-chunk id (.text segment) metadata))
                     text-segments)))
           docs))))

(defn make-recursive [opts]
  (->RecursiveSplitter opts))

(comment

  ;;
  )