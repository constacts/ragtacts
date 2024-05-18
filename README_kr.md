# ragtacts

## 준비물

### Cljoure 설치하기

### OpenAI 키 받기

### 연습 프로젝트 만들기

### ragtacts 라이브러리 추가하기

## 배우기

### llm에게 물어보기

질문하고 싶은 내용을 `ask` 함수 인자에 넣어 물어보세요.

```clojure
(ask "Hello!")
```

기본 모델은 OpenAI gpt-4o지만 다른 모델에게 물어볼 수도 있습니다.

```clojure
(ask "Hello!" {:model "gpt-4-turbo"})
```

`prompt` 함수로 질문 템플릿을 만들어 쓸 수 있습니다. 템플릿은 Python `str.format` 템플릿 문법을 씁니다.

```clojure
(ask (prompt "Tell me a {adjective} joke about {content}."
     {:adjective "funny" :content "chickens"}))
```

[Langchain Hub](https://smith.langchain.com/hub) 프롬프트를 가져와 쓸 수 있습니다.

```clojure
(require '[ragtacts.prompt.langchain :as langchain])

(ask (hub "rlm/rag-prompt") 
     {:context "Ragtacts is an easy and powerful LLM library." 
      :questin "What is Ragtacts?"})
```

질문 할 때 대화 내용을 조금 입력하면 대화 내용을 바탕으로 답을 줍니다.

```clojure
(ask [{:system "You are a wondrous wizard of math."}
      {:user "2+2"}
      {:ai "4"}
      {:user "2+3"}
      {:ai "5"}
      {:user "What's the square of a triangle?"}])
```

`ask` 함수의 결과는 대화 내용입니다. 결과에 대화를 붙여 `ask` 함수를 부르면 이전 대화 맥락에 이어서 물어볼 수 있습니다.

```clojure
(-> (ask "Hi I am Ragtacts")
    (conj "What is my name?")
    ask)
```

### 자연어로 Clojure 함수를 부르기

`ask` 함수로 Clojure 함수를 자연어로 부를 수 있습니다. LLM이 함수가 어떤 일을 하는지 알려주려면 함수에 다음과 같이
메타데이터를 넣어줘야 합니다.

```clojure
  (defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. San Francisco"} location]
    (case (str/lower-case location)
      "tokyo" {:location "Tokyo" :temperature "10" :unit "fahrenheit"}
      "san francisco" {:location "San Francisco" :temperature "72" :unit "fahrenheit"}
      "paris" {:location "Paris" :temperature "22" :unit "fahrenheit"}
      {:location location :temperature "unknown"}))

  (ask "What 's the weather like in San Francisco, Tokyo, and Paris?"
       {:tools [#'get-current-weather]})
```

### 벡터 데이터베이스로 더 정확한 답을 받기

