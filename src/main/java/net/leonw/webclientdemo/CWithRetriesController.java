package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// The basic flow, with retries
// Note that 404 is regarded as failure.
@Slf4j
@AllArgsConstructor
@RestController
public class CWithRetriesController {

    // Instead of webclient so you can clearly see how we build upon the previous iteration.
    // And yes, injecting controllers is ugly.
    private BRefactoredEnrichedOrderController previousController;

    @GetMapping("/with-retries-example/enriched-orders/{id}")
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
                // .retry(3) // NOT HERE: this will restart getOrder even if the exception was in getProduct or map.
                .block(Duration.of(15, ChronoUnit.SECONDS)); // Larger timeout since retries can cost.
    }

    private Mono<Order> getOrder(String id) {
        // 4 attempts: the initial attempt + 3 retries. Each immediate when failed
        // This may be good for dealing with intermittent network failures
        return previousController.getOrder(id)
                .retry(3);
    }

    private Mono<OrderLine> getOrderLine(String id) {
        // 4 attempts, separated by 500 ms. This helps dealing with starting applications
        // which may happen ion Kubernetes. 500 ms is probably a bit much.
        return previousController.getOrderLine(id)
                .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(500)));
    }

    private Mono<Product> getProduct(String id) {
        // An exponentially increasing delay. Probably the best policy if you deal with
        // network twitter (which is probably solved on the next attemp), to starting
        // applications and overloaded applications.
        return previousController.getProduct(id)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

}
