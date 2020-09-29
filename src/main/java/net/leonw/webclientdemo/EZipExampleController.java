package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.List;

// A demo of Mono.zip. My other calls did not have concurrent retrieval of different services,
// so I'll demo it here with some error handling and logging.
// As always: understand the diagram in https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#zip-reactor.core.publisher.Mono-reactor.core.publisher.Mono-
// The point: if one of the Mono's completes before generating an item the zip completes without generating an item even if the other Mono did produce a result. That result would just be ignored.
@Slf4j
@AllArgsConstructor
@RestController
public class EZipExampleController {

    public static final RetryBackoffSpec RETRY_BACKOFF_SPEC = Retry.backoff(3, Duration.ofMillis(30));
    private WebClient webClient;

    @GetMapping("/zip-example/orders/{id}")
    public ZippedOrder zippedOrder(@PathVariable("id") String id) {
        return getOrder(id)
                .flatMap(order -> Flux.fromIterable(order.getOrderLineIds())
                        .flatMap(orderLineId -> {
                                    // What good luck we have! The financials have the same id as the order line!
                                    // Now we don have to rewrite our mock server !
                                    Mono<OrderLine> orderLine = getOrderLine(orderLineId);
                                    Mono<OrderLineFinancials> financials = getOrderLineFinancials(orderLineId);
                                    // So, now we combine these 2 different calls that run in parallel.
                                    // If we also wanted to include the product in the order line we have
                                    // changed getOrderLine(orderLineId) to getOrderLine(orderLineId).flatmap( get product )
                                    // above, and the type would change from orderLine to EnrichedOrderLine.
                                    // Then zip(enrichedOrderLine, financials).
                                    return Mono.zip(orderLine, financials)
                                            .doOnSuccess(tuple -> {
                                                if (tuple == null) {
                                                    log.warn("Mono.zip was successful but at least one of the calls" +
                                                            "did not return a value => no result ");
                                                }
                                            })
                                            // The order param is from the closure in the top flatmap.
                                            .map(tuple -> new ZippedOrderLine(tuple.getT1(), tuple.getT2()));
                                    // .retry(2) // You *could* retry but the individual web calls are already retried. So it is a bit ridiculous.
                                }
                        )
                        .collectList()
                        // Map when there is nothing blocking (like IO or a long computation).
                        // Flatmap if there is, and you should make sure any blocking stuff is on a separate thread pool.
                        // If a method returns Mono, flux, or CompletableFuture that is probably already the case.
                        .map(zippedOrderLines -> new ZippedOrder(order, zippedOrderLines))
                )
                .block(Duration.ofSeconds(5));
    }

    private Mono<Order> getOrder(String id) {
        // order matters. How appropriate :)
        return webClient.get()
                .uri("/orders/{id}", id)
                .retrieve()
                .bodyToMono(Order.class)
                .doOnError(t -> log.info("[non-final] retrieving order id {} failed with message {}", id, t.getMessage()))
                .retryWhen(RETRY_BACKOFF_SPEC)
                .doOnError(t -> log.error("[final] retrieving order id {} has exhausted retries and failed", id, t));
    }

    private Mono<OrderLine> getOrderLine(String id) {
        return webClient.get()
                .uri("/orderlines/{id}", id)
                .retrieve()
                .bodyToMono(OrderLine.class)
                .retryWhen(RETRY_BACKOFF_SPEC);
    }

    private Mono<OrderLineFinancials> getOrderLineFinancials(String id) {
        return webClient.get()
                .uri("/orderlines/{id}", id)// YES I am cheating by using the 'wrong' call
                .retrieve()
                .bodyToMono(OrderLineFinancials.class)
                .retryWhen(RETRY_BACKOFF_SPEC);
    }


}

@Data
@AllArgsConstructor
class OrderLineFinancials {
    private String id;
    private String data;
}

@Data
@AllArgsConstructor
class ZippedOrderLine {
    private OrderLine orderLine;
    private OrderLineFinancials orderLineFinancials;
}

@Data
@AllArgsConstructor
class ZippedOrder {
    private Order order;
    private List<ZippedOrderLine> zippedOrderLines;
}

