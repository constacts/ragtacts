(ns ragtacts.embedding.base
  (:require [clj-ulid :refer [ulid]]))

(defmulti embed (fn [{:keys [type]} texts] type))

(defn text->doc [text-or-doc]
  (let [{:keys [id text metadata]} text-or-doc]
    {:id (or id (ulid))
     :text (if (string? text-or-doc)
             text-or-doc
             text)
     :metadata (or metadata {})}))

(comment
  ;;
  )

