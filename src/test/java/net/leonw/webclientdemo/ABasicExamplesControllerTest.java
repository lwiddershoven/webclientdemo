package net.leonw.webclientdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Junit 5 setup
// Spring boot and junit 5 do require some pom exclusions to work. https://developer.okta.com/blog/2019/03/28/test-java-spring-boot-junit5
// https://www.baeldung.com/mockito-junit-5-extension


// Testing with webclient
// https://www.baeldung.com/spring-mocking-webclient
// https://stackoverflow.com/questions/45301220/how-to-mock-spring-webflux-webclient

// I prefer junit testing here.
// I will try to use the exchange function. That has the advantage that you don't have to deal
// with the various steps of the webClient, but the big disadvantage that you have to setup
// the webclient json (de)serializers. Which is normally done by Spring.
// On the other hand, you can paste in "real" json responses from dependencies and see whether they
// meet expectation.

// The big advantage to me of using exchangeFunction is that you mock only 1 place - the integration point
// with the outside world. The disadvantage is that there is no spring boot autoconfigure and as such the
// configuration can be different in unexpected places.

@ExtendWith(MockitoExtension.class)
class ABasicExamplesControllerTest {
    private  ABasicExamplesController controller;

    @Mock
    private ExchangeFunction exchangeFunction;


    @BeforeEach
    void init() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        controller = new ABasicExamplesController(webClient);
    }

    @Test
    void fail_if_the_exchange_function_does_not_return_a_value() {
        try {
            controller.getOrder("1");
            fail("Without the exchange function having a return value this is not supposed to succeed");
        } catch (NullPointerException e) {
            // ok
        }
    }

    @Test
    void get_order_succeeds() {
        Order expected = new Order("123", Collections.emptyList());
        // From:  https://stackoverflow.com/questions/45301220/how-to-mock-spring-webflux-webclient
        // Note: If you get null pointer exceptions related to publishers on the when call, your IDE might have imported Mono.when instead of Mockito.when.
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(createMockResponse(expected));
        Order retrieved = controller.getOrder("1");
        assertEquals(expected, retrieved);
    }


    @Test
    void get_order_404_throws_NotFound() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(createMock404Response());
        assertThrows(WebClientResponseException.NotFound.class, () ->  controller.getOrder("1"));
    }


    @Test
    void get_multiple_orders_succeed() {
        List<Order> expected = List.of(
                new Order("a", List.of("a1", "a2")),
                new Order("b", List.of("b1", "b2")),
                new Order("c", List.of("c1", "c2"))
        );

        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(createMockResponse(expected.get(0)))
                .thenReturn(createMockResponse(expected.get(1)))
                .thenReturn(createMockResponse(expected.get(2)));

        // Do note that there need to be 3. Not with the same ids as I do not use
        // the Mockito "Answer" for matching. But Flux.fromIterable will trigger a call
        // per input id and so there must be 3 ids here.
        List<Order> retrieved = controller.getMultipleOrders(Set.of("x", "y", "z"));

        assertEquals(expected, retrieved);
    }

    @Test
    void get_multiple_orders_with_Answer_fails_on_wrong_id() {
        // Just a demo on how to set up answers. That may be handy with more complex
        // tests where order matters. (And usually reactive does not maintain order unless asked explicitly).
        List<Order> expected = List.of(
                new Order("a", List.of("a1", "a2")),
                new Order("b", List.of("b1", "b2")),
                new Order("c", List.of("c1", "c2"))
        );

        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenAnswer((Answer<Mono<ClientResponse>>) invocationOnMock -> {
                    // This should probably be static in the test class. For demo purposes inlined.
                    var  ordersPathPattern = PathPatternParser.defaultInstance.parse("/orders/{id}");
                    var request = (ClientRequest)invocationOnMock.getArgument(0);

                    @SuppressWarnings("ConstantConditions") var orderId = ordersPathPattern
                            .matchAndExtract( PathContainer.parsePath(request.url().getPath()))
                            .getUriVariables()
                            .get("id");

                    return expected.stream()
                            .filter(o -> o.getId().equals(orderId))
                            .map(this::createMockResponse)
                            .findFirst()
                            .orElseThrow();
                });
        // Do change the id to something else to see what happens
        List<Order> retrieved = controller.getMultipleOrders(Set.of("a", "b", "c"));

        // assertEquals also compares ordering
        assertThat(expected.equals(retrieved));
    }


    private <T> Mono<ClientResponse> createMockResponse(T value) {
        String valueAsString;
        try {
            valueAsString = new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // Junit test will fail without bothering me
        }

        return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("content-type", "application/json")
                        .body(valueAsString)
                        .build()
        );
    }

    private  Mono<ClientResponse> createMock404Response() {
        return Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND)
                        .header("content-type", "application/json")
                        .build()
        );
    }

}