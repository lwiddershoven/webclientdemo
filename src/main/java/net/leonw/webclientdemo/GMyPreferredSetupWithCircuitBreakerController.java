package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
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

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

// F , but with a _reactive_ circuit breaker added.
// The normal circuit breakers are blocking and will of course blow up a non-reactive application.
// This is not a reactive application (it uses tomcats threads for processing) and thus could
// use a normal circuit breaker, but I use the reactive version because honestly, I'm curious.

// The stupid G prefix on everything - yes, I should have used packages to group stuff together
// instead of a demo per source file. Ah well, next time.

@Slf4j
@AllArgsConstructor
@RestController
public class GMyPreferredSetupWithCircuitBreakerController {

    private GOrderRetriever orderRetriever;
    private GOrderLineRetriever orderLineRetriever;
    private GProductRetriever productRetriever;
    private GMyPreferredSetupControllerWithCircuitBreakerProperties props;

    @GetMapping("/g-with-circuit-breaker/enriched-orders/{id}")
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
@ConfigurationProperties(prefix = "demo.g")
        // this makes it read the properties from config and environment
class GMyPreferredSetupControllerWithCircuitBreakerProperties {
    private int maxDurationSeconds = 15;
}

@Slf4j
@Component
@RequiredArgsConstructor
class GOrderRetriever {
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(3, Duration.ofMillis(100));
    @NonNull
    private WebClient webClient;
    @NonNull
    private GMyPreferredSetupControllerWithCircuitBreakerProperties props;
    @NonNull
    private ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private ReactiveCircuitBreaker circuitBreaker;

    @PostConstruct
    public void setup() {
        // From a design perspective I think I prefer everything order retrieving related together,
        // and not instantiate the circuit breakers in a generic Configuration class.

        // However, instantiating all circuit breakers in a CircuitBreakerConfiguration, or have
        // an OrderRetrieverConfiguration class with its own properties also makes sense.
        // I obviously did not choose that.
        circuitBreaker = reactiveCircuitBreakerFactory.create("orderRetriever");
    }

    // Open questions at this point:
    // What needs to be configured
    // Does this retry ? How do the retries interact with the webClient retries?
    // How does the timeout config work with webClient?
    // Can I have/ should I have more that 1 reactiveCircuitBreakerFactory
    // Which autoconfig is applied to reactiveCircuitBreakerFactory and can I copy this for other reactiveCircuitBreakerFactory if I need them?
    // Does this need threads, and if so, where are they configured and can they realistic ally run out

    // Observations.
    // 1. It seems like the default timeout is 1 second, and thus the request failed:
    //   java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 1000ms in 'circuitBreaker' (and no fallback has been configured)] with root cause
    //   java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 1000ms in 'circuitBreaker' (and no fallback has been configured)] with root cause
    //   In my experience 1 second is very short for systems that do not face paying customers but instead employees, or people doing complex jobs
    //   Especially when some system is doing some form of sequential aggregation (like GraphQL servers or the enriched endpoint here).
    //
    //
    public Mono<Order> retrieve(String orderId) {
        return circuitBreaker.run(
                webClient.get()
                        .uri("/orders/{id}", orderId)
                        .retrieve()
                        .bodyToMono(Order.class)
                        // Not the entire stacktrace for the info level message
                        .doOnError(t -> log.info("[non-final] retrieving order {} failed with message {}", orderId, t.getMessage()))
                        .retryWhen(RETRY_SPEC)
                        .doOnError(t -> log.error("[final] retrieving order order {} has exhausted retries and failed", orderId, t))
                , throwable -> {
                    // metrics, plus
                    log.warn("retrieval finished with an error", throwable);
                    // either return a fallback / default option Mono, or
                    // preserve stacktrace, and handling Error is probably a bit too correct,
                    if (throwable instanceof Error) {
                        throw (Error) throwable;
                    } else if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    } else {
                        throw new RuntimeException(throwable);
                    }
                }
        );
    }
}

@Slf4j
@Component
@AllArgsConstructor
class GOrderLineRetriever {
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
class GProductRetriever {
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