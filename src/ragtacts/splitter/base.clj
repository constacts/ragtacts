(ns ragtacts.splitter.base)

(defprotocol Splitter
  (split [this doc]))