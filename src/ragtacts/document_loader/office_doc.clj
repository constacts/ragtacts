(ns ragtacts.document-loader.office-doc
  (:require [clojure.java.io :as io]
            [ragtacts.document-loader.base :refer [DocumentLoader make-doc]])
  (:import [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]
           [dev.langchain4j.data.document Document DocumentParser]))

(defrecord OfficeDocLoader []
  DocumentLoader
  (load-doc [_ doc-id file]
    (let [^DocumentParser parser (ApacheTikaDocumentParser.)
          ^Document doc (.parse parser (io/input-stream file))]
      (make-doc doc-id (.text doc)))))

(defn make-office-doc-loader [opts]
  (map->OfficeDocLoader opts))