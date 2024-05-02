(ns ragtacts.llm.llama-cpp
  (:require [clojure.java.io :as io]
            [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.util :refer [download-with-progress]])
  (:import [de.kherud.llama InferenceParameters LlamaModel ModelParameters]
           [de.kherud.llama.args MiroStat]
           [com.hubspot.jinjava Jinjava]))

(defn- ->prompt [{:keys [chat-msgs chat-template bos-token eos-token]}]
  (let [jinja (Jinjava.)]
    (.render jinja chat-template {"bos_token" bos-token
                                  "eos_token" eos-token
                                  "add_generation_prompt" true
                                  "messages" (map (fn [{:keys [type text]}]
                                                    {"role" (case type
                                                              :user "user"
                                                              :system "system"
                                                              :ai "assistant")
                                                     "content" text}) chat-msgs)})))

(defrecord LlamaCppLlm [model chat-template bos-token eos-token]
  Llm
  (query [_ chat-msgs]
    (let [prompt (->prompt {:chat-msgs chat-msgs
                            :chat-template chat-template
                            :bos-token bos-token
                            :eos-token eos-token})
          infer-params (doto (InferenceParameters. prompt)
                         (.setTemperature 0.0)
                         (.setPenalizeNl true)
                         (.setMiroStat MiroStat/V2)
                         (.setStopStrings (into-array String [eos-token])))]
      (make-answer (.complete model infer-params)))))

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