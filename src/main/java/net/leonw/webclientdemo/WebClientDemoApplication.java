package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Configuration
@SpringBootApplication
public class WebClientDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebClientDemoApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8080").build();
    }

}

@Slf4j
@Component
class TimingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        Instant start = Instant.now();
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            HttpServletRequest request = (HttpServletRequest) servletRequest; // Yes. No instanceof. I dare to do this
            log.info("{} {} took {} ms", request.getMethod(), request.getRequestURI(), Duration.between(start, Instant.now()).toMillis());
        }
    }
}


@Slf4j
@RestController
@AllArgsConstructor
class Demo {

    private WebClient webClient;

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
                            if (throwable instanceof WebClientResponseException.NotFound) {
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
