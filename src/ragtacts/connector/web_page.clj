(ns ragtacts.connector.web-page
  (:require [clojure.string :as str]
            [hato.client :as http]
            [ragtacts.connector.base :refer [Connector empty-change-log-result
                                             make-change-log
                                             make-change-log-result]]
            [ragtacts.document-loader.base :refer [load-doc]]
            [ragtacts.document-loader.html :refer [make-html-loader]]
            [ragtacts.document-loader.text :refer [make-text-loader]]
            [ragtacts.logging :as log]
            [overtone.at-at :as at]))

(defrecord WebPageConnector [url interval pool !handler]
  Connector
  (connect [_ callback]
    (reset! !handler
            (at/interspaced
             interval
             (fn []
               (log/debug "Start WebPageConnector" url)
               (let [client (http/build-http-client {:connect-timeout 10000
                                                     :redirect-policy :always})
                     {:keys [status headers body]} (http/get url {:http-client client})
                     loader (cond
                              (str/starts-with? (get headers "content-type") "text/html") (make-html-loader {})
                              :else (make-text-loader {}))
                     change-log-result (if (= status 200)
                                         (make-change-log-result [(make-change-log
                                                                   {:type :create
                                                                    :doc (load-doc loader url body)})])
                                         empty-change-log-result)]
                 (callback {:type :complete :change-log-result change-log-result})))
             pool)))

  (close [_]
    (log/debug "Stop WebPageConnector" url)
    (when @!handler
      (at/stop @!handler)))

  (closed? [_]
    (not (:scheduled? (:val @!handler)))))

(defn make-web-page-connector [opts]
  (map->WebPageConnector (merge opts {:!handler (atom nil)
                                      :pool (at/mk-pool)
                                      :interval (* 1000 10)})))
