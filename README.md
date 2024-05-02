# Ragtacts

RAG(Retrieval-Augmented Generation) with your own evolving data.

## What is RAG?

RAG stands for Retrieval-Augmented Generation. It is a language model architecture that combines 
a large pre-trained language model with a retrieval system to access external knowledge sources 
like Wikipedia. This allows the model to generate outputs based not only on its training data, 
but also on relevant information retrieved from these knowledge bases, enabling improved factual 
grounding and question-answering capabilities.

## Getting Started 

### As a Library

[![Clojars Project](https://img.shields.io/clojars/v/com.constacts/ragtacts.svg)](https://clojars.org/com.constacts/ragtacts)

To use the latest release, add the following to your deps.edn (Clojure CLI)

```
com.constacts/ragtacts {:mvn/version "0.1.0"}
```

```clojure
(ns example.core
  (:require [ragtacts.core :as rg]))

;; 1. Initially, you have one document.
;; $ ls -1 ~/papers
;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf
(defn -main [& _]
  (-> ["https://aws.amazon.com/what-is/retrieval-augmented-generation/" "~/papers"]
      rg/app
      (rg/sync 
        ;; Callback that is called when a sync event occurs.
        (fn [app event]
          (println (:text (rg/chat app "What is RAG?")))
          ;; 2. If you get an answer and add antoher document to ~/papers,
          ;;    it will sync up and give you a new answer.
          ;; $ ls -1 ~/papers
          ;; RAPTOR.pdf
          ;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf
          ))))
```

### As a CLI

- Query mode

  You can use ragtacts in query mode with prompts and data sources.

  ```
  $ java -jar ./target/ragtacts-standalone.jar -p "Tell me about RAPTOR" -d ~/papers -d https://aws.amazon.com/what-is/retrieval-augmented-generation/
  AI: RAPTOR is a novel tree-based retrieval system that enhances large language models with contextual information at different levels of abstraction. It utilizes recursive clustering and summarization techniques to create a hierarchical tree structure for synthesizing information from retrieval corpora. RAPTOR outperforms traditional retrieval methods and sets new benchmarks on question-answering tasks.
  ```

- Chat mode 

  Ragtacts can also be used in interactive mode with the datasource and the -m chat option. 

  ```
  $ java -jar ./target/ragtacts-standalone.jar -m chat -d ~/papers -d https://aws.amazon.com/what-is/retrieval-augmented-generation/
  Prompt: Tell me about RAG.    
  AI: RAG stands for Retrieval-Augmented Generation. It allows developers to improve chat applications more efficiently by connecting generative models directly to live social media feeds or news sources. RAG also helps in presenting accurate information with source attribution, enhancing user trust in generative AI solutions. Additionally, it provides developers with more control over the information sources used by the generative models for generating responses. 
  Prompt: Tell me about RAPTOR.
  AI: RAPTOR is a tree-based retrieval model that selects nodes from different layers matching the detail level of a question, providing relevant and comprehensive information for downstream tasks. It consistently outperforms different retrievers when combined with them across various datasets in question-answering tasks. RAPTOR's performance has been evaluated across datasets such as NarrativeQA, QASPER, and QuALITY, where it excels in metrics like ROUGE-L, BLEU, and METEOR.
  ```

### As a API Server

You can run ragtacts with the -m server option to use it as an API server. After running in server mode, open http://localhost:3000 with your browser to see the swagger documentation.

### Advanced usage

ragtacts is largely composed of Apps that can talk to Collections, which are data stores. A collection fetches and stores data from multiple datasources connected by connectors. 

A Collection is composed of the following components

* Connector: Fetches change history from a datasource.
* DocumentLoader: Extracts text from a datasource and imports it into a document format.
* Splitter: Slices the document into chunks for division and storage in the store.
* Embedder: Embeds chunks for storage in a vector store.
* VectorStore: A vector store for semantic search.

The app consists of the following components.

* Collection: Semantically searches for chunks similar to prompts.
* PromptTemplate: Allows you to write pre-stored prompts.
* Memory: Records conversations and answers based on previous conversations.
* LLM: Use a language model to answer prompts.

### Components currently supported

- Connector
  |                 |                                                                              |
  |-----------------|------------------------------------------------------------------------------|
  | FolderConnector | Watch the folder and get any changes.                                        |
  | HttpConnector   | Watch a web resource over HTTP and fetch it back if it has changed.          |
  | SqlConnector    | Periodically fetch changes to a SQL database table.                          |

- DocumentLoader
  |                 |                                                                              |
  |-----------------|------------------------------------------------------------------------------|
  | HtmlLoader      | Extract text from an Html document.                                          |
  | OfficeDocLoader | Extract text from documents such as pdf, doc, ppt, xls, etc.                 |
  | TextLoader      | Replace plain text with documents.                                           |

- Splitter
  |                   |                                                                            |
  |-------------------|----------------------------------------------------------------------------|
  | RecursiveSplitter | Split text without breaking sentences in the middle.                       |

- Embedder
  |                   |                                                                            |
  |-------------------|----------------------------------------------------------------------------|
  | all-MiniLM-L6-v2  | HuggingFace sentence-transformers/all-MiniLM-L6-v2 embedding model         |
  | OpenAI            | Use OpenAI embedding API.<br>To use it, you need to set the OPENAI_API_KEY environment variable. |
  
- VectorStore
  |                     |                                                                          |
  |---------------------|--------------------------------------------------------------------------|
  | InMemoryVectorStore | In-memory vector database. Reads and saves as a JSON-type file.          |
  | Milvus              | Uses the Milvus database                                                 |

- Memory
  |                  |                                                                             |
  |------------------|-----------------------------------------------------------------------------|
  | WindowChatMemory | If the chat history is over a certain size, it clears the oldest content first. |
 
- LLM
  |          |                                                                                     |
  |----------|-------------------------------------------------------------------------------------|
  | LlamaCpp | Infer locally using the llama.cpp model. Support for downloading HuggingFace models |
  | OpenAI   | Uses the OpenAI API.<br>To use it, you need to set the OPENAI_API_KEY environment variable. |


### Component Configurations

You can change component settings by giving the -f option when running ragtacts. The default 
component settings are as follows

```edn
{:splitter
 {:type :recursive
  :params {:size 1000
           :overlap 20}}

 :embedder
 {:type :all-mini-lm-l6-v2
  :params {}}

 :vector-store
 {:type :in-memory
  :params {}}

 :memory
 {:type :window
  :params {:size 10}}

 :prompt-template
 {:type :default
  :params {}}

  :llm
 {:type :llama-cpp
  :params {:model {:type :hugging-face
                   :name "QuantFactory/Meta-Llama-3-8B-Instruct-GGUF"
                   :file "Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"
                   :chat-template "{% set loop_messages = messages %}{% for message in loop ...
                   :bos-token "<|begin_of_text|>"
                   :eos-token "<|end_of_text|>"}
           :n-ctx 8192}}}
```

### Create your own components

To create a component, you can implement the protocols found in the `ragtacts.[component].base` namespace. 
For a description of the protocols, see the reference documentation. The following code implements 
Embedder to create an OpenAIEmbbeder.

```clojure
(ns ragtacts.embedder.open-ai
  (:require [ragtacts.embedder.base :refer [Embedder make-embedding]]
            [wkok.openai-clojure.api :as openai]))

(defrecord OpenAIEmbedder [model]
  Embedder
  (embed [_ chunks]
    (try
      (let [texts (map :text chunks)
            {:keys [data]} (openai/create-embedding {:model model
                                                     :input texts})]
        (mapv (fn [{:keys [embedding]} {:keys [doc-id metadata text]}]
                (make-embedding doc-id
                                text
                                (map float embedding)
                                metadata))
              data
              chunks))
      (catch Exception e
        (.printStackTrace e)))))

(defn make-open-ai-embedder [{:keys [model]}]
  (->OpenAIEmbedder model))
```

## License

Copyright © 2024 Constacts, Inc.

Available under the terms of the Eclipse Public License 1.0, see LICENSE.txt