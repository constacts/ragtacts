(ns ragtacts.connector.web-page
  (:require [hato.client :as http]
            [ragtacts.connector.base :refer [Connector make-change-log make-doc]])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defrecord WebPageConnector [url]
  Connector
  (get-change-logs [_ last-change]
    (let [client (http/build-http-client {:connect-timeout 10000
                                          :redirect-policy :always})
          {:keys [status body]} (http/get url {:http-client client})]
      (if (= status 200)
        (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from body))]
          {:last-change nil
           :change-logs [(make-change-log {:type :create
                                           :doc (make-doc url (.text doc))})]})
        {:last-change nil
         :change-logs []}))))

(defn make-web-page-connector [opts]
  (map->WebPageConnector opts))

