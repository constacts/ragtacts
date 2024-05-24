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
          (dom/h1
           (dom/style {:font-size "24px"
                       :margin-bottom "20px"})
           (dom/text
            "Ragtacts"))
          (dom/style {:margin-top "50px"
                      :margin-left "180px"
                      :margin-right "180px"
                      :display "flex"
                      :flex-direction "column"
                      :gap "20px"})
          ;; Chat
          (dom/div
           (dom/style {:display "flex"
                       :flex-direction "column"
                       :gap "20px"})
           (e/server
            (e/for-by identity [msg msgs] ; chat renders bottom up
                      (when msg
                        (e/client
                         (dom/div
                          (dom/style {:display "flex"
                                      :justify-content "space-between"
                                      :gap "20px"
                                      :visibility (if (nil? msg)
                                                    "hidden"
                                                    "visible")})
                          (let [[role content] (first msg)]
                            (dom/div (dom/style {:width "80px"
                                                 :flex-shrink 0})
                                     (dom/text (name role)))
                            (dom/div (dom/style {:width "100%"})
                                     (dom/text content)))))))))

          ;; Chat Input
          (dom/div
           (dom/style {:display "flex"
                       :align-items "center"
                       :gap "20px"})
           (dom/div
            (dom/style {:width "80px"
                        :flex-shrink 0})
            (dom/text "user"))
           (dom/input
            (dom/props {:style {:width "100%"
                                :padding "8px"
                                :border-radius "10px"
                                :border "1px solid #000"
                                :font-size "18px"}
                        :placeholder "What can I help you with?"
                        :maxlength 100})
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

          (dom/hr (dom/style {:margin-top "40px"
                              :width "100%"}))
          ;; File List
          (dom/div
           (dom/style {:margin-top "40px"
                       :display "flex"
                       :flex-direction "column"
                       :gap "40px"})
           (dom/div
            (dom/style {:display "flex"
                        :align-items "flex-end"
                        :gap "10px"})
            (dom/p
             (dom/style {:font-size "22px"})
             (dom/text "Files"))
            (dom/p
             (dom/style {:font-size "16px"})
             (dom/text "Upload your files and ask questions about them.")))
           (dom/div
            (dom/style {:display "flex"
                        :flex-direction "column"
                        :gap "20px"})
            (e/server
             (e/for-by identity [file files] ; chat renders bottom up
                       (e/client
                        (when-not (empty? file)
                          (dom/div
                           (dom/style {:display "flex"
                                       :justify-content "space-between"
                                       :gap "20px"})
                           (dom/li (dom/style {:width "100%"})
                                   (dom/text file)))))))))

          ;; File Input
          (dom/div
           (dom/style {:display "flex"
                       :justify-content "space-between"
                       :gap "20px"})
           (dom/input
            (dom/on "change"
                    (e/fn [e]
                      (let [first-file (-> e .-target .-files (.item 0))]
                        (swap! !state assoc :file first-file))
                      nil))
            (dom/props {:type "file"
                        :style {:font-size "18px"
                                :width "500px"
                                :height "50px"
                                :padding "10px"
                                :border-radius "10px"
                                ;; :display "inline-block"
                                :vertical-align "middle"
                                ;; :border "1px solid #000"
                                }}))
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
            (dom/style {:font-size "18px"
                        :padding "10px"
                        :border-radius "10px"
                        :border "1px solid #000"})
            (dom/div
             (dom/text "Add File")))))
         (catch Pending e
           (dom/style {})))))))


