(ns ragtacts.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtacts.embedding.open-ai :refer [open-ai-embedding]]
            [ragtacts.llm.base :as llm]
            [ragtacts.llm.open-ai :as open-ai]
            [ragtacts.loader.doc :as doc]
            [ragtacts.loader.web :as web]
            [ragtacts.util :refer [f-string]]
            [ragtacts.prompt.langchain :as langchain]
            [ragtacts.vector-store.milvus :refer [milvus]]
            [ragtacts.splitter.recursive :refer [recursive-splitter]]
            [ragtacts.vector-store.base :as vector-store]
            [ragtacts.vector-store.in-memory :refer [in-memory-vector-store]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; llm

(def ask llm/ask)

(def with-images open-ai/with-images)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; prompt 

(defn prompt
  "Returns a string that prompts the user to answer a question.

   Args:
   - template: Python `str.fomrat` string.
   - params: A map of parameters to pass to the prompt template.

   Returns:
   - String: The prompt to ask the user.

   Example:
   ```clojure
   (prompt \"Question: { question }\" {:question \"Hello!\"})
   ```
   "
  [template params]
  (f-string template params))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; vector Store 

(def add vector-store/add)

(def search vector-store/search)

(def embed vector-store/embed)

(def delete vector-store/delete)

(defn vector-store
  "Return a vector store.
   
   Args:
   - embedding: A map with the following
     - `:type`: A keyword with the embedding type.
   - splitter: A splitter or a map with the following
     - `:size`: An integer with the size of the split.
     - `:overlap`: An integer with the overlap of the split. 
   - db: A map with the following
     - `:type`: A keyword with the db type.
   
   Example:
   ```clojure
   (vector-store)

   (vector-store {:embedding (open-ai-embedding)})
   
   (vector-store {:splitter {:size 500 :overlap 10}
                  :db (in-memory-vector-store)})
   
   (vector-store {:splitter (recursive-splitter {:size 500 :overlap 10})
                  :db (in-memory-vector-store)})
   
   (vector-store {:db (milvus {:collection \"animals\"})})
   ```"
  ([]
   (vector-store {}))
  ([{:keys [embedding splitter db]}]
   (if (and db (not (:type db)))
     (throw (ex-info "db must have a `:type` key" {:db db}))
     (let [splitter (cond
                      (nil? splitter) (recursive-splitter {:size 500 :overlap 10})
                      (nil? (:type splitter)) (recursive-splitter {:size (or (:size splitter) 500)
                                                                   :overlap (or (:overlap splitter) 10)})
                      (:type splitter) splitter
                      :else (throw (ex-info "splitter must have a `:type` key" {:splitter splitter})))]
       {:embedding (or embedding (open-ai-embedding))
        :splitter splitter
        :db (or db (in-memory-vector-store))}))))

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

(comment
  (let [db (vector-store)
        text (web/get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")
        rag-prompt (langchain/hub "rlm/rag-prompt")
        question "What is RAG?"]
    (add db [text])
    (-> (ask (prompt rag-prompt {:context (str/join "\n" (search db question))
                                 :question question}))
        last
        :ai))
  ;;
  )