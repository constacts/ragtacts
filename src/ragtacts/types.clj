(ns ragtacts.types
  (:require [clj-commons.humanize :as h]))

;; Document 

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

;; Chunk

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

;; Vectors

(defrecord Vectors [doc-id text vectors metadata])

(defn make-vectors [doc-id text vectors metadata]
  (->Vectors doc-id text vectors metadata))

(defmethod print-method Vectors [vectors ^java.io.Writer w]
  (.write w (str (into {} (update vectors :text #(h/truncate % 30))))))

;; Answer

(defrecord Answer [text])

(defn make-answer [text]
  (->Answer text))

(comment

  (binding [*print-length* 2]
    ;; (pr-str [1 2 3 4])

    (pr-str (make-doc "Hello, world! direct buffer constructor: unavailable: Reflective setAccessible(true) disabled")))

  ;;
  )