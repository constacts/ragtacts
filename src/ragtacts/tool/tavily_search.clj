(ns ragtacts.tool.tavily-search
  (:require [cheshire.core :as json]
            [hato.client :as http]
            [ragtacts.tool.base :refer [run Tool]]))

(defrecord TavilySearchTool []
  Tool
  (run [_ {:keys [query]}]
    (if-let [api-key (System/getenv "TAVILY_API_KEY")]
      (let [params {:api_key api-key
                    :query query
                    :max_results 5}
            {:keys [status body]} (http/post "https://api.tavily.com/search"
                                             {:body (json/generate-string params)
                                              :content-type :json})]
        (if (= status 200)
          (:results (json/parse-string body true))
          (throw (ex-info "API request failed" {:status status :body body}))))
      (throw (ex-info "API key not found" {}))))

  (metadata [_]
    {:name "tavily_search_results_json"
     :description "A search engine optimized for comprehensive, accurate, and trusted results.\n
                   Useful for when you need to answer questions about current events.\n
                   Input should be a search query."
     :parameters {:type "object"
                  :properties
                  {:query {:type "string"
                           :description "search query to look up"}}
                  :required ["query"]}}))

(defn make-tavily-search-tool []
  (->TavilySearchTool))

(defn -main [& _]
  (let [tool (make-tavily-search-tool)]
    (println (run tool {:query "what is the weather in SF"}))))