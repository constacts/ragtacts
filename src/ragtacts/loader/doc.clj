(ns ragtacts.loader.doc
  (:require [clj-ulid :refer [ulid]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [dev.langchain4j.data.document Document DocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]))

(defn get-text
  "Return the text of a document. PDF, DOCX, and other formats are supported.
   
   Returns:
    - A map with the following keys:
      - `:id`: A string with the document id.
      - `:text`: A string with the document text.
      - `:metadata`: A map with the document metadata.
   "
  [path-or-input-stream]
  (let [input-stream (if (string? path-or-input-stream)
                       (-> path-or-input-stream
                           (str/replace #"~" (System/getProperty "user.home"))
                           io/input-stream)
                       path-or-input-stream)
        ^DocumentParser parser (ApacheTikaDocumentParser.)
        ^Document doc (.parse parser input-stream)]
    {:id (if (string? path-or-input-stream)
           path-or-input-stream
           (ulid))
     :text (.text doc)
     :metadata {}}))

(defn watch [{:keys [path last-change]} callback]
  (throw (ex-info "Not implemented" {})))

(defn stop-watch [pool]
  (throw (ex-info "Not implemented" {})))