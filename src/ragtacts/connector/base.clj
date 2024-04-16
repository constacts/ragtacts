(ns ragtacts.connector.base)

(defprotocol Connector
  (get-docs [this]))
