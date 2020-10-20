package net.leonw.webclientdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.fail;

// Play a bit with the circuit breaker.

// This is an integration test because I want to test the full stack.

class GOrderRetrieverCircuitBreakerIntegrationTest {
    public static MockWebServer mockBackEnd;
    private WebClient webClient;
    private ObjectMapper objectMapper = new ObjectMapper();



    @BeforeEach
    void initialize() throws IOException {
        // Moved the mockBackEnd tpo BeforeEach instead of beforeAll as it remembers state;
        // like all the queued requests.
        // Starting each test with a guaranteed empty mock response queue seems safer to me.
        // It certainly is easier to diagnose failing tests this way.
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();

        var baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @Test
    void the_default_circuit_breaker_timeout_is_one_second() {
        // 4 responses as there could be 4 attempts (even if we know it *should* abort the very first attempt already
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(2000, TimeUnit.MILLISECONDS).setResponseCode(200));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(2000, TimeUnit.MILLISECONDS).setResponseCode(200));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(2000, TimeUnit.MILLISECONDS).setResponseCode(200));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(2000, TimeUnit.MILLISECONDS).setResponseCode(200));

        var circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory();
        var retriever = new GOrderRetriever(webClient, circuitBreakerFactory);
        retriever.setup(); // Normally Spring would do this.

        // You MUST block otherwise nothing happens (no subscription).
        // and lets add a timeout to make sure it ends eventually no matter how stupid my bugs :)
        try {
            retriever.retrieve("123")
                    .block(Duration.ofSeconds(10));
            fail("This should have thrown a timeout exception");
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof TimeoutException)) {
                var cause = e.getCause() != null ? e.getCause().getClass() : e.getCause() + " without cause";
                fail("There was a " + cause + ", not a TimeoutException");
            } else if (!e.getCause().getMessage().equals("Did not observe any item or terminal signal within 1000ms in 'circuitBreaker' (and no fallback has been configured)")) {
                fail("There was the expected timeout exception, but not with the expected message.");
            }
        }
    }

    @Test
    void all_retries_together_need_to_fit_in_timeout() {
        // Default circuit breaker timeout: 1000 ms
        // Every attempt: 200 ms  (response code: 500 so it will retry)
        // There are 3 retries (4 attempts) in the GOrderRetriever retry spec
        // and 4 x 200 + a few hundred ms  due to backoff do not fit into 1000 ms

        // I chose 200 as that gave 2 attempts on my machine

        // 4 responses as there should be 4 attempts.
        // If you do NOT give 4 responses it will fail the same way - but for another reason
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(200, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(200, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(200, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(200, TimeUnit.MILLISECONDS).setResponseCode(500));
        var circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory();
        var retriever = new GOrderRetriever(webClient, circuitBreakerFactory);
        retriever.setup(); // Normally Spring would do this.

        try {
            retriever.retrieve("123")
                    .block(Duration.ofSeconds(10));
            fail("This should have thrown a timeout exception");
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof TimeoutException)) {
                var cause = e.getCause() != null ? e.getCause().getClass() : e.getCause() + " without cause";
                fail("There was a " + cause + ", not a TimeoutException");
            } else if (!e.getCause().getMessage().equals("Did not observe any item or terminal signal within 1000ms in 'circuitBreaker' (and no fallback has been configured)")) {

                // We expect this message because that poor webClient is still busy retrying , and in the mean time
                // the supervising circuitbreaker is losing patience and just aborts that and walks away.

                fail("There was the expected timeout exception, but not with the expected message.");
            }
        }
    }


    @Test
    void buggy_test_very_fast_response_but_still_timeout() {

        // We configure 1 response, and webClient is configured to retry.
        // mockBackEnd does NOT re-use the response.
        // So that response we added is returned once, and any other response will need to come
        // from the webClient timeouts.
        // The circuit breaker is not that patient and will abort the process because there simply is
        // no response forthcoming.
        // Note that mockBackEnd also does not throw an error if requests come in for which no
        // response is configured.

        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(5, TimeUnit.MILLISECONDS).setResponseCode(500));
        var circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory();
        var retriever = new GOrderRetriever(webClient, circuitBreakerFactory);
        retriever.setup(); // Normally Spring would do this.

        try {
            retriever.retrieve("123")
                    .block(Duration.ofSeconds(10));
            fail("This should have thrown a timeout exception");
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof TimeoutException)) {
                var cause = e.getCause() != null ? e.getCause().getClass() : e.getCause() + " without cause";
                fail("There was a " + cause + ", not a TimeoutException");
            } else if (!e.getCause().getMessage().equals("Did not observe any item or terminal signal within 1000ms in 'circuitBreaker' (and no fallback has been configured)")) {
                fail("There was the expected timeout exception, but not with the expected message.");
            }
        }
    }

    @Test
    void within_the_timeout_the_original_exception_is_passed_on() {

        // WARNING: I had to change the timeout on the circuit breaker as the retry policy with 4 attempts (3 retries)
        // did not fit in 1 second

        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(10, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(10, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(10, TimeUnit.MILLISECONDS).setResponseCode(500));
        mockBackEnd.enqueue(new MockResponse().setHeadersDelay(10, TimeUnit.MILLISECONDS).setResponseCode(500));


        // Copied from ReactiveResilience4JCircuitBreakerFactory

        // From the design of the customizer it is however apparent that the circuit breaker ids are supposed to be
        // public, or the circuit breaker factory as passed by Spring is configured inside GOrderRetriever itself
        // for the id of GOrderRetriever.
        // It feels sort of wrong to change the state of a bean that gets injected by Spring (the factory) but
        // that is the design. I think I would have preferred a design that does not so visibly manages mutable state.
        //
        // I *am* fairly sure there are excellent reasons for this design.

        Function<String, Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration> circuitBreakerConfig =
                id -> (new Resilience4JConfigBuilder(id))
                        .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                        .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
                        .build();

        var circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory();
        circuitBreakerFactory.configureDefault(circuitBreakerConfig); // I change the default config otherwise I have to know the ids.

        var retriever = new GOrderRetriever(webClient, circuitBreakerFactory);
        retriever.setup(); // Normally Spring would do this.

        try {
            retriever.retrieve("123")
                    .block(Duration.ofSeconds(10));
            fail("This should have thrown a InternalServerError exception");
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof WebClientResponseException.InternalServerError)) {
                fail("There was a " + e + ", not a InternalServerError exception");
            }
        }
    }

}