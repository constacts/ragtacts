(ns ragtacts.connector.sql
  (:require [ragtacts.connector.base :refer [Connector empty-change-log-result
                                             make-change-log
                                             make-change-log-result]]
            [ragtacts.loader.base :refer [make-doc]]
            [conman.core :as conman])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))


(defrecord SqlConnector [jdbc-url]
  Connector
  (get-change-logs [_ last-change]
    (let [conn (conman/connect! {:jdbc-url jdbc-url}) ;;"jdbc:postgresql://localhost/other_test_db?user=otheradmin&password=otheradmin"
          binding (conman/bind-connection-map conn "other_service.sql")
          res (conman/query binding :get-logs {:table-name "rgtcs_articles_change_logs"})]
      (if (some? res)
        (let [^Document doc (.transform (HtmlTextExtractor.) (Document/from res))]
          (make-change-log-result (map make-change-log res)))
        empty-change-log-result))))

(defn make-sql-connector [opts]
  (map->SqlConnector opts))

