(ns ragtacts.app
  (:require [clj-ulid :refer [ulid]]
            [clojure.string :as str]
            [ragtacts.collection :as collection]
            [ragtacts.llm.base :as llm]
            [ragtacts.logging :as log]
            [ragtacts.memory.base :as memory :refer [make-chat-msg]]
            [ragtacts.prompt-template.base :as prompt-template]))

(defn- conversational-msgs [{:keys [chat-history
                                    system-prompt
                                    user-prompt]}]
  (concat
   [(make-chat-msg {:type :system :text system-prompt})]
   chat-history
   [(make-chat-msg {:type :user :text user-prompt})]))

(defprotocol App
  (chat [this propmt]))

(defrecord AppImpl [id name collection llm memory prompt-template]
  App
  (chat [_ prompt]
    (let [chunks (collection/search collection prompt nil)
          _ (binding [*print-length* 5]
              (log/debug (str "Search result chunks count:" (count chunks)) chunks))
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

(defn make-app [{:keys [id] :as opts}]
  (let [id (or id (ulid))]
    (map->AppImpl (merge opts {:id id}))))

(comment

  (make-app nil)
  ;;
  )