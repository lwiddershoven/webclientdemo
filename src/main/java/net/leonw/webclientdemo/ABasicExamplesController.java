package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

// These examples do not have retries or error handling.
// These examples show the basic happy flow for demonstration purposes only
// Log statements are added
@Slf4j
@AllArgsConstructor
@RestController
public class ABasicExamplesController {
    private WebClient webClient;

    @GetMapping("/basic-examples/orders/{id}")
    public Order getOrder(@PathVariable("id") String id) {
        Order order = webClient.get().uri("/orders/{id}", id).retrieve().bodyToMono(Order.class).block();
        return order;
    }

    // You can add ids by ?id=1&id=2&... or ?id=1,2,3,4
    @GetMapping("/basic-examples/orders/")
    public List<Order> getMany(@RequestParam("id") Set<String> ids) {
        List<Order> orders = Flux.fromIterable(ids)
                .flatMap(id -> webClient.get().uri("/orders/{id}", id).retrieve().bodyToMono(Order.class).retry(3)) // Flatmap: Ditch the Mono 'wrapper'
                .collectList()
                .block(Duration.of(3, ChronoUnit.SECONDS));
        // One requests takes 2 seconds ....
        // Because this is automatically parallel the total time is still 2 seconds. Using only 1 or 2 threads.
        return orders;
    }

    @GetMapping("/basic-examples/enriched-orders/{id}")
    public EnrichedOrder getEnrichedOrderList(@PathVariable("id") String id) {
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
                .block(Duration.of(8, ChronoUnit.SECONDS));
        // getOrder, then get parallel the items, and when an item has been retrieved get the product.
        // With 2 s pr call that means the minimum time is 6 seconds when everything possible runs in parallel
        return enrichedOrder;
    }
}
