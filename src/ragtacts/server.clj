(ns ragtacts.server
  (:require [muuntaja.core :as m]
            [ragtacts.app :as app]
            [ragtacts.collection :as collection]
            [ragtacts.logging :as log]
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

(defn handler [app]
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
                          (let [prompt (-> parameters :body :prompt)]
                            {:status 200
                             :body {:text (:text (app/chat app prompt))}}))}}]]]

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

(defn start [app opts]
  (try
    (let [opts (merge {:port 3000 :host "0.0.0.0"} opts)
          server  (run-undertow (handler app) (dissoc opts :handler))]
      (collection/sync (:collection app) identity)
      (log/info "Server started on port" (:port opts))
      server)
    (catch Throwable t
      (log/error t (str "Server failed to start on port: " (:port opts))))))

(defn stop [server]
  (.stop server)
  (log/info "Server stopped"))