(ns ragtacts.embedding.open-ai
  (:require [ragtacts.embedding.base :refer [embed]]
            [wkok.openai-clojure.api :as openai]))

(defn open-ai-embedding
  ([]
   (open-ai-embedding {:model "text-embedding-3-small"}))
  ([{:keys [model]}]
   {:type :open-ai
    :model model}))

(defmethod embed :open-ai [{:keys [model]} texts]
  (try
    (let [{:keys [data]} (openai/create-embedding {:model model
                                                   :input texts})]
      (map :embedding data)
      #_(mapv (fn [{:keys [embedding]}]
                (make-embedding doc-id
                                text
                                (map float embedding)
                                metadata))
              data))
    (catch Exception e
      (.printStackTrace e))))