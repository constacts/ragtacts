(ns ragtacts.new.core
  (:require [clojure.walk :refer [stringify-keys]]
            [ragtacts.new.embedding.open-ai :refer [open-ai-embedding]]
            [ragtacts.new.llm.base :as llm]
            [ragtacts.new.llm.open-ai]
            [ragtacts.new.vector-store.base :as vector-store]
            [ragtacts.new.splitter.recursive :refer [recursive-splitter]]
            [ragtacts.new.vector-store.in-memory :refer [in-memory-vector-store]])
  (:import [com.hubspot.jinjava Jinjava]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; llm

(def ask llm/ask)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; prompt 

(defn prompt
  "Returns a string that prompts the user to answer a question.

   Args:
   - template: Jinja template string.
   - params: A map of parameters to pass to the prompt template.

   Returns:
   - String: The prompt to ask the user.

   Example:
   ```clojure
   (prompt \"Question: {{ question }}\" {:question \"Hello!\"})
   ```
   "
  [template params]
  (.render (Jinjava.) template (stringify-keys params)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; vector Store 

(def save vector-store/save)

(def search vector-store/search)

(def embed vector-store/embed)

(defn vector-store
  ([]
   (vector-store {}))
  ([{:keys [embedding splitter db]}]
   (if (and db (not (:type db)))
     (throw (ex-info "db must have a `:type` key" {:db db}))
     {:embedding (or embedding (open-ai-embedding))
      :splitter (or splitter (recursive-splitter {:size 500 :overlap 10}))
      :db (or db (in-memory-vector-store))})))
