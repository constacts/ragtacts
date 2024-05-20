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
  (let [result (reduce (fn [s [k v]]
                         (str/replace s (re-pattern (str "\\{\\s*" (name k) "\\s*\\}")) (str v)))
                       s
                       ctx)]
    (if-let [[_ missing-key] (re-find #"\{\s*([^}]+)\s*\}" result)]
      (let [k (str/trim missing-key)]
        (throw (ex-info (str "Missing key: " k) {:key k})))
      result)))

(comment
  (f-string "Hello, {lang} { name }!" {:lang "Clojure"
                                       :name "world"})
  ;;
  )