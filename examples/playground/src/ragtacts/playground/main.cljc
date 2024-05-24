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

(e/defn Answer [text]
  (e/client
   (dom/div
    (dom/props {:class "mb-4 flex rounded-xl bg-white px-2 py-6 dark:bg-slate-900 sm:px-4"})
    (dom/img
     (dom/props {:class "mr-2 flex h-8 w-8 rounded-full sm:mr-4"
                 :src "https://dummyimage.com/256x256/000000/ffffff&text=A"}))
    (dom/div
     (dom/props {:class "flex max-w-3xl items-center rounded-xl"})
     (dom/p (dom/text text))))))

(defn await-promise "Returns a task completing with the result of given promise"
  [p]
  (let [v (m/dfv)]
    (.then p
           #(v (fn [] %))
           #(v (fn [] (throw %))))
    (m/absolve v)))

(e/defn ask-action [e]
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
         (println return-msgs)
         (println @!msgs)
         (reset! !raw-msgs return-msgs)
         (swap! !msgs #(conj
                        (vec %)
                        (last return-msgs)))))
      (set! (.-value dom/node) ""))))

(e/defn Chat []
  (e/client
   (dom/div
    (dom/props {:class "flex h-[60vh] w-full flex-col"})
    (dom/div
     (dom/props {:class "flex-1 overflow-y-auto rounded-xl bg-slate-50 p-4 text-sm leading-6 
                         text-slate-900 dark:bg-slate-800 dark:text-slate-300 sm:text-base 
                         sm:leading-7"})

     (e/server
      (e/for-by identity [msg msgs] ; chat renders bottom up
                (when msg
                  (e/client
                   (dom/div
                    (dom/props {:class "flex justify-between gap-5"})
                    (let [[role content] (first msg)]
                      (if (= :user role)
                        (Question. content)
                        (Answer. content)))))))))

    ;; prompt message input
    (dom/div
     (dom/props {:class "mt-2"})
     (dom/label
      (dom/props {:class "sr-only"
                  :for "chat-input"})
      (dom/text "What can I help you with?"))
     (dom/div
      (dom/props {:class "relative"})
      (dom/input
       (dom/props {:id "chat-input"
                   :class "block w-full resize-none rounded-xl border-none bg-slate-50 p-4 pl-10 
                           pr-20 text-sm text-slate-900 focus:outline-none focus:ring-2 
                           focus:ring-blue-500 dark:bg-slate-800 dark:text-slate-200 
                           dark:placeholder-slate-400 dark:focus:ring-blue-500 sm:text-base"
                   :placeholder "What can I help you with?"})
       (dom/on "keydown" ask-action))
      (dom/button
       (dom/props {:type "button"
                   :class "absolute bottom-2 right-2.5 rounded-lg bg-slate-700 px-4 py-2 text-sm 
                           font-medium text-slate-200 hover:bg-black focus:outline-none 
                           focus:ring-4 focus:ring-blue-300 dark:bg-blue-600 
                           dark:hover:bg-blue-700 dark:focus:ring-blue-800 sm:text-base"})
       (dom/text "Send")
       (dom/span
        (dom/props {:class "sr-only"})
        (dom/text "Send message"))))))))

#?(:clj
   (defn add-file [db filename base64-data]
     (let [input-stream (ByteArrayInputStream. (.decode (Base64/getDecoder) base64-data))
           doc (doc/get-text input-stream)]
       (swap! !files #(conj (vec %) filename))
       (add db [doc]))))

(e/defn Uploader []
  (e/client
   (dom/div
    (dom/props {:class "flex items-center justify-center w-full"})
    (dom/label
     (dom/props {:for "dropzone-file"
                 :class "flex flex-col items-center justify-center w-full h-64 border-2 
                                        border-gray-300 border-dashed rounded-lg cursor-pointer bg-gray-50 
                                        dark:hover:bg-bray-800 dark:bg-gray-700 hover:bg-gray-100 
                                        dark:border-gray-600 dark:hover:border-gray-500 dark:hover:bg-gray-600"})
     (dom/div
      (dom/props {:class "flex flex-col items-center justify-center pt-5 pb-6"})
      (dom/text "📁")
      (dom/p
       (dom/props {:class "mb-2 text-sm text-gray-500 dark:text-gray-400"})
       (dom/span
        (dom/props {:class "font-semibold"})
        (dom/text "Click to upload"))
       (dom/text " or drag and drop"))
      (dom/p
       (dom/props {:class "text-xs text-gray-500 dark:text-gray-400"})
       (dom/text "SVG, PNG, JPG or GIF (MAX. 800x400px)")))
     (dom/input
      (dom/props {:id "dropzone-file"
                  :type "file"
                  :class "hidden"})
      (dom/on "change"
              (e/fn [e]
                (let [first-file (-> e .-target .-files (.item 0))
                      array (new (e/task->cp (await-promise (.arrayBuffer first-file))))
                      data (base64/encodeByteArray (new js/Uint8Array. array))
                      filename (.-name first-file)]
                  (e/server
                   ;;(println filename)
                   (let [input-stream (ByteArrayInputStream. (.decode (Base64/getDecoder) data))
                         doc (doc/get-text input-stream)]
                     (swap! !files #(conj (vec %) filename))
                     (add db [doc]))))
                nil)))))))

(e/defn Files []
  (e/client
   (let [!state (atom {:array nil})]
     (dom/div
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
         (dom/text "Add File"))))))))

(e/defn Main [_]
  (e/client
   (binding [dom/node js/document.body]
     (try
       (dom/div
        (dom/props {:class "mt-12 mx-44 flex flex-col gap-5 text-lg"})
        (dom/h1 (dom/text "Ragtacts Playground"))
        (Chat.)
        (Uploader.)
        (Files.))
       (catch Pending e
         #_(dom/props {}))))))


