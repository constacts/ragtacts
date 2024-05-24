(ns ragtacts.playground.main
  (:import [hyperfiddle.electric Pending]
           #?(:clj [java.util Base64])
           #?(:clj [java.io ByteArrayInputStream]))
  (:require [contrib.data :refer [pad]]
            [contrib.str :refer [empty->nil]]
            [hyperfiddle.electric-ui4 :as ui]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [missionary.core :as m]
            #?(:clj [ragtacts.core :refer [ask vector-store add prompt search]])
            #?(:clj [ragtacts.loader.doc :as doc])
            #?(:clj [clojure.string :as str])
            #?(:clj [ragtacts.prompt.langchain :as langchain])
            #?(:cljs [goog.crypt.base64 :as base64])))

#?(:clj (defonce !raw-msgs (atom (list))))

#?(:clj (defonce !msgs (atom (list))))

(e/def msgs (e/server (pad 10 nil (e/watch !msgs))))

#?(:clj (defonce !files (atom (list))))

(e/def files (e/server (pad 10 nil (e/watch !files))))

#?(:clj (defonce db (vector-store)))

#?(:clj (defonce rag-prompt (langchain/hub "rlm/rag-prompt")))

(defn await-promise "Returns a task completing with the result of given promise"
  [p]
  (let [v (m/dfv)]
    (.then p
           #(v (fn [] %))
           #(v (fn [] (throw %))))
    (m/absolve v)))

(e/defn Main [_]
  (e/client
   (let [!state (atom {:array nil})]
     (binding [dom/node js/document.body]
       (try
         ;; Container
         (dom/div
          (dom/props {:class "mt-12 mx-44 flex flex-col gap-5"})
          (dom/h1
           (dom/text
            "Ragtacts"))
          ;; Chat
          (dom/div
           (dom/props {:class "flex flex-col gap-5"})
           (e/server
            (e/for-by identity [msg msgs] ; chat renders bottom up
                      (when msg
                        (e/client
                         (dom/div
                          (dom/props {:class "flex justify-between gap-5"})
                          (let [[role content] (first msg)]
                            (dom/div (dom/props {:class "w-20 shrink-0"})
                                     (dom/text (name role)))
                            (dom/div (dom/props {:class "w-full"})
                                     (dom/text content)))))))))

          ;; Chat Input
          (dom/div
           (dom/props {:class "flex flex-row gap-5 items-center"})
           (dom/div
            (dom/props {:class "shrink-0 w-20"})
            (dom/text "user"))
           (dom/input
            (dom/props {:class "w-full p-2 text-sm border rounded-lg"
                        :placeholder "What can I help you with?"})
            (dom/on "keydown"
                    (e/fn [e]
                      (when (= "Enter" (.-key e))
                        (when-some [v (empty->nil (.substr (.. e -target -value) 0 100))]
                          (e/server
                           (swap! !msgs #(conj
                                          (vec %)
                                          {:user v}))
                           (let [new-msgs (conj (vec @!raw-msgs)
                                                (prompt rag-prompt
                                                        {:context (str/join "\n" (search db v))
                                                         :question v}))
                                 return-msgs (ask new-msgs)]
                             (reset! !raw-msgs return-msgs)
                             (swap! !msgs #(conj
                                            (vec %)
                                            (last return-msgs))))
                           #_(swap! !msgs #(cons v (take 9 %))))
                          (set! (.-value dom/node) "")))))))

          (dom/hr
           (dom/props {:class "mt-10 w-full"}))
          ;; File List
          (dom/div
           (dom/props {:class "mt-10 flex flex-col gap-5"})
           (dom/div
            (dom/props {:class "mt-10 flex items-end gap-2"})
            (dom/p
             (dom/props {:class "text-sm"})
             (dom/text "Files"))
            (dom/p
             (dom/props {:class "text-base"})
             (dom/text "Upload your files and ask questions about them.")))
           (dom/div
            (dom/props {:class "flex flex-col gap-5"})
            (e/server
             (e/for-by identity [file files] ; chat renders bottom up
                       (e/client
                        (when-not (empty? file)
                          (dom/div
                           (dom/props {:class "flex justify-between gap-5"})
                           (dom/li (dom/props {:class "w-full"})
                                   (dom/text file)))))))))

          ;; File Input
          (dom/div
           (dom/props {:class "flex justify-between gap-5"})
           (dom/input
            (dom/on "change"
                    (e/fn [e]
                      (let [first-file (-> e .-target .-files (.item 0))]
                        (swap! !state assoc :file first-file))
                      nil))
            (dom/props {:type "file"
                        :class "text-sm w-100 h-10 p-2 border rounded-lg align-middle"}))
           (ui/button
            (e/fn []
              (let [array (new (e/task->cp (await-promise (-> @!state :file (.arrayBuffer)))))
                    data (base64/encodeByteArray (new js/Uint8Array. array))
                    file (-> @!state :file .-name)]
                (e/server
                 (let [input-stream (ByteArrayInputStream. (.decode (Base64/getDecoder) data))
                       doc (doc/get-text input-stream)]
                   (swap! !files #(conj (vec %) file))
                   (add db [doc]))))
              nil)
            (dom/props {:class "text-sm p-2 border rounded"})
            (dom/div
             (dom/text "Add File")))))
         (catch Pending e
           (dom/props {})))))))


