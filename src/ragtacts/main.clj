(ns ragtacts.main
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [ragtacts.core :refer [ask get-text prompt save search
                                   vector-store]]
            [ragtacts.prompt.langchain :as langchain]
            [ragtacts.server :as server]))

(def cli-options
  [["-m" "--mode [query|chat|server]" "Run in chat or server mode"
    :default "query"]
   ["-d" "--data-sources URLs or PATHs" "Data sources to sync with"
    :multi true
    :update-fn conj]
   ["-p" "--prompt PROMPT" "Prompt to chat with"]])

(def usage
  "Usage: 

  query mode: [default mode]
    $ ragtacts -p \"prompt\" -d [Url or Path] -d ...
      or
    $ ragtacts -m query -p \"prompt\" -d [Url or Path] -d ...
   
  chat mode: 
    $ ragtacts -m chat -d [Url or Path] -d ...
   
  server mode: 
    $ ragtacts -m server
")

(defn- validate-options [{:keys [mode data-sources prompt]}]
  (case mode
    ("chat" "server") (and (seq data-sources) (nil? prompt))
    "query" (and prompt (seq data-sources))
    nil))

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)]
    (if (validate-options options)
      (let [{:keys [mode data-sources] query :prompt} options]
        (case mode
          "chat" (let [db (vector-store)
                       rag-prompt (langchain/hub "rlm/rag-prompt")]
                   (doseq [data-source data-sources]
                     (save db [(get-text data-source)]))
                   (loop [msgs []]
                     (print "\u001B[32mPrompt: \u001B[0m")
                     (flush)
                     (let [query (str/trim (read-line))]
                       (when-not (= query "")
                         (let [msgs (ask (conj msgs
                                               (prompt rag-prompt
                                                       {:context (str/join "\n" (search db query))
                                                        :question query})))]
                           (println "\u001B[34mAI:" (-> msgs last :ai) "\u001B[0m")
                           (recur msgs))))))

          "server" (let [db (vector-store)
                         _ (doseq [data-source data-sources]
                             (save db [(get-text data-source)]))
                         server (server/start {:db db})]
                     (.addShutdownHook (Runtime/getRuntime) (Thread. #(server/stop server))))

          "query" (let [db (vector-store)
                        rag-prompt (langchain/hub "rlm/rag-prompt")]
                    (doseq [data-source data-sources]
                      (save db [(get-text data-source)]))
                    (println "\u001B[34mAI:"
                             (->
                              (ask (prompt rag-prompt {:context (str/join "\n" (search db query))
                                                       :question query}))
                              last
                              :ai) "\u001B[0m"))
          (println usage)))
      (println usage))))