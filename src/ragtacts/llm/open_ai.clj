(ns ragtacts.llm.open-ai
  (:require [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.logging :as log]
            [ragtacts.tool.base :refer [metadata]]
            [wkok.openai-clojure.api :as openai]))

(defn- ->message [{:keys [type text content tool-call-id tool-calls]}]
  (case type
    :user {:role "user" :content text}
    :system {:role "system" :content text}
    :ai {:role "assistant" :content text}
    :tool {:role "tool"
           :content content
           :tool_call_id tool-call-id}
    :tool-calls {:role "assistant"
                 :tool_calls tool-calls}
    nil))

(defn tool->fn [tool]
  {:type "function"
   :function (metadata tool)})

(defn- ->answer [response]
  (let [result (-> response
                   :choices
                   first
                   :message)]
    (make-answer {:text (:content result)
                  :tool-calls (:tool_calls result)})))
(defrecord OpenAILlm [model]
  Llm
  (query [_ {:keys [chat-msgs tools]}]
    (let [params {:model model
                  :messages (map ->message chat-msgs)}
          response (openai/create-chat-completion
                    (if tools
                      (assoc params :tools (map tool->fn tools))
                      params)
                    {:trace (fn [request response]
                              ;; (println "Request:")
                              ;; (println (:body request))
                              ;; (println)
                              ;; (println "Response:")
                              ;; (println (:body response))
                              (log/debug request))})]

      (->answer response))))

(defn make-open-ai-llm [opts]
  (map->OpenAILlm opts))