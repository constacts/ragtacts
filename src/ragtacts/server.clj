(ns ragtacts.server
  (:require [clj-ulid :refer [ulid]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
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

(s/def ::model string?)

(s/def ::role string?)

(s/def ::content string?)

(s/def ::message (s/keys :req-un [::role ::content]))

(s/def ::messages (s/coll-of ::message))

(s/def ::chat-completions-request (s/keys :req-un [::messages ::model]))

(defn ->msg [{:keys [role content]}]
  (case role
    "user" {:user content}
    "system" {:system content}
    "assistant" {:ai content}
    (throw (ex-info (str role "is unknown role") {:role role}))))

(defn- format-response [result]
  {:id (ulid)
   :object "chat.completion"
   :created (System/currentTimeMillis)
   :model "ragtacts"
   :system_fingerprint "ragtacts"
   :choices [{:finish_reason "stop"
              :index 0
              :logprobs nil
              :message [{:role "assistant"
                         :content (:ai result)}]}]
   :usage {:completion_tokens 0
           :prompt_tokens 0
           :total_tokens 0}})


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
      ["/v1"
       ["/chat/completions"
        {:post {:summary "chat"
                :parameters {:body ::chat-completions-request}
                :handler (fn [{:keys [parameters]}]
                           (let [messages (-> parameters :body :messages)
                                 query (-> messages last :content)
                                 result (ask (conj
                                              (vec (map ->msg (drop-last messages)))
                                              {:user (prompt rag-prompt
                                                             {:context (str/join "\n" (search db query))
                                                              :question query})}))]
                             {:status 200
                              :body (format-response (-> result last))}))}}]]]]

    {:data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         exception/exception-middleware
                         muuntaja/format-request-middleware
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