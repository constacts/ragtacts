(ns ragtacts.new.llm.open-ai
  (:require [cheshire.core :as json]
            [ragtacts.new.llm.base :refer [ask]]
            [wkok.openai-clojure.api :as openai]))


(defn- question->msgs [q]
  (cond
    (string? q) [{:user q}]
    (vector? q) (map #(if (string? %) {:user %} %) q)
    :else (throw (ex-info (str q "is unknown question type") {:q q}))))

(defn- ->open-ai-message [{:keys [system user ai tool-calls tool tool-call-id] :as msg}]
  (cond system {:role "system" :content system}
        user {:role "user" :content user}
        ai {:role "assistant" :content ai}
        tool-calls {:role "assistant" :tool_calls tool-calls}
        tool {:role "tool" :content tool :tool_call_id tool-call-id}
        :else (throw (ex-info (str msg "is unknown message type") {:msg msg}))))

(defn- tool->function [tool-var]
  (let [fn-meta (meta tool-var)
        args (into {}
                   (map
                    (fn [arg]
                      (let [arg-meta (meta arg)]
                        [(keyword arg) {:type (:type arg-meta)
                                        :description (:desc arg-meta)}]))
                    (first (:arglists (meta tool-var)))))]
    {:type "function"
     :function {:name (name (:name fn-meta))
                :description (:desc fn-meta)
                :parameters {:type "object"
                             :properties args
                             :required (map name (first (:arglists fn-meta)))}}}))

(defn- select-tool-by-name [tools function]
  (first (filter #(= (:name function) (name (:name (meta %)))) tools)))

(defn- apply-fn [tool function]
  (let [args (first (:arglists (meta tool)))]
    (apply tool (map #(get (:arguments function) (keyword %)) args))))

(defn- chat-completion [{:keys [model msgs tools]}]
  (let [params {:model (or model "gpt-4o")
                :messages (map ->open-ai-message msgs)}]
    (-> (openai/create-chat-completion (if (seq tools)
                                         (assoc params :tools (map tool->function tools))
                                         params)
                                       {:trace (fn [request response]
                                                 (println "Request:")
                                                 (println (:body request))
                                                 (println)
                                                 (println "Response:")
                                                 (println (:body response))
                                                             ;; (log/debug request)
                                                 )})
        :choices
        first
        :message)))

(defmethod ask :open-ai [q {:keys [model tools]}]
  (let [msgs (question->msgs q)
        result (chat-completion {:model model
                                 :msgs msgs
                                 :tools tools})]
    (if (seq tools)
      (let [{:keys [function]} (update-in (-> result :tool_calls first)
                                          [:function :arguments]
                                          #(json/parse-string % true))
            tool (select-tool-by-name tools function)]
        (when tool
          (conj (vec msgs)
                {:ai (:content
                      (chat-completion
                       {:model model
                        :msgs (concat msgs
                                      [{:tool-calls (-> result :tool_calls)}
                                       {:tool (json/generate-string (apply-fn tool function))
                                        :tool-call-id (-> result :tool_calls first :id)}]
                                      [(last msgs)])
                        :tools tools}))})))
      (conj (vec msgs) {:ai (:content result)}))))

(comment

  ;;
  )

