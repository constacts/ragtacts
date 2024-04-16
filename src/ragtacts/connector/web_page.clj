(ns ragtacts.connector.web-page
  (:require [clojure.tools.logging :as log]
            [hato.client :as http]
            [ragtacts.connector.base :refer [Connector get-docs]]
            [ragtacts.types :refer [make-doc]])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defrecord WebPageConnector [url]
  Connector
  (get-docs [_]
    (let [client (http/build-http-client {:connect-timeout 10000
                                          :redirect-policy :always})
          {:keys [status body]} (http/get url {:http-client client})]
      (if (= status 200)
        (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from body))]
          [(make-doc url (.text doc))])
        []))))

(defn make-web-page-connector [opts]
  (map->WebPageConnector opts))

