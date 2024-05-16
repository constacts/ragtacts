(ns ragtacts.new.loader.web
  (:require [clojure.string :as str]
            [overtone.at-at :as at]
            [hato.client :as http])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defn- html? [headers]
  (str/starts-with? (get headers "content-type") "text/html"))

(defn get-text
  ([url]
   (get-text url nil))
  ([url {:keys [last-modified etag]}]
   (let [client (http/build-http-client {:connect-timeout 10000
                                         :redirect-policy :always})
         request-headers (cond-> {}
                           last-modified (assoc "if-modified-since" last-modified)
                           etag (assoc "if-none-match" etag))
         {:keys [status headers body]} (http/get url {:http-client client
                                                      :headers request-headers})
         last-change {:last-modified (get headers "last-modified")
                      :etag (get headers "etag")}
         content-type (get headers "content-type")]
     (case status
       200 (if (html? headers)
             (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from body))]
               ^{:last-change last-change} {:id (str url)
                                            :text (.text doc)
                                            :metadata {}})
             (throw (ex-info (str "Unsupported content type: " content-type)
                             {:error :unsupported-content-type
                              :content-type content-type})))
       404 (throw (ex-info (str "Page not found: " url) {:error :not-found
                                                         :url url}))
       nil))))

(defn watch [{:keys [url interval last-change]} callback]
  (let [interval (or interval 10000)
        !last-change (atom (or last-change {:last-modified nil
                                            :etag nil}))
        pool (at/mk-pool)]
    (at/interspaced
     interval
     (fn []
       (let [result (try
                      (get-text url @!last-change)
                      (catch Exception e
                        (ex-data e)))]
         (if (= :not-found (:error result))
           (callback {:type :delete :id (str url)})
           (when result
             (callback {:type (if (or (:last-modified @!last-change) (:etag @!last-change))
                                :update
                                :create)
                        :doc result})
             (reset! !last-change (:last-change (meta result)))))))
     pool)
    pool))

(defn stop-watch [pool]
  (at/stop-and-reset-pool! pool))

(comment
  (get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")
  ;;
  )