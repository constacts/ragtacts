(ns ragtacts.legacy.connector.folder
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [ragtacts.legacy.connector.base :refer [Connector empty-change-log-result
                                                    make-change-log
                                                    make-change-log-result]]
            [ragtacts.legacy.document-loader.base :refer [load-doc make-doc]]
            [ragtacts.legacy.document-loader.html :refer [make-html-loader]]
            [ragtacts.legacy.document-loader.office-doc :refer [make-office-doc-loader]]
            [ragtacts.logging :as log])
  (:import [java.io File]))

(defn- loader-for-file [path]
  (cond
    (str/ends-with? path ".html") (make-html-loader {})
    (or (str/ends-with? path ".pdf")
        (str/ends-with? path ".doc")
        (str/ends-with? path ".docx")
        (str/ends-with? path ".ppt")
        (str/ends-with? path ".pptx")) (make-office-doc-loader {})
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

(defn- get-last-modifed [path]
  (sort-by :name (map (fn [^File file]
                        {:path (.getAbsolutePath file)
                         :last-modified (.lastModified file)})
                      (file-seq (io/file path)))))

(defrecord FolderConnector [path !watcher]
  Connector
  (connect [_ callback {:keys [last-change]}]
    (let [path (str/replace path #"~" (System/getProperty "user.home"))]
      (future
        (Thread/sleep 500)
        (callback {:type :complete :change-log-result empty-change-log-result})
        (when-not (= (sort-by :name last-change) (get-last-modifed path))
          (load-all-files path callback)))
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
                        path))))

  (close [_]
    (log/debug "Stopping FolderConnector" path)
    (when @!watcher
      (beholder/stop @!watcher)
      (get-last-modifed path)))

  (closed? [_]
    (if @!watcher
      (.isClosed @!watcher)
      true)))

(defn make-folder-connector [opts]
  (map->FolderConnector (merge opts {:!watcher (atom nil)})))