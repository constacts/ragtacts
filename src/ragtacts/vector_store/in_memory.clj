(ns ragtacts.vector-store.in-memory
  (:require [clojure.walk :refer [stringify-keys]]
            [ragtacts.embedding.base :refer [embed text->doc]]
            [ragtacts.vector-store.base :refer [add search]]
            [ragtacts.splitter.base :refer [split]])
  (:import [dev.langchain4j.data.document Metadata]
           [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.data.segment TextSegment]
           [dev.langchain4j.store.embedding
            EmbeddingSearchRequest
            EmbeddingSearchResult
            EmbeddingMatch]
           [dev.langchain4j.store.embedding.inmemory InMemoryEmbeddingStore]
           [dev.langchain4j.store.embedding.filter MetadataFilterBuilder]
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

(defn in-memory-vector-store
  "Return an in-memory vector store."
  []
  {:type :in-memory
   :store (InMemoryEmbeddingStore.)})

(defmethod add :in-memory [{:keys [embedding splitter db]} texts-or-docs]
  (let [docs (map text->doc texts-or-docs)
        chunked-docs (split splitter docs)
        embeddings (embed embedding (map :text chunked-docs))
        ^InMemoryEmbeddingStore store (:store db)]
    (.addAll store
             (map (fn [vectors]
                    (Embedding. (float-array (map float vectors))))
                  embeddings)
             (map doc->text-segment chunked-docs))))

(defn- ->filter [metadata]
  (when metadata
    (reduce
     (fn [filter [k v]]
       (if filter
         (.and filter (.isEqualTo (MetadataFilterBuilder. (name k)) v))
         (.isEqualTo (MetadataFilterBuilder. (name k)) v)))
     nil
     metadata)))

(defmethod search :in-memory
  ([db query]
   (search db query {}))
  ([{:keys [embedding db]} query {:keys [top-k metadata raw?]}]
   (let [embeddings (embed embedding [query])
         embedding (Embedding. (float-array (map float (first embeddings))))
         ^InMemoryEmbeddingStore store (:store db)
         filter (->filter metadata)
         ^EmbeddingSearchResult result (.search store
                                                (EmbeddingSearchRequest.
                                                 embedding
                                                 (int (or top-k 5))
                                                 0.0
                                                 filter))]
     (map
      (fn [^EmbeddingMatch match]
        (if raw?
          {:text (.text (.embedded match))
           :vector (map float (.vector (.embedding match)))
           :metadata (into {} (.asMap (.metadata (.embedded match))))}
          (.text (.embedded match))))
      (.matches result)))))

(comment
  (->filter {:a 1})
  ;;
  )