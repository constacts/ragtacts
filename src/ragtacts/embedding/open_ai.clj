(ns ragtacts.embedding.open-ai
  (:require [ragtacts.embedding.base :refer [embed]]
            [wkok.openai-clojure.api :as openai]))

(defn open-ai-embedding
  "Return the OpenAI embedding.
   
   Args:
   - A map with the following keys:
     - `:model`: A string with the model name. Default is `text-embedding-3-small`."
  ([]
   (open-ai-embedding {:model "text-embedding-3-small"}))
  ([{:keys [model]}]
   {:type :open-ai
    :model model}))

(defmethod embed :open-ai
  ([embedder texts]
   (embed embedder texts {}))
  ([{:keys [model]} texts {:keys [raw?] :as opts}]
   (try
     (let [{:keys [data]} (openai/create-embedding {:model model
                                                    :input texts}
                                                   opts)]
       (if raw?
         data
         (map :embedding data)))
     (catch Exception e
       (.printStackTrace e)))))

(comment
  (embed (open-ai-embedding) ["Hello, world!"] {:api-endpoint "http://localhost:3030"})

  ;;
  )