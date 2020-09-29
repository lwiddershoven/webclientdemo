package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Slf4j
@AllArgsConstructor
@RestController
public class BRefactoredEnrichedOrderController {
    private WebClient webClient;

    @GetMapping("/refactored-examples/enriched-orders/{id}")
    public EnrichedOrder getEnrichedOrderList(@PathVariable("id") String id) {
        EnrichedOrder enrichedOrder = getOrder(id)
                .flatMap(order ->
                        Flux.fromIterable(order.getOrderLineIds())
                                .flatMap(this::getOrderLine)
                                .flatMap(orderLine -> getProduct(orderLine.getProductId()).map(product -> new EnrichedOrderLine(orderLine, product)))
                                .collectList()
                                .map(enrichedOrderLines -> new EnrichedOrder(order, enrichedOrderLines))
                )
                .block(Duration.of(8, ChronoUnit.SECONDS));

        return enrichedOrder;
    }


    //
    // Methods are public as we will build on them in the next controllers
    // This is *not* recommended in production code and even though I think it helps show the difference it
    // is at the same time embarrassing to commit such code.
    //
    public Mono<Order> getOrder(String id) {
        return webClient.get()
                .uri("/orders/{id}", id)
                .retrieve()
                .bodyToMono(Order.class);
    }

    public Mono<OrderLine> getOrderLine(String orderLineId) {
        return webClient.get()
                .uri("/orderlines/{id}", orderLineId)
                .retrieve()
                .bodyToMono(OrderLine.class);
    }

    public Mono<Product> getProduct(String id) {
        return webClient.get()
                .uri("/products/{id}", id)
                .retrieve()
                .bodyToMono(Product.class);
    }
}
