# Ragtacts

Ask LLMs easily with Ragtacts!

## Documents

- [English](./README.md)
- [한국어](./README_kr.md)

- [ractacts cljdoc](https://cljdoc.org/d/com.constacts/ragtacts/0.3.6/doc/readme)

## Prerequisites

### Install Clojure

Please install [java](https://clojure.org/guides/install_clojure#java) and 
[brew](https://clojure.org/guides/install_clojure#java) first to install Clojure.
And install Clojure with the following command:

```bash
$ brew install clojure/tools/clojure
```

### Clojure REPL with ragtacts

Create a `deps.edn` file and insert the following contents.

[![Clojars Project](https://img.shields.io/clojars/v/com.constacts/ragtacts.svg)](https://clojars.org/com.constacts/ragtacts)

```clojure
{:deps
 {com.constacts/ragtacts {:mvn/version "0.3.6"}}}
```

Next, run the Clojure REPL with the following command. Since ragtacts uses OpenAI as the default 
LLM model, an OpenAI API key is required. Refer to the 
[OpenAI documentation](https://platform.openai.com/docs/quickstart/step-2-set-up-your-api-key) to
prepare your key.

```bash
$ OPENAI_API_KEY=sk-xxxx clj
Clojure 1.11.3
user=> 
```

## QuickStart

To use the Ragtacts library, you need to `require` the `ractacts.core` namespace.

```clojure
(require '[ragtacts.core :refer :all])
```

### Ask to an LLM (Large Language Model)

Put the question you want to ask in the argument of the `ask` function.

```clojure
(ask "Hello!")
;; [{:user "Hello!"} {:ai "Hi there! How can I assist you today?"}]
```

The result of `ask` will be in the form of a question and answer. Each item in the result list is 
a map containing a role and content. The roles are `:user` and `:ai`. The last item with the LLM's 
answer will be the value associated with the `:ai` key.

The default model is OpenAI's gpt-4 but you can also ask questions to other models.

```clojure
(-> "Hello!"
    (ask {:model "gpt-4-turbo"})
    last
    :ai)
;; "Hi there! How can I assist you today?"
```

You can create question templates using the `prompt` function. The templates follow the Python 
`str.format` template syntax.

```clojure
(-> "Tell me a {adjective} joke about {content}."
    (prompt {:adjective "funny" :content "chickens"})
    ask
    last
    :ai)
;; "Sure, here's a classic one for you:\n\nWhy did the chicken go to the séance?\n\nTo ta..."
```

You can use prompts from the [Langchain Hub](https://smith.langchain.com/hub).

```clojure
(require '[ragtacts.prompt.langchain :as langchain])

(-> (langchain/hub "rlm/rag-prompt") 
    (prompt {:context "Ragtacts is an easy and powerful LLM library." 
             :question "What is Ragtacts?"})
    ask
    last
    :ai)
;; "Ragtacts is an easy and powerful LLM library."
```

If you use a model that supports [multimodal](https://platform.openai.com/docs/models) inputs, 
you can also ask questions about images.

```clojure
(->
   (ask (with-images "What are in these images? Is there any difference between them?"
     "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"
     (io/input-stream "/tmp/sample.png")))
   last
   :ai)
```

When asking a question, if you provide previous conversation context, the response will be based 
on that conversation context.

```clojure
(-> [{:system "You are a wondrous wizard of math."}
     {:user "2+2"}
     {:ai "4"}
     {:user "2+3"}
     {:ai "5"}
     {:user "What's the square of a triangle?"}]
    ask
    last
    :ai)
;; "The phrase \"square of a triangle\" is a ..."
```

Since the result of the `ask` function is conversation content, you can append the conversation 
to the result and call the `ask` function again to continue asking questions based on the previous 
conversation context.

```clojure
(-> (ask "Hi I am Ragtacts")
    (conj "What is my name?")
    ask
    last
    :ai)
;; "You mentioned earlier that your name is Ragtacts. How can I help you today, Ragtacts?"
```

### Invoke a Clojure function in natural language

You can call a Clojure function in natural language using the `ask` function. To let the LLM know 
what the function does, you need to include metadata in the function as follows.

```clojure
(defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. San Francisco"} location]
    (case (str/lower-case location)
      "tokyo" {:location "Tokyo" :temperature "10" :unit "fahrenheit"}
      "san francisco" {:location "San Francisco" :temperature "72" :unit "fahrenheit"}
      "paris" {:location "Paris" :temperature "22" :unit "fahrenheit"}
      {:location location :temperature "unknown"}))

(-> "What 's the weather like in San Francisco, Tokyo, and Paris?"
    (ask {:tools [#'get-current-weather]})
    last
    :ai)
;; "Here is the current weather in the requested cities:\n\n1. **San Francisco**: 72°F\n2. **Tokyo**: 
;;  10°F\n3. **Paris**: 22°F\n\nIt seems like the temperatures vary significantly across these cities!"  
```

In some cases, you need to use the result of calling a function as is. In such cases, you can use 
the `:as` key with the `:values` option to receive the result in the following form.

```clojure
(-> "What 's the weather like in San Francisco, Tokyo, and Paris?"
    (ask {:tools [#'get-current-weather] :as :values})
    last
    :ai)

;; [{:get-current-weather {:location "San Francisco", :temperature "72", :unit "fahrenheit"}} 
;;  {:get-current-weather {:location "Tokyo", :temperature "10", :unit "fahrenheit"}} 
;;  {:get-current-weather {:location "Paris", :temperature "22", :unit "fahrenheit"}}]
```

The results are in a list because you can call the same function multiple times in one question. 
Each item contains the result value with the function name as the key. If multiple functions are 
included in `:tools`, the LLM can find and call the appropriate function, allowing you to know 
which function was called by its key.

### Getting more accurate answers with a vector database.

A vector database stores data in vector format. Storing data as vectors allows for finding similar 
data. Suppose you ask an LLM about the contents of a book. The LLM may not be able to provide 
an accurate answer because it does not know the book's contents. However, if you include the book's 
contents in the LLM prompt, the LLM can reference it to give an accurate answer. 

The problem is that the size of the prompt the LLM can handle is limited. Using a vector database 
can reduce the data to be included in the LLM prompt. By slicing the book's contents into smaller 
parts and storing them in a vector database, you can find several pieces of data most similar 
to the question and include them in the LLM prompt. This method is called 
RAG (Retrieval-Augmented Generation).

You can easily do RAG using Ragtacts. Let's first store and retrieve data in the vector database.

```clojure
(let [db (vector-store)]
  (add db ["The new data outside of the LLM's original training data set is called external data."
            "What Is RAG?"
            "The next question may be—what if the external data becomes stale?"
            "Retrieval-Augmented Generation (RAG) is the process of optimizing the output of a large language model."
            "The next step is to perform a relevancy search."
            "Recursive summarization as Context Summarization techniques provide a condensed view of documents"])
  (search db "Tell me about RAG"))
;; ("What Is RAG?" "Retrieval-Augmented Generation (RAG) is the process of optimizing the output of a large language 
;;  model." "Recursive summarizat...)
```

The `vector-store` function creates an in-memory vector database. You can store documents in the 
vector database using the `add` function and retrieve the most similar documents using the `search` 
function, which by default fetches the 5 most similar documents in order. The number of documents 
retrieved can be changed using the `top-k` option value.

```clojure
(let [db (vector-store)]
  (add db ["The new data outside of the LLM's original training data set is called external data."
            "What Is RAG?"
            "The next question may be—what if the external data becomes stale?"
            "Retrieval-Augmented Generation (RAG) is the process of optimizing the output of a large language model."
            "The next step is to perform a relevancy search."
            "Recursive summarization as Context Summarization techniques provide a condensed view of documents"])
  (search db "Tell me about RAG" {:top-k 2}))
;; ("What Is RAG?" "Retrieval-Augmented Generation (RAG) is the process of optimizing the output of a large language 
;;  model.")
```

You can include additional information along with the documents to be stored as vectors, and filter 
your search results using this additional information.

```clojure
(let [db (vector-store)]
  (add db [{:text "What Is RAG?"
              :metadata {:topic "RAG"}}
            {:text "The next question may be—what if the external data becomes stale?"
              :metadata {:topic "Tutorial"}}
            {:text "The next step is to perform a relevancy search."
              :metadata {:topic "Tutorial"}}])
  (search db "Tell me about RAG" {:metadata {:topic "Tutorial"}}))
;; ("The next question may be—what if the external data becomes stale?" "The next step is to..")
```

The `"What Is RAG?"` was most similar to `"Tell me about RAG"`, but since the search was filtered 
to only include documents with `metadata` where the `topic` is `"Tutorial"`, `"What Is RAG?"` 
did not appear in the results.

If you add the `{:raw? true}` option to the `search` function, you can retrieve the stored vector 
values and metadata in the result.

```clojure
(let [db (vector-store)]
  (add db [{:text "What Is RAG?"
              :metadata {:topic "RAG"}}
            {:text "The next question may be—what if the external data becomes stale?"
              :metadata {:topic "Tutorial"}}
            {:text "The next step is to perform a relevancy search."
              :metadata {:topic "Tutorial"}}])
  (search db "Tell me about RAG" {:metadata {:topic "Tutorial"}
                                  :raw? true}))
;; [{:text "The next step is to perform a relevancy search." 
;;   :vector [-0.002841026 0.015938155 ...]
;;   :metadata {:topic "Tutorial"}} ...]
```

You can extract text from web pages or documents (e.g., PDF, DOC, XLS, PPT) and store it in the 
vector database for searching.

```clojure
(require '[ragtacts.loader.web :as web])

(let [db (vector-store)
      text (web/get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")]
  (add db [text])
  (search db "What is RAG?"))

(require '[ragtacts.loader.doc :as doc])

(let [db (vector-store)
      text (doc/get-text "~/papers/RAPTOR.pdf")]
  (add db [text])
  (search db "What is RAPTOR?"))
```

As mentioned earlier, you can split the text and store it in the vector database. If the text passed 
to the `add` function is long, it will be split and stored in the vector database. The default value 
is 500 characters. The text is not cut exactly at 500 characters to avoid splitting in the middle of 
a sentence or word. You can change the character limit using the `:splitter` option in the 
`vector-store` function. You need to provide the `:size` and `:overlap` options. The `:overlap` 
option specifies the overlap size to ensure text is not cut off abruptly.

```clojure
(let [db (vector-store {:splitter {:size 100 :overlap 10}})
      text (doc/get-text "~/papers/RAPTOR.pdf")]
  (add db [text])
  (search db "What is RAPTOR?"))
```

Now, let's ask the LLM based on the content in the vector database. We need to concatenate 
the retrieved content from the vector database into a string, incorporate it into an appropriate 
prompt, and then query the LLM. For the example, we will use the `rlm/rag-prompt` from 
the LangChain Hub as the prompt template.

```clojure
(let [db (vector-store)
        text (web/get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")
        rag-prompt (langchain/hub "rlm/rag-prompt")
        question "What is RAG?"]
    (add db [text])
    (-> (ask (prompt rag-prompt {:context (str/join "\n" (search db question))
                                 :question question}))
        last
        :ai))
```

Ragtacts has a `watch` function that can update the vector database with the changed content when 
the content on a web page or in a folder is updated. This function allows you to keep the data 
in the vector database synchronized with the changing data.

```clojure
(def web-wather
  (web/watch {:url "https://aws.amazon.com/what-is/retrieval-augmented-generation/"
              :interval 1000}
              (fn [change-log]
                ;; {:type :create :text "..."}
                (println change-log))))

(web/stop-watch web-wather)

;; WIP
(def folder-wather
  (doc/watch {:path "~/papers"}
              (fn [change-log]
                (println change-log))))

(doc/stop-watch folder-wather)
```

## Using Ragtacts RAG Playground

The examples folder contains a RAG Playground created with 
[electric](https://github.com/hyperfiddle/electric). Run the Playground with the following command 
and point your web browser to [http://localhost:8080](http://localhost:8080) in your web browser.

```bash
$ cd examples/playground
$ clj -A:dev -X dev/-main
```

![Playground](./doc/images/playground.png)

## Using Ragtacts as CLI

Ragtacts can also be used as a CLI. Download the `ragtacts.jar` file from the 
[Releases](https://github.com/constacts/ragtacts/releases/) page, and run it with Java to allow 
querying an LLM based on web pages or documents.

```bash
$ java -jar target/ragtacts-standalone.jar -p "What is RAG?" -d https://aws.amazon.com/what-is/retrieval-augmented-generation/
AI: RAG, or Retrieval-Augmented Generation, is a process that enhances the output of a large language model (LLM) by incorporating an information retrieval component. This component pulls relevant information from an external knowledge base and provides it to the LLM, enabling it to generate more accurate responses. This approach offers organizations better control over the generated text output and improves the overall quality of the responses. 
```

By using the `chat` mode, you can ask questions interactively.

```bash
$ java -jar target/ragtacts-standalone.jar -m chat -d https://aws.amazon.com/what-is/retrieval-augmented-generation/
Prompt: What is RAG?
AI: RAG, or Retrieval-Augmented Generation, is a process that optimizes the output of a large language model by first retrieving information from an external, authoritative knowledge base before generating a response. This allows the model to use both its training data and the new information to create more accurate and reliable answers. This approach gives organizations greater control over generated text and helps improve the quality of the responses. 
Prompt:
```

## Using Ragtacts as API Server

You can also use Ragtacts as an API server. Enter the following command and then access 
[http://localhost:3000](http://localhost:3000). The API is compatible with the OpenAI 
[Chat](https://platform.openai.com/docs/api-reference/chat) API.

```bash
$ java -jar target/ragtacts-standalone.jar -m server -d https://aws.amazon.com/what-is/retrieval-augmented-generation/
```

![ServerMode](./doc/images/server.png)

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## License

Copyright © 2024 Constacts, Inc.

Distributed under the Eclipse Public License, the same as Clojure.