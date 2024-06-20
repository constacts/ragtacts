(ns ragtacts.embedding.base
  (:require [clj-ulid :refer [ulid]]))

(defmulti embed
  "Return the embedding of a texts.
   
  Args:
  - embedding: A map with the following
    - `:type`: A keyword with the embedding type.
  - texts: A list of strings.
   
  Returns:
  - A list of float embeddings."
  (fn [{:keys [type]} & params] type))

(defn text->doc
  "Return a document from a text or a document.

   Args:
   - text-or-doc: A string or a map with the following
   
   Returns:
   - A map with the following keys:
     - `:id`: A string with the document id.
     - `:text`: A string with the document text.
     - `:metadata`: A map with the document metadata."
  [text-or-doc]
  (let [{:keys [id text metadata]} text-or-doc]
    {:id (or id (ulid))
     :text (if (string? text-or-doc)
             text-or-doc
             text)
     :metadata (or metadata {})}))

(comment
  ;;
  )

