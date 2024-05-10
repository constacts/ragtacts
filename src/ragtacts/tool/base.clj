(ns ragtacts.tool.base)

(defprotocol Tool
  (run [this args])
  (metadata [this]))