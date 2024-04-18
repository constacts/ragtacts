(ns ragtacts.vector-store.base
  (:require [clj-commons.humanize :as h]))

(defrecord Vectors [doc-id text vectors metadata])

(defn make-vectors
  ([vectors]
   (make-vectors nil nil vectors nil))
  ([doc-id text vectors metadata]
   (->Vectors doc-id text vectors metadata)))

(defmethod print-method Vectors [vectors ^java.io.Writer w]
  (.write w (str (into {} (update vectors :text #(h/truncate % 30))))))

(defprotocol VectorStore
  (insert [this vectors])
  (delete-by-id [this id])
  (search [this vectors opts]))