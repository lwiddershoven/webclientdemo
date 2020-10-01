package net.leonw.webclientdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Junit 5 setup
// Spring boot and junit 5 do require some pom exclusions to work. https://developer.okta.com/blog/2019/03/28/test-java-spring-boot-junit5
// https://www.baeldung.com/mockito-junit-5-extension

// Junit test vs Integration test - This is a junit test.
// In this test we mock the WebClient entirely.
// On the one hand this is a bit of a pain as it requires a fair amount of meaningless boilerplate
// but on the other hand everything is better than testing way too much, like with a 'real' webserver.
// Integration tests are useful too of course, but they serve a different purpose.

// reactive code does not really stimulate normalized components; it feels more natural to
// have one large flow. The stuff inside flatmaps however *can* be made into injectable
// spring components and thereby individually tested, and more importantly, mocked away in the
// primary flow.
// So where I have methods is the refactored controller, they could have been components.
// It all depends on the complexity of the flow. HFor hello world this is overkill, for some real
// flows adding features afterwards to the flow would be really helped my moving parts to their own
// tested components.


@ExtendWith(MockitoExtension.class)
class ABasicExamplesControllerWithMockedWebClientTest {

    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private  ABasicExamplesController controller;


    @Test
    void fail_if_the_exchange_function_does_not_return_a_value() {
        try {
            controller.getOrder("1");
            fail("Without a return value for the mock this will fail");
        } catch (NullPointerException e) {
            // ok
        }
    }

    @Test
    void succeed_when_an_order_is_returned() {
        // https://www.baeldung.com/spring-mocking-webclient

        Mono<Order> expected = Mono.just(new Order("123", Collections.emptyList()));

        when(webClient.get())
                .thenReturn(requestHeadersUriSpec);
        // I don't really feel the need to verify the URL here.
        when(requestHeadersUriSpec.uri(anyString(), anyString()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Order.class))
                .thenReturn(expected);

        Order retrieved = controller.getOrder("1");

        assertEquals(expected.block(), retrieved);
    }

    @Test
    void succeed_when_an_order_is_returned_refactored() {
        // https://www.baeldung.com/spring-mocking-webclient

        Order expected = new Order("123", Collections.emptyList());
        setupWebClient1(expected);

        Order retrieved = controller.getOrder("1");

        assertEquals(expected, retrieved);
    }


    // For testing code this is hopefully acceptable.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> void setupWebClient1(T value) {
        Class valueClass = value.getClass();

        when(webClient.get())
                .thenReturn(requestHeadersUriSpec);
        // I don't really feel the need to verify the URL here.
        when(requestHeadersUriSpec.uri(anyString(), anyString()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);
        when(responseSpec.bodyToMono(valueClass))
                .thenReturn(Mono.just(value));
    }





}