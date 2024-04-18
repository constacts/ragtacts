(ns ragtacts.connector.sql
  (:require [ragtacts.connector.base :refer [Connector make-change-log make-doc]])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.document.transformer HtmlTextExtractor]))

(defrecord SqlConnector [jdbc-url]
  Connector
  (get-change-logs [_ last-change]
    ;;아직아무것도 없다
    ))


(defn make-sql-connector [opts]
  (map->SqlConnector opts))

