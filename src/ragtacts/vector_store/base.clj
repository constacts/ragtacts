(ns ragtacts.vector-store.base
  (:require [ragtacts.embedding.base :as embedding]))

(defmulti save
  "Returns the answer to the question you asked the LLM.
     
     Args:
     - q: The string or messages seq question you want to ask the LLM. If it were a sequence of 
      messages, the items would have the following keys and string values: `:system`, `:user`, `:ai`.
       - [{:system \"You are a helpful assistant.\"}, {:user \"Hello!\"}]
         
     - params: A map of parameters to pass to the LLM.
       - :type: The type of LLM to use. Defaults to :open-ai.
  
     Returns:
     - String: The answer to the question you asked the LLM.
  
     Example:"
  (fn [{:keys [db]} docs] (:type db)))

(defmulti search (fn [{:keys [db]} query & [params]] (:type db)))

(defn embed [{:keys [embedding]} texts]
  (embedding/embed embedding texts))