(ns ragtacts.memory.window
  (:require [ragtacts.memory.base :refer [ChatMemory add-chat get-chat-history make-chat-msg]])
  (:import [dev.langchain4j.memory.chat MessageWindowChatMemory]
           [dev.langchain4j.data.message SystemMessage UserMessage AiMessage]))

(defrecord WindowChatMemory [memory]
  ChatMemory
  (add-chat [_ {:keys [type text]}]
    (let [msg (case type
                :user (UserMessage. text)
                :system (SystemMessage. text)
                :ai (AiMessage. text))]
      (.add memory msg)))

  (get-chat-history [_]
    (map
     (fn [msg]
       (cond
         (instance? AiMessage msg) {:type :ai :text (.text msg)}
         (instance? UserMessage msg) {:type :user :text (.singleText msg)}
         (instance? SystemMessage msg) {:type :system :text (.text msg)}
         :else (throw (ex-info "Invalid ChatMessage Type" {:msg msg}))))
     (.messages memory))))

(defn make-window-chat-memory [{:keys [size]}]
  (->WindowChatMemory (MessageWindowChatMemory/withMaxMessages size)))

(comment
  (let [m (make-window-chat-memory {:size 2})]
    (add-chat m (make-chat-msg {:type :ai :text "hello1"}))
    (add-chat m (make-chat-msg {:type :user :text "hello2"}))
    (add-chat m (make-chat-msg {:type :ai :text "hello3"}))
    (get-chat-history m))
  ;;
  )