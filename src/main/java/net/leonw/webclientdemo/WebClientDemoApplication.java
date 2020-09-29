package net.leonw.webclientdemo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Configuration
@SpringBootApplication
public class WebClientDemoApplication {

    public static void main(String[] args) {
//        BlockHound.install();
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
