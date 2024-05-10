(ns ragtacts.tool.collection-search
  (:require [ragtacts.collection :as collection :refer [make-collection]]
            [ragtacts.connector.web-page :refer [make-web-page-connector]]
            [ragtacts.embedder.open-ai :refer [make-open-ai-embedder]]
            [ragtacts.splitter.recursive :refer [make-recursive]]
            [ragtacts.tool.base :refer [run Tool]]
            [ragtacts.vector-store.in-memory :refer [make-in-memory-vector-store]]))

(defrecord CollectionSearchTool [collection name description]
  Tool
  (run [_ {:keys [query]}]
    (collection/search collection query {}))

  (metadata [_]
    {:name name
     :description description
     :parameters {:type "object"
                  :properties
                  {:query {:type "string"
                           :description "query to look up in retriever"}}
                  :required ["query"]}}))

(defn make-collection-search-tool [args]
  (map->CollectionSearchTool args))

(defn -main [& _]
  (let [coll (make-collection {:id "test1"
                               :name "langsmith-coll"
                               :connectors [(make-web-page-connector {:url "https://docs.smith.langchain.com/overview"})]
                               :splitter (make-recursive {:size 1000 :overlap 20})
                               :embedder (make-open-ai-embedder {:model "text-embedding-3-small"})
                               :vector-store (make-in-memory-vector-store nil)})
        tool (make-collection-search-tool {:collection coll
                                           :name "langsmith_search"
                                           :desciption "Search for information about LangSmith. For any questions about LangSmith, you must use this tool!"})]
    (collection/sync coll
                     (fn [result]
                       (println (-> (run tool {:query "how to upload a dataset"})
                                    first
                                    :text))))))

