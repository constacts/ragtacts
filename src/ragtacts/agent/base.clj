(ns ragtacts.agent.base
  (:require [cheshire.core :as json]
            [ragtacts.llm.base :refer [query]]
            [ragtacts.llm.llama-cpp :refer [make-llama-cpp-llm]]
            [ragtacts.tool.base :refer [metadata run]]
            [ragtacts.tool.get-word-length :refer [make-get-word-length-tool]]))

(defprotocol Agent
  (chat [this prompt]))

(defn- find-tool-by-name [tools name]
  (first (filter #(= name (:name (metadata %))) tools)))

(defrecord AgentImpl [llm tools]
  Agent
  (chat [_ prompt]
    (let [chat-msgs [{:type :user :text prompt}]
          tool-calls (:tool-calls (query llm {:chat-msgs chat-msgs
                                              :tools tools}))
          fn-call (-> tool-calls first :function)
          {:keys [name arguments]} fn-call
          selected-tool (find-tool-by-name tools name)
          result (when selected-tool
                   (run selected-tool (try (json/parse-string arguments true)
                                           (catch Exception _
                                             arguments))))]
      (query llm {:chat-msgs (concat chat-msgs
                                     [{:type :tool-calls
                                       :tool-calls tool-calls}
                                      {:type :tool
                                       :content (json/generate-string result)
                                       :tool-call-id (-> tool-calls first :id)}])
                  :tools tools}))))

(defn make-agent [{:keys [llm tools]}]
  (->AgentImpl llm tools))

(defn -main [& _]
  (let [llm (make-llama-cpp-llm {:model {:type :hugging-face
                                         :name "QuantFactory/Meta-Llama-3-8B-Instruct-GGUF"
                                         :file "Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"
                                         :chat-template "{% set loop_messages = messages %}{% for message in loop_messages %}{% set content = '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' %}{% if loop.index0 == 0 %}{% set content = bos_token + content %}{% endif %}{{ content }}{% endfor %}{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}{% endif %}"
                                         :bos-token "<|begin_of_text|>"
                                         :eos-token "<|end_of_text|>"}
                                 :n-ctx 8192})
        ;; llm (make-open-ai-llm {:model "gpt-3.5-turbo-0125"})
        tools [(make-get-word-length-tool)]
        agent (make-agent {:llm llm
                           :tools tools})]
    (println (chat agent "How many letters in the word eudca"))))