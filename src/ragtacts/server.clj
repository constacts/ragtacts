(ns ragtacts.server
  (:require [clojure.string :as str]
            [muuntaja.core :as m]
            [ragtacts.core :refer [ask prompt search]]
            [ragtacts.logging :as log]
            [ragtacts.prompt.langchain :as langchain]
            [reitit.coercion.spec]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.undertow :refer [run-undertow]]))

(defn app [db rag-prompt]
  (ring/ring-handler
   (ring/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "ragtacts API"}
                       :basePath "/"}
             :handler (swagger/create-swagger-handler)}}]

     ["/api"
      {:swagger {}}

      ["/chat"
       {:post {:summary "chat"
               :parameters {:body {:prompt string?}}
               :responses {200 {:body {:text string?}}}
               :handler (fn [{:keys [parameters]}]
                          (let [query (-> parameters :body :prompt)
                                result (ask (prompt rag-prompt
                                                    {:context (str/join "\n" (search db query))
                                                     :question query}))]
                            {:status 200
                             :body {:text (-> result last :ai)}}))}}]]]

    {:data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         exception/exception-middleware
                         muuntaja/format-request-middleware
                         coercion/coerce-response-middleware
                         coercion/coerce-request-middleware
                         multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/"})
    (ring/create-default-handler))))

(defn start [{:keys [db] :as opts}]
  (try
    (let [opts (merge {:port 3000 :host "0.0.0.0"} opts)
          rag-prompt (langchain/hub "rlm/rag-prompt")
          server (run-undertow (app db rag-prompt) (dissoc opts :handler))]
      (log/info "Server started on port" (:port opts))
      server)
    (catch Throwable t
      (log/error t (str "Server failed to start on port: " (:port opts))))))

(defn stop [server]
  (.stop server)
  (log/info "Server stopped"))