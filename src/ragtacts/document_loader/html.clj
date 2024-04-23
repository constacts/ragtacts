(ns ragtacts.document-loader.html
  (:require [ragtacts.document-loader.base :refer [DocumentLoader make-doc]])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defrecord HtmlLoader []
  DocumentLoader
  (load-doc [_ doc-id source]
    (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from source))]
      (make-doc doc-id (.text doc)))))

(defn make-html-loader [opts]
  (map->HtmlLoader opts))