(ns ragtacts.connector.base
  (:require [clj-commons.humanize :as h]))

(defrecord Document [id text metadata])

(defmethod print-method Document [doc ^java.io.Writer w]
  (.write w (str (into {} (update doc :text #(h/truncate % 30))))))

(defn make-doc
  ([text]
   (make-doc nil text))
  ([id text]
   (make-doc id text {}))
  ([id text metadata]
   (->Document id text metadata)))

;; types
;; :create :update :delete

(defrecord ChangeLog [type doc])

(defn make-change-log [opts]
  (map->ChangeLog opts))

(defrecord ChangeLogResult [change-logs last-change])

(defn make-change-log-result
  ([change-logs]
   (make-change-log-result change-logs nil))
  ([change-logs last-change]
   (->ChangeLogResult change-logs last-change)))

(def empty-change-log-result
  (make-change-log-result [] nil))

(defprotocol Connector
  (get-change-logs [this last-change]))