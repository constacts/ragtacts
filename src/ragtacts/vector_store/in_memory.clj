(ns ragtacts.vector-store.in-memory
  (:require [clojure.walk :refer [keywordize-keys stringify-keys]]
            [ragtacts.embedding.base :refer [embed text->doc]]
            [ragtacts.splitter.base :refer [split]]
            [ragtacts.vector-store.base :refer [add search delete]]
            [clojure.set :refer [subset?]]
            [clojure.java.io :as io])
  (:import [dev.langchain4j.data.document Metadata]
           [dev.langchain4j.data.embedding Embedding]
           [dev.langchain4j.data.segment TextSegment]
           [dev.langchain4j.store.embedding EmbeddingMatch EmbeddingSearchRequest EmbeddingSearchResult]
           [dev.langchain4j.store.embedding.filter MetadataFilterBuilder]
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
  ([{:keys [embedding db]} query {:keys [top-k metadata raw? metadata-out-fields]}]
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
           :metadata (keywordize-keys
                      (select-keys
                       (into {} (.asMap (.metadata (.embedded match))))
                       metadata-out-fields))}
          (.text (.embedded match))))
      (.matches result)))))

(defn- get-private [obj field-name]
  (let [field (.getDeclaredField (class obj) field-name)]
    (.setAccessible field true)
    (.get field obj)))

(defmethod delete :in-memory [{:keys [db]} metadata]
  (let [^InMemoryEmbeddingStore store (:store db)
        entries (get-private store "entries")]
    (doseq [entry entries]
      (let [embedded (get-private entry "embedded")
            entry-metadata (keywordize-keys (into {} (.asMap (.metadata embedded))))]
        (when (subset? (set metadata) (set entry-metadata))
          (.remove entries entry))))))

(defn- create-parent-dir [filename]
  (-> filename
      io/file
      .getParent
      io/file
      .mkdir))

(defn save-to-file
  "Save an in-memory vector store to a file."
  [{:keys [db]} filename]
  (let [^InMemoryEmbeddingStore store (:store db)]
    (create-parent-dir filename)
    (.serializeToFile store ^String filename)))

(defn load-from-file
  "Load an in-memory vector store from a file."
  [filename]
  {:type :in-memory
   :store (InMemoryEmbeddingStore/fromFile ^String filename)})

(comment


  ;;
  )