(ns ragtacts.prompt.langchain
  (:require [hato.client :as http]))

(def ^:private base-url "https://api.hub.langchain.com")

(defn- get-latest-commit-hash [repo]
  (let [url (str base-url "/commits/" repo "/?limit=10&offset=0")
        {:keys [status body]} (http/get url {:as :json})]
    (when (= 200 status)
      (-> body
          :commits
          first
          :commit_hash))))

(defn hub
  "Retrun a public repository prompt template from the [Langchain Hub](https://smith.langchain.com/hub).
   
   Example:
   ```clojure
   (hub \"rlm/rag-prompt\")
   ```
   "
  [repo]
  (when-let [latest-commit-hash (get-latest-commit-hash repo)]
    (let [url (str base-url "/commits/" repo "/" latest-commit-hash)
          {:keys [status body]} (http/get url {:as :json})]
      (when (= 200 status)
        (let [{:keys [template
                      template_format]} (-> body
                                            :manifest
                                            :kwargs
                                            :messages
                                            first
                                            :kwargs
                                            :prompt
                                            :kwargs)]
          (if (= "f-string" template_format)
            template
            (throw (ex-info "Only f-string template is supported"
                            {:template-format template_format}))))))))

(comment
  (hub "rlm/rag-prompt")
  ;;
  )