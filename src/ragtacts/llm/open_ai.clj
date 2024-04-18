(ns ragtacts.llm.open-ai
  (:require [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.logging :as log]
            [wkok.openai-clojure.api :as openai]))

(defn- ->message [{:keys [type text]}]
  {:role (case type
           :user "user"
           :system "system"
           :ai "assistant")
   :content text})

(defn- ->answer [response]
  (-> response
      :choices
      first
      :message
      :content
      make-answer))
(defrecord OpenAILlm [model]
  Llm
  (query [_ chat-msgs]
    (let [response (openai/create-chat-completion
                    {:model model
                     :messages (map ->message chat-msgs)}
                    {:trace (fn [request response]
                              (log/debug request))})]

      (->answer response))))

(defn make-open-ai-llm [opts]
  (map->OpenAILlm opts))