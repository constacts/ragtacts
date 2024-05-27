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

(e/def msgs (e/server (e/watch !msgs)))

#?(:clj (defonce !files (atom (list))))

(e/def files (e/server (e/watch !files)))

#?(:clj (defonce !db (atom (vector-store))))

#?(:clj (defonce rag-prompt (langchain/hub "rlm/rag-prompt")))

(e/defn Question [text]
  (e/client
   (dom/div
    (dom/props {:class "flex flex-row px-2 py-4 sm:px-4"})
    (dom/img
     (dom/props {:class "mr-2 flex h-8 w-8 rounded-full sm:mr-4"
                 :src "https://dummyimage.com/256x256/363536/ffffff&text=U"}))
    (dom/div
     (dom/props {:class "flex max-w-3xl items-center"})
     (dom/p (dom/text text))))))

(e/defn Answer [text files]
  (e/client
   (dom/div
    (dom/props {:class "mb-4 flex rounded-xl bg-white px-2 py-6 dark:bg-slate-900 sm:px-4"})
    (dom/img
     (dom/props {:class "mr-2 flex h-8 w-8 rounded-full sm:mr-4"
                 :src "https://dummyimage.com/256x256/000000/ffffff&text=A"}))
    (dom/div
     (dom/props {:class "flex flex-col gap-5"})
     (dom/div
      (dom/props {:class "flex max-w-3xl items-center rounded-xl"})
      (dom/p (dom/text text)))
     (when (seq files)
       (dom/div
        (dom/props {:class "flex gap-2"})
        (e/for-by identity [file files]
                  (dom/span
                   (dom/props {:class "rounded-xl bg-slate-100 px-4 py-2 text-gray-700 text-sm"})
                   (dom/text (str "📁 " file))))))))))

(defn await-promise "Returns a task completing with the result of given promise"
  [p]
  (let [v (m/dfv)]
    (.then p
           #(v (fn [] %))
           #(v (fn [] (throw %))))
    (m/absolve v)))

(e/defn ask-action [text]
  (e/server
   (swap! !msgs #(conj
                  (vec %)
                  {:user text}))
   (let [chunks (map #(select-keys % [:text :metadata]) (search @!db text {:raw? true}))
         new-msgs (conj (vec @!raw-msgs)
                        (prompt rag-prompt
                                {:context (str/join "\n" chunks)
                                 :question text}))
         return-msgs (ask new-msgs)]
     (reset! !raw-msgs return-msgs)
     (swap! !msgs #(conj
                    (vec %)
                    (assoc (last return-msgs)
                           :files
                           (distinct
                            (map (fn [c]
                                   (get (:metadata c) "filename")) chunks))))))))

(defn scroll-bottom [el]
  (.scrollTo el 0 (.-scrollHeight el)))

#?(:cljs
   (defonce !scroll-timer (atom nil)))

#?(:cljs
   (defn chat-scroll-down []
     (when-let [timer @!scroll-timer]
       (.clearTimeout js/window timer))
     (let [timer (.setTimeout js/window
                              #(scroll-bottom (dom/by-id "chat-content"))
                              100)]
       (reset! !scroll-timer timer))))

