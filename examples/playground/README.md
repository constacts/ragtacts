# ragtacts/fullstack

This ragtacts example demonstrates how to ask an LLM questions based on an uploaded file. 
The example was created using [electric](https://github.com/hyperfiddle/electric). 
To run the example, enter the following command and then put http://localhost:8080 into your 
browser's address bar:

## Instructions

Dev build:

* Shell: `clj -A:dev -X dev/-main`, or repl: `(dev/-main)`
* http://localhost:8080
* Electric root function: [src/ragtacts/playground/main.cljc](src/ragtacts/playground/main.cljc)
* Hot code reloading works: edit -> save -> see app reload in browser

Prod build:

```shell
clj -X:build:prod build-client
clj -M:prod -m prod
```

Uberjar (optional):
```
clj -X:build:prod uberjar :build/jar-name "target/app.jar"
java -cp target/app.jar clojure.main -m prod
```