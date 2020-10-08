package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// The basic flow, with retries, 404 handling and error handling
// getProduct views 404 as a reason to insert a Default Product.
// The retries from the previous controller have been copied as the relative placing of
// the error handling code is significant.
@Slf4j
@AllArgsConstructor
@RestController
public class DWithErrorHandlingController {

    // Instead of webclient so you can clearly see how we build upon the previous iteration.
    // And yes, injecting controllers is ugly.
    private BRefactoredEnrichedOrderController previousController;

    @GetMapping("/decent-error-handling-example/enriched-orders/{id}")
    public EnrichedOrder getEnrichedOrderList3(@PathVariable("id") String id) {
        return getOrder(id)
                .flatMap(order ->
                        Flux.fromIterable(order.getOrderLineIds())
                                .flatMap(orderLineId -> getOrderLine(orderLineId))
                                .flatMap(orderLine -> getProduct(orderLine.getProductId())
                                        .map(product -> new EnrichedOrderLine(orderLine, product)))
                                .collectList()
                                .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .doOnSuccess(completedEnrichedOrder -> log.info("Success retrieving id {}: {}", id, completedEnrichedOrder))
                .doOnError(throwable -> log.warn("Retrieving id {} failed", id, throwable))
                .block(Duration.of(15, ChronoUnit.SECONDS)); // Larger timeout since retries can cost.
    }



    private Mono<Order> getOrder(String id) {
        // order matters. How appropriate :)
        return previousController.getOrder(id)
                // Every time a failure occurs the following line is logged. This could also include sending metrics of course!
                .doOnError(t -> log.info("[non-final] retrieving order id {} failed with message {}", id, t.getMessage()))
                .retry(3)
                // At most once; only when even after 3 retries there is still nu success.
                .doOnError(t -> log.error("[final] retrieving order id {} has exhausted retries and failed", id, t));
    }

    private Mono<OrderLine> getOrderLine(String id) {
        return previousController.getOrderLine(id)
                .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(500)));
        // Do be aware that it is possible to create a retry spec with predicate ; you can for instance not
        // retry on a 5xx server error if you know that is something that will never recover.
    }

    private Mono<Product> getProduct(String id) {
        return previousController.getProduct(id)
                .onErrorReturn(
                        throwable -> {
                            // Deal with 404. I think this method sucks but I don't know of a better way
                            if (throwable instanceof WebClientResponseException.NotFound) {
                                log.warn("Call for product id {} returned 404. replacing result with default value", id);
                                return true; // YES return the default
                            } else {
                                return false; // NO rethrow the error
                            }
                        },
                        new Product("default", "unknown product")
                )
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                // If you place onErrorReturn here it will get a 404, and retry, and only the 404 of the last
                // retry will result in the default. That is waste. If you say 404 is not an error you need to
                // deal with it before retry.
                .doOnError(t -> log.error("Could not retrieve product with id {}", id, t));
    }
}
