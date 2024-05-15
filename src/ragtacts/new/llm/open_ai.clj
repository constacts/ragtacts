(ns ragtacts.new.llm.open-ai
  (:require [ragtacts.new.llm.base :refer [ask]]
            [wkok.openai-clojure.api :as openai]))

(defn- question->msgs [q]
  (if (string? q)
    [{:user q}]
    q))

(defn- ->open-ai-message [{:keys [system user ai] :as msg}]
  (cond system {:role "system" :content system}
        user {:role "user" :content user}
        ai {:role "assistant" :content ai}
        :else (throw (ex-info (str msg "is unknown message type") {:msg msg}))))

(defn- parse-output [{:keys [choices]}]
  (-> choices
      first
      :message
      :content))

(defmethod ask :open-ai [q {:keys [model]}]
  (parse-output
   (openai/create-chat-completion {:model (or model "gpt-4o")
                                   :messages (map ->open-ai-message (question->msgs q))}
                                  {:trace (fn [request response]
                                            ;; (println "Request:")
                                            ;; (println (:body request))
                                            ;; (println)
                                            ;; (println "Response:")
                                            ;; (println (:body response))
                                            ;; (log/debug request)
                                            )})))