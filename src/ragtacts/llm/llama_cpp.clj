(ns ragtacts.llm.llama-cpp
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.tool.base :refer [metadata]]
            [ragtacts.util :refer [download-with-progress]])
  (:import [com.hubspot.jinjava Jinjava]
           [de.kherud.llama InferenceParameters LlamaModel ModelParameters]
           [de.kherud.llama.args MiroStat]))

(defn- ->prompt [{:keys [chat-msgs chat-template bos-token eos-token]}]
  (let [jinja (Jinjava.)]
    (.render jinja chat-template
             {"bos_token" bos-token
              "eos_token" eos-token
              "add_generation_prompt" true
              "messages"
              (map (fn [{:keys [type text content tool-calls]}]
                     (case type
                       :user {"role" "user" "content" text}
                       :system {"role" "system" "content" text}
                       :ai {"role" "assistant" "content" text}
                       :fn-metadata {"role" "function_metadata" "content" text}
                       :tool-calls {"role" "function_call"
                                    "content" (try (json/generate-string tool-calls)
                                                   (catch Exception _
                                                     (str tool-calls)))}
                       :tool {"role" "function_response" "content" content}
                       nil)) chat-msgs)})))
(defn- ->fn-call [tool]
  (json/generate-string (metadata tool)))

(def metadata-for-setup
  [{:type "function"
    :function {:name "get_stock_price"
               :description "Get the stock price of an array of stocks"
               :parameters {:type "object"
                            :properties {:names {:type "array"
                                                 :items {:type "string"}
                                                 :description "An array of stocks"}}
                            :required ["names"]}}}
   {:type "function"
    :function {:name "get_big_stocks"
               :description "Get the names of the largest N stocks by market cap"
               :parameters {:type "object"
                            :properties {:number {:type "integer"
                                                  :description "The number of largest stocks to get the names of, e.g. 25"}
                                         :region {:type "string"
                                                  :description "The region to consider, can be \"US\" or \"World\"."}}
                            :required ["number"]}}}])

(defn- tools->msgs [tools]
  (when tools
    [{:type :fn-metadata :text (json/generate-string metadata-for-setup)}
     {:type :user :text "What is the current weather in London?"}
     {:type :tool-calls
      :tool-calls [{:name "get_current_weather"
                    :arguments {:city "London"}}]}
     {:type :tool
      :content "{\n    \"temperature\": \"15 C\",\n    \"condition\": \"Cloudy\"\n}"
      :tool_call_id nil}
     {:type :ai :text "The current weather in London is Cloudy with a temperature of 15 Celsius"}
     {:type :fn-metadata
      :text (str "[" (str/join ", " (map ->fn-call tools)) "]")}]))

(defrecord LlamaCppLlm [model chat-template bos-token eos-token]
  Llm
  (query [_ {:keys [chat-msgs tools]}]
    (let [chat-msgs (concat (drop-last chat-msgs)
                            (tools->msgs tools)
                            [(last chat-msgs)])
          prompt (->prompt {:chat-msgs chat-msgs
                            :chat-template chat-template
                            :bos-token bos-token
                            :eos-token eos-token})
          infer-params (doto (InferenceParameters. prompt)
                         (.setTemperature 0.0)
                         (.setPenalizeNl true)
                         (.setMiroStat MiroStat/V2)
                         (.setStopStrings (into-array String [eos-token])))
          result (.complete model infer-params)
          fn-call (try
                    (json/parse-string result true)
                    (catch Exception _
                      nil))]
      (make-answer {:text (when-not fn-call
                            result)
                    :tool-calls (when fn-call
                                  [{:function fn-call}])}))))

(defn- get-model-path [{:keys [type name file]}]
  (if (= :hugging-face type)
    (str "models/" name "/" file)
    file))

(defn- load-model [{:keys [path n-ctx]}]
  (LlamaModel. (doto (ModelParameters.)
                 (.setDisableLog true)
                 (.setModelFilePath path)
                 (.setNGpuLayers 43)
                 (.setNCtx n-ctx))))

(defn- download-hugging-face-model [{:keys [name file]} path]
  (let [model-url (str "https://huggingface.co/" name "/resolve/main/" file "?download=true")]
    (println "Downloading model from HuggingFace" name)
    (.mkdirs (io/file (str "models/" name)))
    (download-with-progress model-url path)))

(defn make-llama-cpp-llm [{:keys [model] :as params}]
  (let [path (get-model-path model)
        llama-cpp-model (if (.exists (io/as-file path))
                          (load-model (assoc params :path path))
                          (if (= :hugging-face (:type model))
                            (do
                              (download-hugging-face-model model path)
                              (load-model (assoc params :path path)))
                            (throw (ex-info "Model not found" {:model model}))))]
    (map->LlamaCppLlm {:model llama-cpp-model
                       :chat-template (:chat-template model)
                       :bos-token (:bos-token model)
                       :eos-token (:eos-token model)})))
