(ns ragtacts.collection
  (:refer-clojure :exclude [sync])
  (:require [clj-ulid :refer [ulid]]
            [ragtacts.connector.base :as connector]
            [ragtacts.embedder.base :as embedder]
            [ragtacts.logging :as log]
            [ragtacts.splitter.base :as splitter :refer [make-chunk]]
            [ragtacts.vector-store.base :as vector-store]))

(defn- apply-change-log [{:keys [splitter embedder vector-store]} {:keys [type doc]}]
  (log/debug "Apply change log:" type doc)
  (case type
    (:create :update) (let [chunks (splitter/split splitter [doc])
                            _ (binding [*print-length* 5]
                                (log/debug (str "Chunks count:" (count chunks)) chunks))
                            vectors (embedder/embed embedder chunks)
                            _ (binding [*print-length* 5]
                                (log/debug (str "Vectors count:" (count vectors)) vectors))]
                        (when (= :update type)
                          (vector-store/delete-by-id vector-store (:id doc)))
                        (vector-store/insert vector-store vectors))
    :delete (vector-store/delete-by-id vector-store (:id doc))
    (throw (ex-info "Unknown change log type" {:type type}))))

(defprotocol Collection
  (sync [this callback])
  (stop [this])
  (search [this text metadata]))

(defrecord CollectionImpl [id name connectors splitter embedder vector-store]
  Collection
  (sync [this callback]
    (log/debug "Starting Sync" connectors)
    (doseq [connector connectors]
      (connector/connect
       connector
       (fn [{:keys [change-log-result]}]
         (let [{:keys [change-logs]} change-log-result]
           (doseq [change-log change-logs]
             (apply-change-log {:splitter splitter
                                :embedder embedder
                                :vector-store vector-store} change-log))
           (callback {:type :complete :connector connector})))))
    this)

  (search [_ text metadata]
    (let [prompt-vectors (embedder/embed embedder [(make-chunk text)])]
      (vector-store/search vector-store prompt-vectors nil)))

  (stop [this]
    (log/debug "Stopping Sync")
    (doseq [connector connectors]
      (connector/close connector))
    this))

(defn make-collection [{:keys [id] :as opts}]
  (let [id (or id (ulid))]
    (map->CollectionImpl (merge opts {:id id}))))

(comment

  (make-collection {:collections [1 2]})
  ;;
  )