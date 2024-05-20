(ns ragtacts.legacy.tool.base)

(defprotocol Tool
  (run [this args])
  (metadata [this]))