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
(ask 
  (prompt "Context: { }\nQuestion: { }" 
     {:question ""}))
```

