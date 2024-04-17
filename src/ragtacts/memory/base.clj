(ns ragtacts.memory.base)

(defrecord ChatMsg [type text])

(defn make-chat-msg [opts]
  (map->ChatMsg opts))

(defprotocol ChatMemory
  (add-chat [this chat-msg])
  (get-chat-history [this]))
