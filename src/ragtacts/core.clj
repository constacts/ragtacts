(ns ragtacts.core
  (:refer-clojure :exclude [sync chunk])
  (:require [clojure.java.io :as io]
            [ragtacts.app :as app :refer [make-app]]
            [ragtacts.collection :as collection :refer [make-collection]]
            [ragtacts.connector.folder :refer [make-folder-connector]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.memory.window :refer [make-window-chat-memory]]
            [ragtacts.prompt-template.default :refer [make-default-prompt-template]]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]))

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

(defn app
  ([data-sources]
   (app data-sources {}))
  ([data-sources components]
   (let [components (merge default-components components)
         connectors (map infer-connector data-sources)]
     (make-app (assoc (merge default-components components)
                      :collection (make-collection (if (seq connectors)
                                                     (assoc components :connectors connectors)
                                                     components)))))))

(defn sync [app callback]
  (collection/sync (:collection app)
                   (fn [event]
                     (callback app event)))
  app)

(defn stop [app]
  (collection/stop (:collection app)))

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
