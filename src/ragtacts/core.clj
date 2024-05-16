(ns ragtacts.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [stringify-keys]]
            [ragtacts.embedding.open-ai :refer [open-ai-embedding]]
            [ragtacts.llm.base :as llm]
            [ragtacts.llm.open-ai]
            [ragtacts.loader.doc :as doc]
            [ragtacts.loader.web :as web]
            [ragtacts.splitter.recursive :refer [recursive-splitter]]
            [ragtacts.vector-store.base :as vector-store]
            [ragtacts.vector-store.in-memory :refer [in-memory-vector-store]])
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; loader

(defn- http? [data-sources]
  (some? (or (re-find #"^https://" data-sources)
             (re-find #"^http://" data-sources))))

(defn- path? [data-sources]
  (let [path (str/replace data-sources #"~" (System/getProperty "user.home"))]
    (try
      (.exists (io/file path))
      (catch Exception _
        false))))

(defn get-text [source]
  (cond
    (http? source) (web/get-text source)
    (path? source) (doc/get-text source)
    :else (throw (ex-info (str "Unknown data source:" source) {:source source}))))