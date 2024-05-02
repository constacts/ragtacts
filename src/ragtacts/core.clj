(ns ragtacts.core
  (:refer-clojure :exclude [sync chunk])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtacts.app :as app :refer [make-app]]
            [ragtacts.collection :as collection :refer [make-collection]]
            [ragtacts.connector.folder :refer [make-folder-connector]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.all-mini-lm-l6-v2 :refer [make-all-mini-lm-l6-v2-embedder]]
            [ragtacts.llm.llama-cpp :refer [make-llama-cpp-llm]]
            [ragtacts.memory.window :refer [make-window-chat-memory]]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]))

(defn default-splitter []
  (make-recursive {:size 1000 :overlap 20}))

(defn default-embedder []
  (make-all-mini-lm-l6-v2-embedder {}))

(defn default-vector-store []
  (make-in-memory-vector-store nil))

(defn default-memory []
  (make-window-chat-memory {:size 10}))

(defn default-prompt-template []
  (make-default-prompt-template nil))

(defn default-llm []
  (make-llama-cpp-llm {:model {:type :hugging-face
                               :name "QuantFactory/Meta-Llama-3-8B-Instruct-GGUF"
                               :file "Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"
                               :chat-template "{% set loop_messages = messages %}{% for message in loop_messages %}{% set content = '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' %}{% if loop.index0 == 0 %}{% set content = bos_token + content %}{% endif %}{{ content }}{% endfor %}{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}{% endif %}"
                               :bos-token "<|begin_of_text|>"
                               :eos-token "<|end_of_text|>"}
                       :n-ctx 8192}))

(defn- http? [data-sources]
  (some? (or (re-find #"^https://" data-sources)
             (re-find #"^http://" data-sources))))

(defn- path? [data-sources]
  (let [path (str/replace data-sources #"~" (System/getProperty "user.home"))]
    (try
      (.exists (io/file path))
      (catch Exception _
        false))))

(defn- infer-connector [data-sources]
  (cond
    (http? data-sources) (make-web-page-connector {:url data-sources})
    (path? data-sources) (make-folder-connector {:path data-sources})
    :else (throw (ex-info "Unknown data source" {:data-sources data-sources}))))

(defn default-components []
  {:splitter (default-splitter)
   :embedder (default-embedder)
   :vector-store (default-vector-store)
   :memory (default-memory)
   :prompt-template (default-prompt-template)
   :llm (default-llm)})

(defn app
  ([data-sources]
   (app data-sources {}))
  ([data-sources components]
   (let [defaults (default-components)
         components (merge defaults components)
         connectors (map infer-connector data-sources)]
     (make-app (assoc (merge defaults components)
                      :collection (make-collection (if (seq connectors)
                                                     (assoc components :connectors connectors)
                                                     components)))))))

(defn sync [app callback]
  (collection/sync (:collection app)
                   (fn [event]
                     (callback app event)))
  app)

(defn stop [app]
  (-> (:collection app)
      collection/stop)
  (shutdown-agents))

(def chat app/chat)

(comment

  (require '[ragtacts.core :refer [app sync chat]])
  ;; 1. Initially, you have one document.
  ;; $ ls -1 ~/papers
  ;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf

  (-> (app ["https://aws.amazon.com/what-is/retrieval-augmented-generation/"
            "~/papers"])
      (sync
       ;; Callback that is called when a sync event occurs.
       (fn [app event]
         (when (= :complete (:type event))
           (println (chat app "Tell me about RAG technology."))
           ;; 2. If you get an answer and add antoher document to ~/papers,
           ;;    it will sync up and give you a new answer.
           ;; $ ls -1 ~/papers
           ;; RAPTOR.pdf
           ;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf
           ))))
  ;;
  )
