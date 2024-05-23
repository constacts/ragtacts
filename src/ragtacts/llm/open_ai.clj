(ns ragtacts.llm.open-ai
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [ragtacts.llm.base :refer [ask]]
            [wkok.openai-clojure.api :as openai])
  (:import [java.io ByteArrayOutputStream InputStream]
           [java.util Base64]
           [org.apache.tika Tika]))

(defn- question->msgs [q]
  (cond
    (string? q) [{:user q}]
    (vector? q) (map #(if (string? %) {:user %} %) q)
    :else (throw (ex-info (str q "is unknown question type") {:q q}))))

(defn- ->open-ai-message [{:keys [system user ai tool-calls tool tool-call-id] :as msg}]
  (cond system {:role "system" :content system}
        user {:role "user" :content user}
        ai {:role "assistant" :content ai}
        tool-calls {:role "assistant" :tool_calls tool-calls}
        tool {:role "tool" :content tool :tool_call_id tool-call-id}
        :else (throw (ex-info (str msg "is unknown message type") {:msg msg}))))

(defn- tool->function [tool-var]
  (let [fn-meta (meta tool-var)
        args (into {}
                   (map
                    (fn [arg]
                      (let [arg-meta (meta arg)]
                        [(keyword arg) {:type (:type arg-meta)
                                        :description (:desc arg-meta)}]))
                    (first (:arglists (meta tool-var)))))]
    {:type "function"
     :function {:name (name (:name fn-meta))
                :description (:desc fn-meta)
                :parameters {:type "object"
                             :properties args
                             :required (map name (first (:arglists fn-meta)))}}}))

(defn- select-tool-by-name [tools function]
  (first (filter #(= (:name function) (name (:name (meta %)))) tools)))

(defn- apply-fn [tool function]
  (let [args (first (:arglists (meta tool)))]
    (apply tool (map #(get (:arguments function) (keyword %)) args))))

(defn- chat-completion [{:keys [model msgs tools]}]
  (let [params {:model (or model "gpt-4o")
                :messages (map ->open-ai-message msgs)}]
    (-> (openai/create-chat-completion (if (seq tools)
                                         (assoc params :tools (map tool->function tools))
                                         params)
                                       {:throw-exceptions? false
                                        :trace (fn [request response]
                                                ;; (println "Request:")
                                                ;; (println (:body request))
                                                ;; (println)
                                                ;; (println "Response:")
                                                ;; (println (:body response))
                                                ;; (log/debug request)
                                                 )})
        :choices
        first
        :message)))

(defn- parse-arguments [result]
  (update result :tool_calls
          #(map (fn [tool-call]
                  (update-in tool-call [:function :arguments]
                             (fn [arguments]
                               (json/parse-string arguments true))))
                %)))

(defn- ask-open-ai [q {:keys [model tools as max-tokens temperature top-p]}]
  (let [msgs (question->msgs q)
        result (chat-completion {:model model
                                 :msgs msgs
                                 :tools tools
                                 :max_tokens max-tokens
                                 :top_p top-p
                                 :temperature temperature})]
    (if (seq tools)
      (let [parsed-result (parse-arguments result)
            fn-results (map (fn [{:keys [id function]}]
                              (let [tool (select-tool-by-name tools function)]
                                {:id id
                                 :function function
                                 :result (when tool
                                           (apply-fn tool function))}))
                            (:tool_calls parsed-result))]
        (conj (vec msgs)
              {:ai
               (if (= :values as)
                 (map (fn [{:keys [function result]}]
                        {(keyword (:name function)) result}) fn-results)
                 (:content
                  (chat-completion
                   {:model model
                    :msgs (concat msgs
                                  [{:tool-calls (-> result :tool_calls)}]
                                  (map
                                   (fn [{:keys [id result]}]
                                     {:tool (json/generate-string result)
                                      :tool-call-id id})
                                   fn-results)
                                  [(last msgs)])})))}))
      (conj (vec msgs) {:ai (:content result)}))))

(defmethod ask :open-ai [q params]
  (ask-open-ai q params))

(defn- file->bytes [input-stream]
  (with-open [xin input-stream
              xout (ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn with-images
  "Return a message with an image.
   
   Args:
    - text: The prompt to ask the user.
    - images: A list of image URLs or input streams.
   
   Example:
   ```clojure
   (->
     (with-images \"What are in these images? Is there any difference between them?\"
       \"https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg\"
       (io/input-stream \"/tmp/sample.png\"))
     ask
     last
     :ai)
   ```"
  [text & images]
  (let [image-urls (map (fn [image]
                          (if (instance? InputStream image)
                            (let [bytes (file->bytes image)
                                  mime-type (.detect (Tika.) bytes)
                                  base64 (.encodeToString (Base64/getEncoder) bytes)]
                              (str "data:" mime-type ";base64," base64))
                            image)) images)]
    [{:user
      (cons {:type "text" :text text}
            (map (fn [image-url]
                   {:type "image_url" :image_url {:url image-url}}) image-urls))}]))

(comment


  ;;
  )

