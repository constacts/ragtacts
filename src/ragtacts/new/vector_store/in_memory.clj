(ns ragtacts.new.vector-store.in-memory
  (:require [clojure.walk :refer [stringify-keys]]
            [ragtacts.new.embedding.base :refer [embed text->doc]]
            [ragtacts.new.vector-store.base :refer [save search]])
  (:import [dev.langchain4j.data.document Metadata]
           [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.data.segment TextSegment]
           [dev.langchain4j.store.embedding EmbeddingSearchRequest]
           [dev.langchain4j.store.embedding.inmemory InMemoryEmbeddingStore]
           [java.util HashMap]))

(defn- doc->text-segment [{:keys [id
                                  text
                                  metadata]}]
  (let [metadata (if id
                   (assoc metadata :id id)
                   metadata)]
    (TextSegment. text (if metadata
                         (Metadata/from (HashMap. (stringify-keys metadata)))
                         (Metadata.)))))

(defn in-memory []
  {:type :in-memory
   :store (InMemoryEmbeddingStore.)})

(defmethod save :in-memory [{:keys [embedding db]} texts-or-docs]
  (let [docs (map text->doc texts-or-docs)
        embeddings (embed embedding (map :text docs))
        ^InMemoryEmbeddingStore store (:store db)]
    (.addAll store
             (map (fn [vectors]
                    (Embedding. (float-array (map float vectors))))
                  embeddings)
             (map doc->text-segment docs))))

(defmethod search :in-memory
  ([db query]
   (search db query {}))
  ([{:keys [embedding db]} query {:keys [top-k]}]
   (let [embeddings (embed embedding [query])
         embedding (Embedding. (float-array (map float (first embeddings))))
         ^InMemoryEmbeddingStore store (:store db)
         result (.search store
                         (EmbeddingSearchRequest.
                          embedding
                          (int (or top-k 5))
                          0.0
                          nil))]
     (map #(.text (.embedded %)) (.matches result)))))