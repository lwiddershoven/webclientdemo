package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Configuration
@SpringBootApplication
public class WebclientdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebclientdemoApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8080").build();
    }

}


@Slf4j
@RestController
@AllArgsConstructor
class Demo {

    private WebClient webClient;

    // One Call for 1 ID
    @GetMapping("/one/{id}")
    @ResponseBody
    public String getOne(@PathVariable("id") String id) {
        log.info("Get One for id {}", id);
        Order order = webClient.get().uri("/orders/{id}", id).retrieve().bodyToMono(Order.class).block();
        log.info("Get One for id {} completed with {}", id, order);
        return "finished";
    }

    // One Call per ID, multiple ids
    @GetMapping("/many/")
    @ResponseBody
    public String getMany(@RequestParam("id") Set<String> ids) {
        log.info("Get for ids {}", ids);
        List<Order> orders = Flux.fromIterable(ids)
                .flatMap(id -> webClient.get().uri("/orders/{id}", id).retrieve().bodyToMono(Order.class).retry(3)) // Flatmap: Ditch the Mono 'wrapper'
                .collectList()
                .block(Duration.of(3, ChronoUnit.SECONDS));
        // One requests takes 2 seconds ....
        // 1o parallel requests take the same.

        // NB: No connection pool used or required as Netty can handle a lot of connections with just a few threads
        log.info("Get for ids {} completed with {}", ids, orders);
        return "finished";
    }

    // One Call returning list, Each with follow up calls
    @GetMapping("/orders-with-lines-and-product/{id}")
    @ResponseBody
    public String getEnrichedOrderList(@PathVariable("id") String id) {
        log.info("Retrieve enriched order {}", id);
        EnrichedOrder enrichedOrder = webClient.get()
                .uri("/orders/{id}", id)
                .retrieve().bodyToMono(Order.class)
                .flatMap(order ->
                        Flux.fromIterable(order.getOrderLineIds())
                                .flatMap(orderLineId -> webClient.get().uri("/orderlines/{id}", orderLineId).retrieve().bodyToMono(OrderLine.class))
                                .flatMap(orderLine -> webClient.get().uri("/products/{id}", orderLine.getProductId()).retrieve().bodyToMono(Product.class).map(product -> new EnrichedOrderLine(orderLine, product)))
                                .collectList()
                                .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .block(Duration.of(15, ChronoUnit.SECONDS));

        log.info("Return enriched order {}", enrichedOrder);
        return "completed";
    }

    // One Call returning list, Each with follow up calls
    @GetMapping("/orders-with-lines-and-product-2/{id}")
    @ResponseBody
    public String getEnrichedOrderList2(@PathVariable("id") String id) {
        log.info("Retrieve enriched order {}", id);
        EnrichedOrder enrichedOrder = getOrder(id)
                .flatMap(order ->
                        Flux.fromIterable(order.getOrderLineIds())
                                .flatMap(orderLineId -> getOrderLine(orderLineId))
                                .flatMap(orderLine -> getProduct(orderLine.getProductId()).map(product -> new EnrichedOrderLine(orderLine, product)))
                                .collectList()
                                .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .block(Duration.of(15, ChronoUnit.SECONDS));

        log.info("Return enriched order {}", enrichedOrder);
        return "completed";
    }

    // One Call returning list, Each with follow up calls
    @GetMapping("/orders-with-lines-and-product-3/{id}")
    @ResponseBody
    public String getEnrichedOrderList3(@PathVariable("id") String id) {
        log.info("Retrieve enriched order {}", id);
        EnrichedOrder enrichedOrder = getOrder(id)
                .retry(3)
                .flatMap(order ->
                                Flux.fromIterable(order.getOrderLineIds())
                                        .flatMap(orderLineId -> getOrderLine(orderLineId).retryWhen(Retry.fixedDelay(3, Duration.ofMillis(500))))
                                        .flatMap(orderLine -> getProduct(orderLine.getProductId())
                                                .onErrorReturn(
                                                        throwable -> (throwable instanceof WebClientResponseException.NotFound),
                                                        new Product("default", "unknown product")
                                                )
                                                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))

//                                        .onErrorReturn(new Product("default", "unknown product"))
                                                .map(product -> new EnrichedOrderLine(orderLine, product)))
                                        .collectList()
                                        .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .block(Duration.of(15, ChronoUnit.SECONDS));

        log.info("Return enriched order {}", enrichedOrder);
        return "completed";
    }

    @GetMapping("/orders-with-lines-and-product-4/{id}")
    @ResponseBody
    public String getEnrichedOrderList4(@PathVariable("id") String id) {
        log.info("Retrieve enriched order {}", id);
        EnrichedOrder enrichedOrder = getOrder(id)
                .flatMap(order ->
                        Flux.fromIterable(order.getOrderLineIds())
                                .flatMap(this::getOrderLine)
                                .flatMap(orderLine -> getProduct(orderLine.getProductId())
                                        .map(product -> new EnrichedOrderLine(orderLine, product)))
                                .collectList()
                                .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .doOnSuccess(completedEnrichedOrder -> log.info("Success retrieving id {}", id, completedEnrichedOrder))
                .doOnError(throwable -> log.warn("Retrieving id {} failed", id, throwable))
                .block(Duration.of(15, ChronoUnit.SECONDS));

        log.info("Return enriched order {}", enrichedOrder);
        return "completed";
    }

    private Mono<Order> getOrder(String id) {
        return webClient.get()
                .uri("/orders/{id}", id)
                .retrieve()
                .bodyToMono(Order.class)
                .doOnError(t -> log.info("[non-final] retrieving order id {} failed with message {}", id, t.getMessage()))
                .retry(3)
                .doOnError(t -> log.error("[final] retrieving order id {} has exhausted retries and failed", id, t));
    }

    private Mono<OrderLine> getOrderLine(String id) {
        return webClient.get()
                .uri("/orderlines/{id}", id)
                .retrieve()
                .bodyToMono(OrderLine.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(500)));
    }

    private Mono<Product> getProduct(String id) {
        return webClient.get()
                .uri("/products/{id}", id)
                .retrieve()
                .bodyToMono(Product.class)
                .onErrorReturn(
                        throwable -> {
                            if(throwable instanceof WebClientResponseException.NotFound) {
                                log.warn("Call for product id {} returned 404. replacing result with default value");
                                return true;
                            } else {
                                return false;
                            }
                        },
                        new Product("default", "unknown product")
                )
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                .doOnError(t -> log.error("Could not retrieve product with id {}", id, t));
    }

}

@Data
@AllArgsConstructor
class EnrichedOrder {
    private Order order;
    private List<EnrichedOrderLine> orderLines;

}

@Data
@AllArgsConstructor
class EnrichedOrderLine {
    private OrderLine orderLine;
    private Product product;
}

@Data
@AllArgsConstructor
class Order {
    private String id;
    private List<String> orderLineIds;
}

@Data
@AllArgsConstructor
class OrderLine {
    private String id, productId;
}

@Data
@AllArgsConstructor
class Product {
    private String id, data;
}
