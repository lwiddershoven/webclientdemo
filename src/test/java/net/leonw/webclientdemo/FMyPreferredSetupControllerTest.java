package net.leonw.webclientdemo;


//
// Demonstrates that even reactive applications can benefit from components.
// Agile is about making it cheap and easy to change. Having

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FMyPreferredSetupControllerTest {
    @Mock
    private OrderRetriever orderRetriever;
    @Mock
    private OrderLineRetriever orderLineRetriever;
    @Mock
    private ProductRetriever productRetriever;

    private FMyPreferredSetupController controller;

    @BeforeEach
    void setup() {
        controller = new FMyPreferredSetupController(orderRetriever, orderLineRetriever, productRetriever, new FMyPreferredSetupControllerProperties());
    }

    @Test
    void all_works_fine() {
        var order = new Order("a", List.of("a1"));
        var orderLine = new OrderLine("a1", "p1");
        var product = new Product("p1", "data");

        when(orderRetriever.retrieve("a"))
                .thenReturn(Mono.just(order));
        when(orderLineRetriever.retrieve("a1"))
                .thenReturn(Mono.just(orderLine));
        when(productRetriever.retrieve("p1"))
                .thenReturn(Mono.just(product));

        assertEquals(
                new EnrichedOrder(order, List.of(new EnrichedOrderLine(orderLine, product))),
                controller.getEnrichedOrderList("a")
        );
    }


    @Test
    void an_order_line_is_not_found() {
        var order = new Order("a", List.of("a1", "a2"));
        var orderLine = new OrderLine("a1", "p1");
        var product = new Product("p1", "data");

        when(orderRetriever.retrieve("a"))
                .thenReturn(Mono.just(order));
        when(orderLineRetriever.retrieve("a1"))
                .thenReturn(Mono.just(orderLine));
        when(orderLineRetriever.retrieve("a2"))
                .thenThrow(Exceptions.retryExhausted("test", new RuntimeException("test")));
        when(productRetriever.retrieve("p1"))
                .thenReturn(Mono.just(product));

        // I am not sure this is that much better then try / catch and Exceptions.isRetryException
        // I do understand the philosophy of hiding the implementation of the retry signal
        // but I personally do believe that making the exceptions public would have some advantages
        // together with the disadvantage that people may enumerate them and forget to update the enumeration
        // when something changes.
        assertThrows(Exceptions.retryExhausted("test", new RuntimeException()).getClass(),
                () ->  controller.getEnrichedOrderList("a"));
    }

    @Test
    void use_the_default_product_when_the_product_is_not_found() {
        var order = new Order("a", List.of("a1"));
        var orderLine = new OrderLine("a1", "p1");
        var product = new Product("p1", "data");
        var defaultProduct = new Product("UNKNOWN", "");


        when(orderRetriever.retrieve("a"))
                .thenReturn(Mono.just(order));
        when(orderLineRetriever.retrieve("a1"))
                .thenReturn(Mono.just(orderLine));
        when(productRetriever.retrieve("p1"))
                .thenReturn(Mono.just(defaultProduct));
        // That is the beauty of having product handling in a component - for the caller it looks
        // like any successful call and for testing it fits your happy flow.

        assertEquals(
                new EnrichedOrder(order, List.of(new EnrichedOrderLine(orderLine, defaultProduct))),
                controller.getEnrichedOrderList("a")
        );
    }

}
