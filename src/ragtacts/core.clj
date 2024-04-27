(ns ragtacts.core
  (:refer-clojure :exclude [sync chunk])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [overtone.at-at :as at]
            [ragtacts.app :as app :refer [make-app]]
            [ragtacts.collection :as collection :refer [make-collection]]
            [ragtacts.connector.base :as connector]
            [ragtacts.connector.folder :refer [make-folder-connector]]
            [ragtacts.connector.sql :refer [make-sql-connector]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.logging :as log]
            [ragtacts.memory.window :refer [make-window-chat-memory]]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.server :as server]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]
            [ragtacts.vector-store.milvus :refer [make-milvus-vector-store]]))

(defn default-splitter []
  (make-recursive {:size 1000 :overlap 20}))

(defn default-embedder []
  (make-open-ai-embedder {:model "text-embedding-3-small"}))

(defn default-vector-store []
  (make-in-memory-vector-store nil))

(defn default-memory []
  (make-window-chat-memory {:size 10}))

(defn default-prompt-template []
  (make-default-prompt-template nil))

(defn default-llm []
  (make-open-ai-llm {:model "gpt-3.5-turbo-0125"}))

(defn- http? [data-sources]
  (some? (or (re-find #"^https://" data-sources)
             (re-find #"^http://" data-sources))))

(defn- path? [data-sources]
  (try
    (.exists (io/file data-sources))
    (catch Exception _
      false)))

(defn- infer-connector [data-sources]
  (cond
    (http? data-sources) (make-web-page-connector {:url data-sources})
    (path? data-sources) (make-folder-connector {:path data-sources})
    :else (throw (ex-info "Unknown data source" {:data-sources data-sources}))))

(def default-components
  {:splitter (default-splitter)
   :embedder (default-embedder)
   :vector-store (default-vector-store)
   :memory (default-memory)
   :prompt-template (default-prompt-template)
   :llm (default-llm)})

(def components
  {:connector
   {:web-page {:cons-fn make-web-page-connector}
    :sql {:cons-fn make-sql-connector}}

   :splitter
   {:recursive {:cons-fn make-recursive}}

   :embedder
   {:open-ai {:cons-fn make-open-ai-embedder}}

   :vector-store
   {:in-memory {:cons-fn make-in-memory-vector-store}
    :milvus {:cons-fn make-milvus-vector-store}}

   :memory
   {:window {:cons-fn make-window-chat-memory}}

   :prompt-template
   {:default {:cons-fn make-default-prompt-template}}

   :llm
   {:open-ai {:cons-fn make-open-ai-llm}}})

(defn- eval-config [config]
  (into {}
        (map
         (fn [[component-key value]]
           (if (= component-key :connectors)
             [:connectors (map
                           #(when-let [fn (get-in components [:connector (:type %) :cons-fn])]
                              (fn (:params %)))
                           value)]
             (when-let [fn (get-in components [component-key (:type value) :cons-fn])]
               [component-key (fn (:params value))])))
         config)))

(defn- wait [{:keys [connectors]}]
  (let [latches (into {} (map (fn [connector]
                                [connector (promise)])
                              connectors))]
    (at/interspaced 1000
                    (fn []
                      (doseq [connector connectors]
                        (when (connector/closed? connector)
                          (deliver (get latches connector) :complete))))
                    (at/mk-pool))
    (doseq [latch (vals latches)]
      @latch)))

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

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [mode file data-sources prompt]} options
        config (with-open [r (io/reader file)]
                 (edn/read (java.io.PushbackReader. r)))
        _ (log/debug "Config file" config)
        components (merge default-components (eval-config config))
        connectors (map infer-connector data-sources)
        collection (make-collection (if (seq connectors)
                                      (assoc components :connectors connectors)
                                      components))
        app (make-app (assoc components :collection collection))
        stop-app (fn []
                   (-> collection
                       collection/stop
                       wait)
                   (shutdown-agents))
        set-shutdown-hook (fn []
                            (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))]
    (case mode
      "chat" (let [latch (promise)]
               (set-shutdown-hook)
               (print "Syncing...")
               (flush)
               (collection/sync collection (fn [_] (deliver latch :complete)))
               (println @latch)
               (loop []
                 (print "\u001B[32mPrompt: \u001B[0m")
                 (flush)
                 (let [prompt (str/trim (read-line))]
                   (when-not (= prompt "")
                     (println "\u001B[34mAI:" (:text (app/chat app prompt)) "\u001B[0m")
                     (recur))))
               (println "\u001B[34mAI: Bye~!\u001B[0m"))
      "server" (let [server (server/start app {})]
                 (.addShutdownHook (Runtime/getRuntime) (Thread. #(do
                                                                    (server/stop server)
                                                                    (stop-app)))))
      "query" (let [latch (promise)]
                (collection/sync collection (fn [_] (deliver latch :complete)))
                (println @latch)
                (println "\u001B[34mAI:" (:text (app/chat app prompt)) "\u001B[0m")
                (collection/stop collection)
                (shutdown-agents))
      (println usage))))

(comment

  (require '[ragtacts.core :refer :all])

  ;; 1. Initially, you have one document.
  ;; $ ls -1 ~/papers
  ;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf


  ;;
  )
