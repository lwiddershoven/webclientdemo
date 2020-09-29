# WebClientDemo

## This Project

I wrote this project to explore the replacement of Spring RestTemplate, WebClient. I wrote it both to understand the basics
of WebClient and reactive programming, and to share some of the constructs I found.

Whether this code is any good I do not know. Use at your own risk :) 

## Setup

There is an accompanying project [SimpleServer](https://github.com/lwiddershoven/simpleserver) that runs on port 8080
and provides some mock data. You can keep this running. 

You can run the application like any spring boot application: `mvn spring-boot:run` or in intellij just `Ctrl-Alt-F10` 
on the application class.

The applications use (SpringFox)[https://springfox.github.io/springfox/] to generate a nice API web page under `localhost:8080/swagger-ui`
for the mock server, and `localhost:8081/swagger-ui` for this server.

You can also use [httpie](https://httpie.org/) to test the calls. That is what I personally do. Or curl, but httpie is much more convenient.

## How to start

I named the controllers in order. ABasicExamplesController is the start, going onto DWithErrorHandlingController. 
EZipExampleController is an extra.

Look at the code to see if you can understand it. And run the tests (and look at the elapsed time) to see 
various things running in parallel. Every call on the mock server takes 2 seconds.

Available calls are (httpie commands):
- `http GET http://localhost:8081/basic-examples/orders/1`
- `http GET http://localhost:8081/basic-examples/orders/?id=1,2,3,4,5,6,7`
- `http GET http://localhost:8081/basic-examples/enriched-orders/1`
- `http GET http://localhost:8081/refactored-examples/enriched-orders/1`
- `http GET http://localhost:8081/with-retries-example/enriched-orders/1`
- `http GET http://localhost:8081/decent-error-handling-example/enriched-orders/1`
- `http GET http://localhost:8081/zip-example/orders/1`

## Some extra remarks

- if you do not `subscribe` (or `block`/`blockLast`) nothing happens
- try to use `retrieve`, not `exchange`
- if you *have* to use `exchange`, you *must* consume the body to prevent a [memory leak](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/WebClient.RequestHeadersSpec.html#exchange--)
- There should be at most 1 `subscribe` (or `block`/`blockLast`) in the code started by the controller. Zero if you use the WebFlux server where controller methods return `Mono` or `Flux`
- [How do I ....](https://projectreactor.io/docs/core/release/reference/#faq). Read this. Really. At least the questions. So you know what is apperently not trivial
- Operator documentation can be found in [Mono](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html) and [Flux](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html) 
  It pays to spend the time and understand the diagrams. Even at the expense of the headaches you'll get.
-  [Testing Reactor](https://projectreactor.io/docs/core/release/reference/#testing) is not easy, but there are facilities to help
- Do not use resilience4j retry in your flow. It blocks. 
- [Map or Flatmap](https://medium.com/@nikeshshetty/5-common-mistakes-of-webflux-novices-f8eda0cd6291#:~:text=Yes%20there%20is%20a%20difference,operations%20which%20are%20done%20synchronously.)


TODO. Wisdom of blockhound, how to deal with traditional HttpClient calls, links to reactive docs.