(ns ragtacts.llm.open-ai
  (:require [ragtacts.llm.base :refer [Llm]]
            [ragtacts.types :refer [make-answer]]
            [wkok.openai-clojure.api :as openai]))

(defrecord OpenAILlm [model]
  Llm
  (query [_ prompt]
    (let [response (openai/create-chat-completion {:model model
                                                   :messages [{:role "user"
                                                               :content prompt}]}
                                                  {:trace (fn [request response])})]
      (-> response
          :choices
          first
          :message
          :content
          make-answer))))

(defn make-open-ai-llm [opts]
  (map->OpenAILlm opts))