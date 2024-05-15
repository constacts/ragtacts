(ns ragtacts.new.loader.web
  (:require [clojure.string :as str]
            [hato.client :as http])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defn web-text [url]
  (let [client (http/build-http-client {:connect-timeout 10000
                                        :redirect-policy :always})
        {:keys [status headers body]} (http/get url {:http-client client})]
    (when (= status 200)
      (when (str/starts-with? (get headers "content-type") "text/html")
        (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from body))]
          (.text doc))))))

(comment
  (web-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")
  ;;
  )