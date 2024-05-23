(ns ragtacts.llm.base)

(defmulti ask
  "Returns the answer to the question you asked the LLM.
   
   Args:
   - q: The string or messages seq question you want to ask the LLM. If it were a sequence of 
    messages, the items would have the following keys and string values: `:system`, `:user`, `:ai`.
     - [{:system \"You are a helpful assistant.\"}, {:user \"Hello!\"}]
       
   - params: A map of parameters to pass to the LLM.
     - `:type`: The type of LLM to use. Defaults to :open-ai.
     - `max-tokens`: The maximum number of tokens that can be generated in the chat completion.
     - `temperature`: What sampling temperature to use, between 0 and 2.
     - `top-p`: An alternative to sampling with temperature, called nucleus sampling, 
                where the model considers the results of the tokens with top-p probability mass. 
     - `:tools`: List of function Vars to use as tools for the LLM.
     - `:as`: If you use `:values` in the `:as` option when tool is invoked with the tools
              option, it will return the resulting list of following map of tool calls:
        - key: function name
        - value: result of the function call

   Returns:
   - String: The answer to the question you asked the LLM.

   Example:
   ```clojure
   (ask \"Hello!\")

   (ask \"Hello!\" {:type :open-ai})

   (ask \"Hello!\" {:type :open-ai :model \"gpt-4o\"})

   (ask [{:system \"You are a helpful assistant.\"}, {:user \"Hello!\"}])

   ;; tools
   (defn ^{:desc \"Get the current weather in a given location\"} get-current-weather 
     [^{:type \"string\" :desc \"The city and state, e.g. San Francisco, CA\"} location] 
     ...))
                                                        
   (ask \"What's the weather like in San Francisco, Tokyo, and Paris?\" 
        {:tools [#'get-current-weather]})
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

