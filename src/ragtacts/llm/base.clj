(ns ragtacts.llm.base)

(defmulti ask
  "Returns the answer to the question you asked the LLM.
   
   Args:
   - q: The string or messages seq question you want to ask the LLM. If it were a sequence of 
    messages, the items would have the following keys and string values: `:system`, `:user`, `:ai`.
     - [{:system \"You are a helpful assistant.\"}, {:user \"Hello!\"}]
       
   - params: A map of parameters to pass to the LLM.
     - `:type`: The type of LLM to use. Defaults to :open-ai.
     - `:tools`: List of function Vars to use as tools for the LLM.
     - `:as`: If you use `:value` in the `:as` option when tool is invoked with the tools
              option, it will return the resulting value of tool.

   Returns:
   - String: The answer to the question you asked the LLM.

   Example:
   ```clojure
   (ask \"Hello!\")

   (ask \"Hello!\" {:type :open-ai})

   (ask \"Hello!\" {:type :open-ai :model \"gpt-4o\"})

   (ask [{:system \"You are a helpful assistant.\"}, {:user \"Hello!\"}])
   ```
   "
  (fn [q & {:keys [type]}] type))

(defmethod ask :default
  ([q]
   (ask q {:type :open-ai}))
  ([q {:keys [type] :as params}]
   (ask q (if type
            params
            (assoc params :type :open-ai)))))