(e/defn Chat []
  (e/client
   (let [!loading? (atom false)
         !text (atom "")
         text (e/watch !text)]
     (dom/div
      (dom/props {:class "flex h-[60vh] w-full flex-col"})
      (dom/div
       (dom/props {:id "chat-content"
                   :class "flex-1 overflow-y-auto rounded-xl bg-slate-50 p-4 text-sm leading-6 
                         text-slate-900 dark:bg-slate-800 dark:text-slate-300 sm:text-base 
                         sm:leading-7"})
       (e/server
        (e/for-by identity [msg msgs] ; chat renders bottom up
                  (when msg
                    (e/client
                     (dom/div
                      (dom/props {:class "flex justify-between gap-5"})
                      (let [{:keys [ai user files]} msg]
                        (cond
                          user (Question. user)
                          ai (Answer. ai files)
                          :else nil)))
                     (chat-scroll-down))))))

    ;; prompt message input
      (dom/div
       (dom/props {:class "mt-2"})
       (dom/label
        (dom/props {:class "sr-only"
                    :for "chat-input"})
        (dom/text "What can I help you with?"))
       (try
         (dom/div
          (dom/props {:class "relative"})
          (ui/input
           text (e/fn [v]
                  (reset! !text v))
           (dom/props {:id "chat-input"
                       :class "block w-full resize-none rounded-xl border-none bg-slate-50 p-4 
                                  pr-20 text-sm text-slate-900 focus:outline-none focus:ring-2 
                                  focus:ring-blue-500 dark:bg-slate-800 dark:text-slate-200 
                                  dark:placeholder-slate-400 dark:focus:ring-blue-500 sm:text-base"
                       :placeholder "What can I help you with?"})
           (dom/on "keydown" (e/fn [e]
                               (when (and (= "Enter" (.-key e)) (not (.-isComposing e)))
                                 (reset! !loading? true)
                                 (ask-action. (.. e -target -value))
                                 (scroll-bottom (dom/by-id "chat-content"))
                                 (reset! !text "")
                                 (.blur (.-target e))))))
          (dom/button
           (dom/props {:type "button"
                       :class "absolute bottom-2 right-2.5 rounded-lg bg-slate-600 px-4 py-2 text-sm 
                                  font-medium text-slate-200 hover:bg-black focus:outline-none 
                                  focus:ring-4 focus:ring-blue-300 dark:bg-blue-600 
                                  dark:hover:bg-blue-700 dark:focus:ring-blue-800 sm:text-base"})
           (dom/on "click" (e/fn [e]
                             (when (seq @!text)
                               (reset! !loading? true)
                               (ask-action. @!text)
                               (scroll-bottom (dom/by-id "chat-content"))
                               (reset! !text ""))))
           (dom/text "Send")
           (dom/span
            (dom/props {:class "sr-only"})
            (dom/text "Send message"))))
         (catch Pending e
           (when @!loading?
             (dom/div
              (dom/props {:class "flex justify-center text-gray-400 mt-2"})
              (let [dots (apply str (repeat (int (mod e/system-time-secs 5)) " 🤖 "))]
                (dom/text (str dots "🤖 AI is writing 🤖" dots))))
             (reset! !loading? false)))))))))

#?(:clj
   (defn add-file [db filename base64-data]
     (let [input-stream (ByteArrayInputStream. (.decode (Base64/getDecoder) base64-data))
           doc (doc/get-text input-stream)]
       (swap! !files #(conj (vec %) filename))
       (add db [doc]))))

(e/defn Uploader []
  (e/client
   (dom/div
    (dom/props {:class "mt-5 w-full mx-auto"})
    (dom/label
     (dom/props {:class "text-base text-gray-500 font-semibold mb-2 block"})
     (dom/text "Upload document"))

    (try
      (dom/input
       (dom/props {:type "file"
                   :class "w-full text-gray-400 font-semibold text-sm bg-white border 
                         file:cursor-pointer cursor-pointer file:border-0 file:py-3 file:px-4 
                         file:mr-4 file:bg-gray-100 file:hover:bg-gray-200 file:text-gray-500 
                         rounded"})
       (dom/on "change"
               (e/fn [e]
                 (let [first-file (-> e .-target .-files (.item 0))
                       array (new (e/task->cp (await-promise (.arrayBuffer first-file))))
                       data (base64/encodeByteArray (new js/Uint8Array. array))
                       filename (.-name first-file)]
                   (e/server
                    (let [input-stream (ByteArrayInputStream. (.decode (Base64/getDecoder) data))
                          doc (-> (doc/get-text input-stream)
                                  (update :metadata assoc :filename filename))]
                      (swap! !files #(conj (vec %) filename))
                      (add @!db [doc])))
                   (set! (.-value dom/node) ""))
                 nil)))
      (dom/p
       (dom/props {:class "text-xs text-gray-400 mt-2"})
       (dom/text "PDF, DOC, PPT, XLS are Allowed"))
      (catch Pending _
        (dom/div
         (dom/props {:class "flex justify-center text-gray-400"})
         (let [dots (apply str (repeat (int (mod e/system-time-secs 5)) " 🏃‍➡️ "))]
           (dom/text (str dots "🏃‍➡️ Uploading 🏃‍➡️" dots)))))))))

(e/defn Files []
  (e/client
   (dom/div
    (dom/props {:class "mt-5"})
    (dom/p
     (dom/props {:class "text-base text-gray-500 font-semibold mb-2 block"})
     (dom/text "Documents"))
    (dom/table
     (dom/props {:class "border-collapse w-full border border-slate-400 bg-white text-sm shadow-sm"})
     (dom/thead
      (dom/props {:class "bg-slate-50 dark:bg-slate-700"})
      (dom/tr
       (dom/th
        (dom/props {:class "w-1/2 border border-slate-300 font-semibold p-4 text-slate-500 text-left"})
        (dom/text "File Name"))))
     (dom/tbody
      (if (seq files)
        (e/for-by identity [file files] ; chat renders bottom up
                  (when file
                    (e/client
                     (when-not (empty? file)
                       (dom/tr
                        (dom/td
                         (dom/props {:class "border border-slate-300 p-4 text-slate-500"})
                         (dom/text (str "📁 " file))))))))
        (dom/tr
         (dom/td
          (dom/props {:class "border border-slate-300 p-4 text-slate-500 text-center"})
          (dom/text "☁️ Upload your files and ask questions about them. ☁️")))))))))

(e/defn Main [_]
  (e/client
   (binding [dom/node js/document.body]
     (try
       (dom/div
        (dom/props {:class "mt-10 mx-44 flex flex-col gap-5 "})
        (dom/div
         (dom/props {:class "flex flex-row justify-between items-center"})
         (dom/p
          (dom/props {:class "text-lg font-semibold mb-2 block"})
          (dom/text "Ragtacts Playground"))
         (dom/button
          (dom/props {:type "button"
                      :class "rounded-lg bg-slate-200 px-4 py-2 text-sm font-medium text-slate-700 
                              hover:bg-slate-300 focus:outline-none focus:ring-4 focus:ring-blue-300 
                              sm:text-base"})
          (dom/on "click" (e/fn [e]
                            (e/server
                             (reset! !raw-msgs (list))
                             (reset! !msgs (list))
                             (reset! !files (list))
                             (reset! !db (vector-store)))
                            ;;
                            ))
          (dom/text "Reset")))
        (Chat.)
        (Uploader.)
        (Files.))))))