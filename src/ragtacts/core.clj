(ns ragtacts.core
  (:refer-clojure :exclude [sync])
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [ragtacts.connector.base :as connector]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.base :as embedder]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.base :as llm]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.prompt-template.base :as prompt-template]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.splitter.base :as splitter]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.types :refer [make-chunk]]
            [ragtacts.vector-store.base :as vector-store]
            [ragtacts.vector-store.milvus :refer [make-milvus-vector-store]]))

(defprotocol App
  (sync [this])
  (chat [this prompt]))

(defrecord AppImpl [id
                    connectors
                    splitter
                    embedder
                    vector-store
                    memory
                    prompt-template
                    llm]
  App
  (sync [this]
    (doseq [connector connectors]
      (let [docs (connector/get-docs connector)
            chunks (splitter/split splitter docs)
            vectors (embedder/embed embedder chunks)]
        (vector-store/save vector-store vectors)))
    this)

  (chat [_ prompt]
    (let [prompt-vectors (embedder/embed embedder [(make-chunk prompt)])
          chunks (vector-store/search vector-store prompt-vectors nil)
          prompt (prompt-template/prompt prompt-template prompt {:context (->> chunks
                                                                               (map :text)
                                                                               (str/join "\n"))})
          answer (llm/query llm prompt)]
      answer)))

(defn default-connector [url]
  (make-web-page-connector {:url url}))

(defn default-splitter []
  (make-recursive {:size 1000 :overlap 20}))

(defn default-embedder []
  (make-open-ai-embedder {:model "text-embedding-3-small"}))

(defn default-vector-store []
  (make-milvus-vector-store {:collection "mycoll3" :db "mydb"}))

(defn default-memory [])

(defn default-prompt-template []
  (make-default-prompt-template nil))

(defn default-llm []
  (make-open-ai-llm {:model "gpt-3.5-turbo-0125"}))

(defn app
  ([urls]
   (app urls {}))
  ([urls {:keys [connectors
                 splitter
                 embedder
                 vector-store
                 memory
                 prompt-template
                 llm]
          :or {connectors (map default-connector urls)
               splitter (default-splitter)
               embedder (default-embedder)
               vector-store (default-vector-store)
               memory (default-memory)
               prompt-template (default-prompt-template)
               llm (default-llm)}}]
   (map->AppImpl {:connectors connectors
                  :splitter splitter
                  :embedder embedder
                  :vector-store vector-store
                  :memory memory
                  :prompt-template prompt-template
                  :llm llm})))

(def components
  {:connector
   {:web-page {:cons-fn make-web-page-connector}}

   :splitter
   {:recursive {:cons-fn make-recursive}}

   :embedder
   {:open-ai {:cons-fn make-open-ai-embedder}}

   :vector-store
   {:milvus {:cons-fn make-milvus-vector-store}}

   :memory
   {}

   :prompt-template
   {:default {:cons-fn make-default-prompt-template}}

   :llm
   {:open-ai {:cons-fn make-open-ai-llm}}})

(defn- app-from-config [config urls]
  (->> (map
        (fn [[component-key {:keys [type params]}]]
          (when-let [fn (get-in components [component-key type :cons-fn])]
            [component-key (fn params)]))
        config)
       (into {})
       (app urls)))

(def cli-options
  [["-f" "--file FILE" "File for app configuration"
    :default "resources/default.edn"]
   ["-u" "--urls URLS" "URLs to sync"
    :multi true
    :update-fn conj]
   ["-p" "--prompt PROMPT" "Prompt to chat with"]])

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [file urls prompt]} options]
    (with-open [r (io/reader file)]
      (let [config (edn/read (java.io.PushbackReader. r))]
        (-> (app-from-config config urls)
            sync
            (chat prompt)
            :text
            println)))))