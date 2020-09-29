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

TODO. Wisdom of blockhound, how to deal with traditional HttpClient calls, links to reactive docs.