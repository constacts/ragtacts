(ns ragtacts.main
  (:gen-class)
  (:refer-clojure :exclude [sync])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [overtone.at-at :as at]
            [ragtacts.app :as app]
            [ragtacts.collection :as collection]
            [ragtacts.connector.base :as connector]
            [ragtacts.connector.sql :refer [make-sql-connector]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.core :as core]
            [ragtacts.embedder.all-mini-lm-l6-v2 :refer [make-all-mini-lm-l6-v2-embedder]]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.llama-cpp :refer [make-llama-cpp-llm]]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.logging :as log]
            [ragtacts.memory.window :refer [make-window-chat-memory]]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.server :as server]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]
            [ragtacts.vector-store.milvus :refer [make-milvus-vector-store]]))

(def cli-options
  [["-m" "--mode [query|chat|server]" "Run in chat or server mode"
    :default "query"]
   ["-f" "--file FILE" "File for app configuration"
    :default "resources/default.edn"]
   ["-d" "--data-sources URLs or PATHs" "Data sources to sync with"
    :multi true
    :update-fn conj]
   ["-p" "--prompt PROMPT" "Prompt to chat with"]])

(def usage
  "Usage: 

  query mode: [default mode]
    $ ragtacts -p \"prompt\" -d [Url or Path] -d ...
      or
    $ ragtacts -m query -p \"prompt\" -d [Url or Path] -d ...
   
  chat mode: 
    $ ragtacts -m chat -d [Url or Path] -d ...
   
  server mode: 
    $ ragtacts -m server
")

(def component-map
  {:connector
   {:web-page {:cons-fn make-web-page-connector}
    :sql {:cons-fn make-sql-connector}}

   :splitter
   {:recursive {:cons-fn make-recursive}}

   :embedder
   {:open-ai {:cons-fn make-open-ai-embedder}
    :all-mini-lm-l6-v2 {:cons-fn make-all-mini-lm-l6-v2-embedder}}

   :vector-store
   {:in-memory {:cons-fn make-in-memory-vector-store}
    :milvus {:cons-fn make-milvus-vector-store}}

   :memory
   {:window {:cons-fn make-window-chat-memory}}

   :prompt-template
   {:default {:cons-fn make-default-prompt-template}}

   :llm
   {:open-ai {:cons-fn make-open-ai-llm}
    :llama-cpp {:cons-fn make-llama-cpp-llm}}})

(defn- eval-config [config]
  (into {}
        (map
         (fn [[component-key value]]
           (if (= component-key :connectors)
             [:connectors (map
                           #(when-let [fn (get-in component-map [:connector (:type %) :cons-fn])]
                              (fn (:params %)))
                           value)]
             (when-let [fn (get-in component-map [component-key (:type value) :cons-fn])]
               [component-key (fn (:params value))])))
         config)))

(defn- wait [{:keys [connectors]}]
  (let [latches (into {} (map (fn [connector]
                                [connector (promise)])
                              connectors))
        pool (at/mk-pool)]
    (at/interspaced 1000
                    (fn []
                      (doseq [connector connectors]
                        (when (connector/closed? connector)
                          (deliver (get latches connector) :complete))))
                    pool)
    (doseq [latch (vals latches)]
      @latch)
    (at/stop-and-reset-pool! pool)))

(defn- validate-options [{:keys [mode data-sources prompt]}]
  (case mode
    ("chat" "server") (and (seq data-sources) (nil? prompt))
    "query" (and prompt (seq data-sources))
    nil))

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)]
    (if (validate-options options)
      (let [{:keys [mode file data-sources prompt]} options
            config (with-open [r (io/reader file)]
                     (edn/read (java.io.PushbackReader. r)))
            _ (log/debug "Config file" config)
            chat-app (core/app data-sources (eval-config config))
            stop-app (fn []
                       (-> (:collection chat-app)
                           collection/stop
                           wait)
                       (shutdown-agents))
            set-shutdown-hook (fn []
                                (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))]
        (case mode
          "chat" (let [latch (promise)]
                   (set-shutdown-hook)
                   (log/debug "Syncing...")
                   (flush)
                   (core/sync chat-app (fn [_ _] (deliver latch :complete)))
                   (log/debug @latch)
                   (loop []
                     (print "\u001B[32mPrompt: \u001B[0m")
                     (flush)
                     (let [prompt (str/trim (read-line))]
                       (when-not (= prompt "")
                         (println "\u001B[34mAI:" (:text (app/chat chat-app prompt)) "\u001B[0m"))
                       (recur))))
          "server" (let [server (server/start chat-app {})]
                     (.addShutdownHook (Runtime/getRuntime) (Thread. #(do
                                                                        (server/stop server)
                                                                        (stop-app)))))
          "query" (let [latch (promise)]
                    (core/sync chat-app (fn [_ _] (deliver latch :complete)))
                    (log/debug @latch)
                    (println "\u001B[34mAI:" (:text (app/chat chat-app prompt)) "\u001B[0m")
                    (stop-app))
          (println usage)))
      (println usage))))