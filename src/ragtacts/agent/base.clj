(ns ragtacts.agent.base
  (:require [cheshire.core :as json]
            [ragtacts.collection :as collection :refer [make-collection]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.llm.base :refer [query]]
            [ragtacts.llm.open-ai :refer [make-open-ai-llm]]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.tool.base :refer [metadata run]]
            [ragtacts.tool.collection-search :refer [make-collection-search-tool]]
            [ragtacts.tool.tavily-search :refer [make-tavily-search-tool]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]))

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
  (let [;; WIP
        ;; llm (make-llama-cpp-llm {:model {:type :hugging-face
        ;;                                  :name "QuantFactory/Meta-Llama-3-8B-Instruct-GGUF"
        ;;                                  :file "Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"
        ;;                                  :chat-template "{% set loop_messages = messages %}{% for message in loop_messages %}{% set content = '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' %}{% if loop.index0 == 0 %}{% set content = bos_token + content %}{% endif %}{{ content }}{% endfor %}{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}{% endif %}"
        ;;                                  :bos-token "<|begin_of_text|>"
        ;;                                  :eos-token "<|end_of_text|>"}
        ;;                          :n-ctx 8192})
        llm (make-open-ai-llm {:model "gpt-3.5-turbo-0125"})
        coll (make-collection {:name "langsmith-coll"
                               :connectors [(make-web-page-connector {:url "https://docs.smith.langchain.com/overview"})]
                               :splitter (make-recursive {:size 1000 :overlap 20})
                               :embedder (make-open-ai-embedder {:model "text-embedding-3-small"})
                               :vector-store (make-in-memory-vector-store nil)})
        collection-tool (make-collection-search-tool {:collection coll
                                                      :name "langsmith_search"
                                                      :description "Search for information about LangSmith. For any questions about LangSmith, you must use this tool!"})
        search-tool (make-tavily-search-tool)
        tools [search-tool collection-tool]
        agent (make-agent {:llm llm
                           :tools tools})]
    (collection/sync coll
                     (fn [result]
                       (println "-- collection tool")
                       (println (chat agent "how can langsmith help with testing?"))
                       (println)
                       (println "-- search tool")
                       (println (chat agent "whats the weather in sf?"))))))


