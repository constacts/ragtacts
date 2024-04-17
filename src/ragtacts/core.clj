(ns ragtacts.core
  (:refer-clojure :exclude [sync chunk])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [ragtacts.connector.base :as connector]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.base :as embedder]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.base :as llm]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.logging :as log]
            [ragtacts.memory.base :as memory :refer [make-chat-msg]]
            [ragtacts.memory.window :refer [make-window-chat-memory]]
            [ragtacts.prompt-template.base :as prompt-template]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.splitter.base :as splitter]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.types :refer [make-chunk]]
            [ragtacts.vector-store.base :as vector-store]
            [ragtacts.vector-store.milvus :refer [make-milvus-vector-store]]))

(defn- conversational-msgs [{:keys [chat-history
                                    system-prompt
                                    user-prompt]}]
  (concat
   [(make-chat-msg {:type :system :text system-prompt})]
   chat-history
   [(make-chat-msg {:type :user :text user-prompt})]))

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
      (log/debug "Sync" connector)
      (let [docs (connector/get-docs connector)
            _ (log/debug (str "Documents count:" (count docs)) docs)
            chunks (splitter/split splitter docs)
            _ (binding [*print-length* 5]
                (log/debug (str "Chunks count:" (count chunks)) chunks))
            vectors (embedder/embed embedder chunks)
            _ (binding [*print-length* 5]
                (log/debug (str "Vectors count:" (count vectors)) vectors))]
        (vector-store/save vector-store vectors)))
    this)

  (chat [_ prompt]
    (let [prompt-vectors (embedder/embed embedder [(make-chunk prompt)])
          _ (binding [*print-length* 5]
              (log/debug (str "Vectors count:" (count prompt-vectors)) prompt-vectors))
          chunks (vector-store/search vector-store prompt-vectors nil)
          _ (binding [*print-length* 5]
              (log/debug (str "Chunks count:" (count chunks)) chunks))
          _ (memory/add-chat memory (make-chat-msg {:type :user :text prompt}))
          chat-history (memory/get-chat-history memory)
          system-prompt (prompt-template/prompt prompt-template {:context (->> chunks
                                                                               (map :text)
                                                                               (str/join "\n"))})
          _ (log/debug (str "Prompt:" prompt))
          answer (llm/query llm (conversational-msgs {:chat-history chat-history
                                                      :system-prompt system-prompt
                                                      :user-prompt prompt}))]
      (memory/add-chat memory (make-chat-msg {:type :ai :text (:text answer)}))
      (log/debug "Memory:" (memory/get-chat-history memory))
      answer)))


(defn default-connector [url]
  (make-web-page-connector {:url url}))

(defn default-splitter []
  (make-recursive {:size 1000 :overlap 20}))

(defn default-embedder []
  (make-open-ai-embedder {:model "text-embedding-3-small"}))

(defn default-vector-store []
  (make-milvus-vector-store {:collection "mycoll3" :db "mydb"}))

(defn default-memory []
  (make-window-chat-memory {:size 10}))

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
                 llm]}]
   (let [memory (or memory (default-memory))
         promt-template (or prompt-template (default-prompt-template))]
     (map->AppImpl {:connectors (or connectors (map default-connector urls))
                    :splitter (or splitter (default-splitter))
                    :embedder (or embedder (default-embedder))
                    :vector-store (or vector-store (default-vector-store))
                    :memory memory
                    :prompt-template prompt-template
                    :llm (assoc (or llm (default-llm))
                                :memory memory
                                :promt-template promt-template)}))))

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
   {:window {:cons-fn make-window-chat-memory}}

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
  [["-m" "--mode [query|chat|sync|server]" "Run in chat or server mode"
    :default "query"]
   ["-f" "--file FILE" "File for app configuration"
    :default "resources/default.edn"]
   ["-u" "--urls URLS" "URLs to sync"
    :multi true
    :update-fn conj]
   ["-p" "--prompt PROMPT" "Prompt to chat with"]])

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [mode file urls prompt]} options]
    (log/debug "Options" options)
    (with-open [r (io/reader file)]
      (let [config (edn/read (java.io.PushbackReader. r))
            _ (log/debug "Config file" config)
            app (app-from-config config urls)]
        (case mode
          "chat" (do (loop []
                       (print "\u001B[32mPrompt: \u001B[0m")
                       (flush)
                       (let [prompt (str/trim (read-line))]
                         (when-not (= prompt "")
                           (println "\u001B[34mAI:" (:text (chat app prompt)) "\u001B[0m")
                           (recur))))
                     (println "\u001B[34mAI: Bye~!\u001B[0m"))
          "sync" (sync app)
          "server" (println "Server mode not implemented yet")
          (println "\u001B[34mAI:" (:text (chat app prompt)) "\u001B[0m"))))))
