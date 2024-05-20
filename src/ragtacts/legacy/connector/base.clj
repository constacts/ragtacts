(ns ragtacts.legacy.connector.base)

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
  (connect [this callback opts])
  (close [this])
  (closed? [this]))
