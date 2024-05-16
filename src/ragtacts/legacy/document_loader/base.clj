(ns ragtacts.legacy.document-loader.base
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

(defprotocol DocumentLoader
  (load-doc [this doc-id source]))