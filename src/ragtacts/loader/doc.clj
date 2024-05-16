(ns ragtacts.loader.doc
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [dev.langchain4j.data.document Document DocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]))

(defn get-text [path]
  (let [path (str/replace path #"~" (System/getProperty "user.home"))
        ^DocumentParser parser (ApacheTikaDocumentParser.)
        ^Document doc (.parse parser (io/input-stream path))]
    {:id path
     :text (.text doc)
     :metadata {}}))

(defn watch [{:keys [path last-change]} callback]
  (throw (ex-info "Not implemented" {})))

(defn stop-watch [pool]
  (throw (ex-info "Not implemented" {})))