(ns ragtacts.splitter.base
  (:require [clj-commons.humanize :as h]))

(defrecord Chunk [doc-id text metadata])

(defn make-chunk
  ([text]
   (make-chunk nil text))
  ([doc-i text]
   (make-chunk doc-i text {}))
  ([doc-i text metadata]
   (->Chunk doc-i text metadata)))

(defmethod print-method Chunk [chunk ^java.io.Writer w]
  (.write w (str (into {} (update chunk :text #(h/truncate % 30))))))

(defprotocol Splitter
  (split [this doc]))