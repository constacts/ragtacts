(ns ragtacts.embedding.base
  (:require [clj-ulid :refer [ulid]]))

(defmulti embed (fn [{:keys [type]} texts] type))

(defn text->doc [text-or-doc]
  (if (string? text-or-doc)
    {:id (ulid)
     :text text-or-doc
     :metadata {}}
    text-or-doc))