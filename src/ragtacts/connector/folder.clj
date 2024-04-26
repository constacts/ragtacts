(ns ragtacts.connector.folder
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [ragtacts.connector.base :refer [Connector make-change-log
                                             make-change-log-result]]
            [ragtacts.document-loader.base :refer [load-doc make-doc]]
            [ragtacts.document-loader.html :refer [make-html-loader]]
            [ragtacts.document-loader.pdf :refer [make-pdf-loader]]
            [ragtacts.logging :as log]))

(defn- loader-for-file [path]
  (cond
    (str/ends-with? path ".html") (make-html-loader {})
    (str/ends-with? path ".pdf") (make-pdf-loader {})
    :else nil))

(defn- load-all-files [path callback]
  (doseq [file (file-seq (io/file path))]
    (let [file-path (.getAbsolutePath file)
          loader (loader-for-file file-path)]
      (when loader
        (log/debug "Load file" file-path)
        (let [doc (load-doc loader (str file-path) file)
              change-log (make-change-log {:type :create :doc doc})]
          (callback {:type :complete
                     :change-log-result (make-change-log-result [change-log])}))))))

(defrecord FolderConnector [path !watcher]
  Connector
  (connect [_ callback]
    (future
      (Thread/sleep 500)
      (load-all-files path callback))
    (reset! !watcher (beholder/watch
                      (fn [{:keys [type path]}]
                        (log/debug type path)
                        (when-let [loader (loader-for-file path)]
                          (let [doc (when-not (= :delete type)
                                      (load-doc loader (str path) (.toFile path)))
                                change-log (case type
                                             :create (make-change-log {:type :create
                                                                       :doc doc})
                                          ;;  :modify (make-change-log {:type :update
                                          ;;                            :doc nil})
                                             :delete (make-change-log {:type :delete
                                                                       :doc (make-doc (str path) nil)})
                                             (log/error "Unknown type" type path))
                                change-log-result (make-change-log-result [change-log])]
                            (log/debug change-log-result)
                            (callback {:type :complete :change-log-result change-log-result}))))
                      path)))

  (close [_]
    (when @!watcher
      (beholder/stop @!watcher)))

  (closed? [_]
    (if @!watcher
      (.isClosed @!watcher)
      true)))

(defn make-folder-connector [opts]
  (map->FolderConnector (merge opts {:!watcher (atom nil)})))