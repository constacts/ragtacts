(ns ragtacts.llm.llama-cpp
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.util :refer [download-with-progress]])
  (:import [de.kherud.llama InferenceParameters LlamaModel ModelParameters]
           [de.kherud.llama.args MiroStat]))


(defn- ->message [{:keys [type text]}]
  (str (case type
         :user "User: "
         :system "System:"
         :ai "Assistant: ")
       text))

(defn- parse-output [text]
  (apply str (second (split-at (+ (str/index-of text "Assistant: ") (count "Assistant: ")) text))))

(defrecord LlamaCppLlm [model]
  Llm
  (query [_ chat-msgs]
    (let [prompt (str/join "\n" (map ->message chat-msgs))
          infer-params (doto (InferenceParameters. prompt)
                         (.setTemperature 0.0)
                         (.setPenalizeNl true)
                         (.setMiroStat MiroStat/V2)
                         (.setStopStrings (into-array String ["User:"])))]
      (make-answer (parse-output (.complete model infer-params))))))

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
  (let [url (str "https://huggingface.co/" name "/resolve/main/" file "?download=true")]
    (println "Downloading model from HuggingFace" name)
    (.mkdirs (io/file (str "models/" name)))
    (download-with-progress url path)))

(defn make-llama-cpp-llm [{:keys [model] :as params}]
  (let [path (get-model-path model)
        llama-cpp-model (if (.exists (io/as-file path))
                          (load-model (assoc params :path path))
                          (if (= :hugging-face (:type model))
                            (do
                              (download-hugging-face-model model path)
                              (load-model (assoc params :path path)))
                            (throw (ex-info "Model not found" {:model model}))))]
    (->LlamaCppLlm llama-cpp-model)))