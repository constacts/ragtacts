(ns ragtacts.types)

;; Document 

(defrecord Document [id text metadata])

(defn make-doc
  ([text]
   (make-doc nil text))
  ([id text]
   (make-doc id text {}))
  ([id text metadata]
   (->Document id text metadata)))

;; Chunk

(defrecord Chunk [doc-id text metadata]
  Object
  (toString [_]
    (str "Chunk: ")))

(defn make-chunk
  ([text]
   (make-chunk nil text))
  ([doc-i text]
   (make-chunk doc-i text {}))
  ([doc-i text metadata]
   (->Chunk doc-i text metadata)))

;; Vectors

(defrecord Vectors [doc-id text vectors metadata])

(defn make-vectors [doc-id text vectors metadata]
  (->Vectors doc-id text vectors metadata))

;; Answer

(defrecord Answer [text])

(defn make-answer [text]
  (->Answer text))