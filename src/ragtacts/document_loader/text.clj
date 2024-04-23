(ns ragtacts.document-loader.text
  (:require [ragtacts.document-loader.base :refer [DocumentLoader make-doc]]))

(defrecord TextLoader []
  DocumentLoader
  (load-doc [_ doc-id source]
    (make-doc doc-id source)))

(defn make-text-loader [opts]
  (map->TextLoader opts))