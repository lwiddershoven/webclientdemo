package net.leonw.webclientdemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.Exceptions;
import java.util.List;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// VERY IMPORTANT

// Do not forget to .block() (or subscribe) because otherwise nothing happens. Duh.


// Fairly simple test for this component with a clear scope
@ExtendWith(MockitoExtension.class)
public class FOrderRetrieverTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private OrderRetriever orderRetriever;


    @BeforeEach
    void init() {
        var webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        orderRetriever = new OrderRetriever(webClient);
    }

    @Test
    void not_found_throws_exception() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(TestUtils.createMock404Response())
                .thenReturn(TestUtils.createMock404Response())
                .thenReturn(TestUtils.createMock404Response())
                .thenReturn(TestUtils.createMock404Response());

        try {
            // That nice functional junit5 assertThrows is lovely,
            // unfortunately the projectreactor people don't make their exceptions public
            orderRetriever.retrieve("a").block();
            fail("This is supposed to end with an IllegalStateException");
        } catch (IllegalStateException e) {
            if (!Exceptions.isRetryExhausted(e)) {
                fail("Expected retry exhausted exception");
            }
        }
    }

    @Test
    void returns_a_valid_order() {
        Order expected = new Order("a", List.of("a1", "a2"));
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(TestUtils.createMockResponse(expected));
        assertEquals(expected, orderRetriever.retrieve("a").block());
    }


    @Test
    void server_exception_throws_exception() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(TestUtils.createMockStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(TestUtils.createMockStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(TestUtils.createMockStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(TestUtils.createMockStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR));

        try {
            // That nice functional junit5 assertThrows is lovely,
            // unfortunately the projectreactor people don't make their exceptions public
            orderRetriever.retrieve("a").block();
            fail("This is supposed to end with an IllegalStateException");
        } catch (IllegalStateException e) {
            if (!Exceptions.isRetryExhausted(e)) {
                fail("Expected retry exhausted exception");
            }
        }
    }
}
