(ns ragtacts.util
  (:require [clojure.java.io :as io :refer [output-stream]]
            [clojure.string :as str]
            [hato.client :as hc]
            [hato.middleware :as hm])
  (:import [me.tongfei.progressbar ProgressBar]
           [org.apache.commons.io.input CountingInputStream]))

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

(defn f-string
  "Python-like f-string
   
   Example:
   ```clojure
   (f-string \"Hello, {name}!\" {:name \"world\"})
   ;; => \"Hello, world!\"
   ```
   "
  [s ctx]
  (let [get-value (fn [k]
                    (if-let [v (get ctx (keyword k))]
                      v
                      (throw (ex-info (str "key not found: " k) {:key k}))))]
    (loop [s s
           result ""
           k nil]
      (if-let [c (first s)]
        (cond
          (and (= c \{) k) (recur (rest s) (str result c) "")
          (= c \{) (recur (rest s) (str result) "")
          (and (= c \}) k) (recur (rest s) (str result (get-value (str/trim k))) nil)
          k (recur (rest s) result (str k c))
          :else (recur (rest s) (str result c) nil))
        result))))

(comment
  ;;
  )