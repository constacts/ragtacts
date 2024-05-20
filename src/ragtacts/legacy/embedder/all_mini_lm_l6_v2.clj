(ns ragtacts.legacy.embedder.all-mini-lm-l6-v2
  (:require [ragtacts.legacy.embedder.base :refer [embed Embedder make-embedding]])
  (:import [dev.langchain4j.model.embedding AllMiniLmL6V2EmbeddingModel]))

(defrecord AllMiniLmL6V2Embedder []
  Embedder
  (embed [_ chunks]
    (let [model (AllMiniLmL6V2EmbeddingModel.)
          texts (map :text chunks)
          data (doall (map #(into [] (-> (.embed model %)
                                         .content
                                         .vector)) texts))]
      (mapv (fn [embedding {:keys [doc-id metadata text]}]
              (make-embedding doc-id
                              text
                              (map float embedding)
                              metadata))
            data
            chunks))))

(defn make-all-mini-lm-l6-v2-embedder [{:keys [model]}]
  (->AllMiniLmL6V2Embedder))

(comment
  ;;
  )