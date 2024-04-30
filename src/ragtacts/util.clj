(ns ragtacts.util
  (:require [hato.client :as hc]
            [hato.middleware :as hm]
            [clojure.java.io :as io :refer [output-stream]])
  (:import [org.apache.commons.io.input CountingInputStream]
           [me.tongfei.progressbar ProgressBar]))

(defn wrap-downloaded-bytes-counter
  [client]
  (fn [req]
    (let [resp (client req)
          counter (CountingInputStream. (:body resp))]
      (merge resp {:body                     counter
                   :downloaded-bytes-counter counter}))))

(def download-middleware
  (concat [(first hm/default-middleware) wrap-downloaded-bytes-counter]
          (drop 1 hm/default-middleware)))

(defn download-with-progress [url target]
  (let [request (hc/get url {:as :stream
                             :middleware download-middleware
                             :http-client {:redirect-policy :always}})
        length (Long. (get-in request [:headers "content-length"] 0))
        buffer-size (* 1024 100)]
    (with-open [input (:body request)
                output (output-stream target)]
      (let [buffer (make-array Byte/TYPE buffer-size)
            counter (:downloaded-bytes-counter request)
            ^ProgressBar progress (.build (doto (ProgressBar/builder)
                                            (.setInitialMax length)
                                            (.setTaskName "Downloading")
                                            (.setUnit " MB" 1048576)))]
        (loop []
          (let [size (.read input buffer)]
            (when (pos? size)
              (.write output buffer 0 size)
              (print "\r")
              (.stepTo progress (.getByteCount counter))
              (recur))))))
    (println)))
