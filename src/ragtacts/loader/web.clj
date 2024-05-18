(ns ragtacts.loader.web
  (:require [clojure.string :as str]
            [overtone.at-at :as at]
            [hato.client :as http])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defn- html? [headers]
  (str/starts-with? (get headers "content-type") "text/html"))

(defn get-text
  "Return the text of a web page.
   
  Args:
  - url: A string with the URL of the web page.
  - last-change: A map with the following keys
    - `:last-modified`: A string with the last modified date.
    - `:etag`: A string with the etag.
   
  Returns:
  - A map with the following
    - `:id`: A string with the document id.
    - `:text`: A string with the document text.
    - `:metadata`: A map with the document metadata."
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

(defn watch
  "Watch a web page for changes.
   
   Args:
   - url: A string with the URL of the web page.
   - interval: An integer with the interval in milliseconds.
   - callback: A function that receives a map with the following
     - `:type`: A keyword with the change type. It can be `:create`, `:update`, or `:delete`.
     - `:doc`: A map with the following keys
       - `:id`: A string with the document id.
       - `:text`: A string with the document text.
       - `:metadata`: A map with the document metadata.
   
   Returns:
   - A pool that can be stopped with `stop-watch`.
   "
  [{:keys [url interval last-change]} callback]
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

(defn stop-watch
  "Stop a web page watch pool."
  [pool]
  (at/stop-and-reset-pool! pool))

(comment
  (get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")
  ;;
  )