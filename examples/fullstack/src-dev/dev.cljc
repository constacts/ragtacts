(ns dev
  (:require ragtacts.example.fullstack.main [hyperfiddle.electric :as e]
            #?(:clj [ragtacts.example.fullstack.server-jetty :as jetty])
            #?(:clj [shadow.cljs.devtools.api :as shadow])
            #?(:clj [shadow.cljs.devtools.server :as shadow-server])
            #?(:clj [clojure.tools.logging :as log])))

(comment (-main)) ; repl entrypoint

#?(:clj ;; Server Entrypoint
   (do
     (def config
       {:host "0.0.0.0"
        :port 8080
        :resources-path "public/ragtacts_example"
        :manifest-path ; contains Electric compiled program's version so client and server stays in sync
        "public//ragtacts_example/js/manifest.edn"})

     (defn -main [& args]
       (log/info "Starting Electric compiler and server...")

       (shadow-server/start!)
       (shadow/watch :dev)
       (comment (shadow-server/stop!))

       (def server (jetty/start-server!
                    (fn [ring-request]
                      (e/boot-server {} ragtacts.example.fullstack.main/Main ring-request))
                    config))

       (comment (.stop server)))))

#?(:cljs ;; Client Entrypoint
   (do
     (def electric-entrypoint (e/boot-client {} ragtacts.example.fullstack.main/Main nil))

     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor (electric-entrypoint
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; stop the reactor
       (set! reactor nil))))