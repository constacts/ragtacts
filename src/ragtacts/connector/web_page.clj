(ns ragtacts.connector.web-page
  (:require [clojure.string :as str]
            [hato.client :as http]
            [overtone.at-at :as at]
            [ragtacts.connector.base :refer [Connector empty-change-log-result
                                             make-change-log
                                             make-change-log-result]]
            [ragtacts.document-loader.base :refer [load-doc make-doc]]
            [ragtacts.document-loader.html :refer [make-html-loader]]
            [ragtacts.document-loader.text :refer [make-text-loader]]
            [ragtacts.logging :as log]))

(defn- html? [headers]
  (str/starts-with? (get headers "content-type") "text/html"))

(defrecord WebPageConnector [url interval pool !handler !last-change]
  Connector
  (connect [_ callback opts]
    (when (:last-change opts)
      (reset! !last-change (:last-change opts)))
    (reset! !handler
            (at/interspaced
             interval
             (fn []
               (log/debug "Start WebPageConnector" url)
               (let [client (http/build-http-client {:connect-timeout 10000
                                                     :redirect-policy :always})
                     request-headers (cond-> {}
                                       (:last-modified @!last-change) (assoc "if-modified-since" (:last-modified @!last-change))
                                       (:etag @!last-change) (assoc "if-none-match" (:etag @!last-change)))
                     {:keys [status headers body]} (http/get url {:http-client client
                                                                  :headers request-headers})
                     last-modified (get headers "last-modified")
                     etag (get headers "etag")
                     change-log-result (case status
                                         200 (let [loader (cond
                                                            (html? headers) (make-html-loader {})
                                                            :else (make-text-loader {}))]
                                               (swap! !last-change assoc :last-modified last-modified :etag etag)
                                               (make-change-log-result [(make-change-log
                                                                         {:type :create
                                                                          :doc (load-doc loader url body)})]))
                                         404 (make-change-log-result [(make-change-log
                                                                       {:type :delete
                                                                        :doc (make-doc url nil)})])
                                         empty-change-log-result)]
                 (callback {:type :complete :change-log-result change-log-result})))
             pool)))

  (close [_]
    (log/debug "Stopping WebPageConnector" url)
    (at/stop-and-reset-pool! pool)
    @!last-change)

  (closed? [_]
    (not (:scheduled? (:val @!handler)))))

(defn make-web-page-connector [opts]
  (map->WebPageConnector (merge opts {:!handler (atom nil)
                                      :pool (at/mk-pool)
                                      :interval (* 1000 10)
                                      :!last-change (atom
                                                     {:last-modified nil
                                                      :etag nil})})))
