(ns ragtacts.llm.llama-cpp
  (:require [clojure.string :as str]
            [ragtacts.llm.base :refer [Llm make-answer]]
            [ragtacts.logging :as log])
  (:import [de.kherud.llama InferenceParameters LlamaModel ModelParameters]
           [de.kherud.llama.args MiroStat LogFormat]))


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

(defn make-llama-cpp-llm [{:keys [model n-ctx]}]
  (let [model (LlamaModel. (doto (ModelParameters.)
                             (.setDisableLog true)
                             (.setModelFilePath model)
                             (.setNGpuLayers 43)
                             (.setNCtx n-ctx)))]
    (->LlamaCppLlm model)))