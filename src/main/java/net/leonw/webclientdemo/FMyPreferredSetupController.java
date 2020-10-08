package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// Refactored to component based so mocking of external dependencies is easy.
// In this case I added the classes to this file. That is not common in Java though.


@Slf4j
@AllArgsConstructor
@RestController
public class FMyPreferredSetupController {

    private OrderRetriever orderRetriever;
    private OrderLineRetriever orderLineRetriever;
    private ProductRetriever productRetriever;
    private FMyPreferredSetupControllerProperties props;

    @GetMapping("/f-ultimate-example-perhaps/enriched-orders/{id}")
    public EnrichedOrder getEnrichedOrderList(@PathVariable("id") String id) {
        try {
            return orderRetriever.retrieve(id)
                    .flatMap(order ->
                            Flux.fromIterable(order.getOrderLineIds())
                                    .flatMap(orderLineId -> orderLineRetriever.retrieve(orderLineId))
                                    .flatMap(orderLine -> productRetriever.retrieve(orderLine.getProductId())
                                            .map(product -> new EnrichedOrderLine(orderLine, product))
                                    )
                                    .collectList()
                                    .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                    )
                    .doOnSuccess(completedEnrichedOrder -> log.info("Success retrieving enriched order {}: {}", id, completedEnrichedOrder))
                    .doOnError(throwable -> log.warn("Retrieving enriched order {} failed", id, throwable))
                    .block(Duration.of(props.getMaxDurationSeconds(), ChronoUnit.SECONDS)); // Larger timeout since retries can cost.
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                // Do not print the entire stack trace in the log
                log.warn("Call for enriched order {} aborted: {}", id, e.getMessage());
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Please try again later.");
            }
            throw e;
        }
    }
}

@Data
@Configuration // This makes it a bean in the context, i.e. injectable
@ConfigurationProperties(prefix = "demo.f") // this makes it read the properties from config and environment
class FMyPreferredSetupControllerProperties {
    private int maxDurationSeconds = 15;
}

@Slf4j
@Component
@AllArgsConstructor
class OrderRetriever {
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(3, Duration.ofMillis(100));
    private WebClient webClient;

    public Mono<Order> retrieve(String orderId) {
        return webClient.get()
                .uri("/orders/{id}", orderId)
                .retrieve()
                .bodyToMono(Order.class)
                // Not the entire stacktrace for the info level message
                .doOnError(t -> log.info("[non-final] retrieving order {} failed with message {}", orderId, t.getMessage()))
                .retryWhen(RETRY_SPEC)
                .doOnError(t -> log.error("[final] retrieving order order {} has exhausted retries and failed", orderId, t));
    }
}

@Slf4j
@Component
@AllArgsConstructor
class OrderLineRetriever {
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(3, Duration.ofMillis(100));
    private WebClient webClient;

    public Mono<OrderLine> retrieve(String orderLineId) {
        return webClient.get()
                .uri("/orderlines/{id}", orderLineId)
                .retrieve()
                .bodyToMono(OrderLine.class)
                .doOnError(t -> log.info("[non-final] retrieving orderLine {} failed with message {}", orderLineId, t.getMessage()))
                .retryWhen(RETRY_SPEC)
                .doOnError(t -> log.error("[final] retrieving orderLine {} has exhausted retries and failed", orderLineId, t));
    }
}


@Slf4j
@Component
@AllArgsConstructor
class ProductRetriever {
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(3, Duration.ofMillis(100));
    public static final Product UNKNOWN_PRODUCT = new Product("", "");
    private WebClient webClient;

    public Mono<Product> retrieve(String productId) {
        return webClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .onErrorReturn(
                        throwable -> {
                            // We basically move the error signal back to the happy flow
                            if (throwable instanceof WebClientResponseException.NotFound) {
                                log.warn("Call for product id {} returned 404. replacing result with default value", productId);
                                return true; // YES return the default
                            } else {
                                return false; // NO rethrow the error
                            }
                        },
                        UNKNOWN_PRODUCT // Maybe null would work but reactive flows really don't like null. So then optional would be an option.
                )
                .doOnError(t -> log.info("[non-final] retrieving product {} failed with message {}", productId, t.getMessage()))
                .retryWhen(RETRY_SPEC)
                .doOnError(t -> log.error("[final] retrieving product {} has exhausted retries and failed", productId, t));
    }

}