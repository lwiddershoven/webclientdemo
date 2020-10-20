package net.leonw.webclientdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

// It seems there is some very nice build in webserver for integration testing
// so lets also experiment with that

// I don't favor integration tests, but having something that starts the spring context in various
// profiles to make sure everything is wired is useful. And checking whether the filters and
// encoders are wired properly is also useful. And no, this test does not do that. This test just
// verifies the webClient configuration (retry, error handlers).

// It's just a shame integration tests take forever which demotivates running them often.

// Because we refactored out the network interaction to their own classes we only need to use a mock
// webserver to test these; the possibly more complex flows of the controller can be done with mocks.
// You do have to check how errors thrown by the xxxRetriever are handled by the main flow though by
// having the mocks throw the same exceptions.

// Do note that testing the error handling takes forever as all the retries are done, including the
// waiting in between. If you do it wrong the tests will never complete and just keep running !

public class FOrderRetrieverIntegrationTest {
    public static MockWebServer mockBackEnd;
    private OrderRetriever retriever;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws IOException {

        // Note; in the G test I moved this to @BeforeEach and changed @AfterAll to @AfterEach.
        // The backend is stateful and unrequested mockResponses will just be processed by the next
        // test, which is confusing.

        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
    }

    @BeforeEach
    void initialize() {
        var baseUrl = String.format("http://localhost:%s",
                mockBackEnd.getPort());
        var webClient = WebClient.builder().baseUrl(baseUrl).build();
        retriever = new OrderRetriever(webClient);
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @Test
    void happy_case_all_ok() throws JsonProcessingException {
        var mockOrder = new Order("123", List.of("abc", "def"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/json"));

        Mono<Order> orderMono = retriever.retrieve("1");

        StepVerifier.create(orderMono)
                .expectNextMatches(order -> order.getId().equals(mockOrder.getId()))
                .verifyComplete();
    }


    // @Test
    void wrong_content_type_test_will_not_finish() throws JsonProcessingException {
        var mockOrder = new Order("123", List.of("abc", "def"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/xxx"));

        Mono<Order> orderMono = retriever.retrieve("1");

        StepVerifier.create(orderMono)
                .expectNextMatches(order -> order.getId().equals(mockOrder.getId()))
                .verifyComplete();
    }

    @Test
    void wrong_content_type_will_fail_after_retries() throws JsonProcessingException {
        var mockOrder = new Order("123", List.of("abc", "def"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/xxx"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/xxx"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/xxx"));
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockOrder))
                .addHeader("Content-Type", "application/xx" +
                        "x"));

        Mono<Order> orderMono = retriever.retrieve("1");

        StepVerifier.create(orderMono)
                .expectErrorMatches(t -> Exceptions.isRetryExhausted(t) && (t.getCause() instanceof UnsupportedMediaTypeException))
//                .expectErrorMatches(t -> Exceptions.isRetryExhausted(t) && (t.getCause() instanceof NullPointerException)) // I did verify the test works :)
//                .verifyComplete(); This has no timeout - do not use
                .verify(Duration.ofSeconds(30));
    }

}
