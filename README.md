# Ragtacts

RAG(Retrieval-Augmented Generation) with your own evolving data.

## What is RAG?

RAG stands for Retrieval-Augmented Generation. It is a language model architecture that combines 
a large pre-trained language model with a retrieval system to access external knowledge sources 
like Wikipedia. This allows the model to generate outputs based not only on its training data, 
but also on relevant information retrieved from these knowledge bases, enabling improved factual 
grounding and question-answering capabilities.

## Features

- 

## Getting Started 

### As a Library

To use the latest release, add the following to your deps.edn (Clojure CLI)

```
com.constacts/ragtacts {:mvn/version "0.1.0"}
```

```clojure
(require '[ragtacts.core :refer [app sync chat]])
;; 1. Initially, you have one document.
;; $ ls -1 ~/papers
;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf

(-> (app ["https://aws.amazon.com/what-is/retrieval-augmented-generation/"
          "~/papers"])
    (sync
      ;; Callback that is called when a sync event occurs.
      (fn [app event]
        (when (= :complete (:type event))
          (println (chat app "Tell me about RAG technology."))
          ;; 2. If you get an answer and add antoher document to ~/papers,
          ;;    it will sync up and give you a new answer.
          ;; $ ls -1 ~/papers
          ;; RAPTOR.pdf
          ;; Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.pdf
          ))))
```

### As a CLI


### As a API Server

### Advanced Usage


## License

Copyright © 2024 Constacts, Inc.

Available under the terms of the Eclipse Public License 1.0, see LICENSE.txt