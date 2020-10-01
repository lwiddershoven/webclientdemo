package net.leonw.webclientdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
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
                .thenReturn(createMockResponse());
        Order retrieved = controller.getOrder("1");
        assertEquals(expected, retrieved);
    }


    private Mono<ClientResponse> createMockResponse() {
        return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("content-type", "application/json")
                        .body("{ \"id\" : \"123\", \"orderLineIds\": []}")
                        .build()
        );
    }

}