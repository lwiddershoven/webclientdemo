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
- Do not use ThreadLocals (like logging MDC). They don't work because threads are shared and tasks can hop threads between steps.
- There is a [subscriberContext](https://projectreactor.io/docs/core/release/reference/#context.write) that flows with the event. Use that for metadata if you need to.
- This [subscriberContext](https://projectreactor.io/docs/core/release/reference/#context.write) is accessible through [the static call Mono.subscriberContext](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#subscriberContext--)
- Due to the multithreaded nature everything is or should be immutable. In particular, the various Mono and Flux operators return a *new* Mono or Flux. `monoB = monoA.retry(2)` results in monoA without retry, and a new monoB with retry.
- Don't block. Wrap blocking calls (like traditional drivers, HttpClient, or database calls) using a [separate scheduler](https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking)
- Use [blockhound](https://github.com/reactor/BlockHound). If not on pro than at least during testing. Note that WebClient used to have an [issue with blockhound](https://github.com/reactor/reactor-netty/issues/939) but that is apparently fixed.
  If you use a modern Java in Intellij, add `-XX:+AllowRedefinitionToAddDeleteMethods` to the VM Options of the run configuration. 
  This is tested in this application. And it does throw an exception if a Thread.sleep is added in the webClient flow. So, using block() is ok.
- retry resubscribes to the start of the flow. Whatever happened happend, and is forgotten. It just starts again at the start of _this chain_ as if it is the movie _groundhog day_. 
  So make sure your flow only includes 1 IO operation.  `mainFlowInitialOp().possiblyRetry().flatMap( someIO().possiblyRetry() ).flatMap( someIO().possiblyRetry() ).absolutelyNoRetry()`.
- retry(3) makes 4 attempts. the initial attempt and 3 retries
- The exception you get when you have no 404 handling logic would look something like 
```log4j
Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed; nested exception is reactor.core.Exceptions$RetryExhaustedException: Retries exhausted: 3/3] with root cause 
org.springframework.web.reactive.function.client.WebClientResponseException$NotFound: 404 Not Found from GET http://localhost:8080/orders/1`
```  
- You can specify predicates on retry to indicate which errors should be retried. I don't doubt you can chain multiple retries each with a different predicate to fine tune error handling
- Same with error handlers like `onErrorReturn`

Logging
- property `logging.level.reactor.netty.http.client=DEBUG` at least shows you which requests are being done (to whch URL) but not that much
- You can of course log netty and spring entirely, and it does add more info, but not that much is of use.
- There was a really nice talk about reactive netty in SpringOne 2020 ([YouTube](https://www.youtube.com/watch?v=LLSln1_JAMY)) which did show how to debug requests. 
  Even if it's a bit of a deep dive I think having some understanding on what is happening is valuable. 
- Wire logging requires a code change but it can be really informative. It is also very verbose and does require some knowledge to parse on a busy machine (e.g. learn to mach trace ids).
  You can activate by configuring the reactor HttpClient like `webClientBuilder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true))).baseUrl("http://localhost:8080").build();`
  and set the logging for `reactor.netty.http.client.HttpClient` to DEBUG.