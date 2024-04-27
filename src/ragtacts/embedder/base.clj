(ns ragtacts.embedder.base
  (:require [clj-commons.humanize :as h]))

(defrecord Embedding [doc-id text vectors metadata])

(defn make-embedding
  ([vectors]
   (make-embedding nil nil vectors nil))
  ([doc-id text vectors metadata]
   (->Embedding doc-id text vectors metadata)))

(defmethod print-method Embedding [vectors ^java.io.Writer w]
  (.write w (str (into {} (update vectors :text #(h/truncate % 30))))))

(defprotocol Embedder
  (embed [this chunks]))
