(ns ragtacts.connector.folder
  (:require [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [ragtacts.connector.base :refer [Connector make-change-log
                                             make-change-log-result]]
            [ragtacts.document-loader.base :refer [load-doc]]
            [ragtacts.document-loader.html :refer [make-html-loader]]
            [ragtacts.document-loader.pdf :refer [make-pdf-loader]]
            [ragtacts.logging :as log]))

(defn- loader-for-file [path]
  (cond
    (str/ends-with? path ".html") (make-html-loader {})
    (str/ends-with? path ".pdf") (make-pdf-loader {})
    :else nil))

(defrecord FolderConnector [path !watcher]
  Connector
  (connect [_ callback]
    (reset! !watcher (beholder/watch
                      (fn [{:keys [type path]}]
                        (log/debug type path)
                        (when-let [loader (loader-for-file path)]
                          (let [change-log (case type
                                             :create (make-change-log {:type :create
                                                                       :doc (load-doc loader (str path) path)})
                                          ;;  :modify (make-change-log {:type :update
                                          ;;                            :doc nil})
                                          ;;  :delete (make-change-log {:type :delete :doc nil})
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