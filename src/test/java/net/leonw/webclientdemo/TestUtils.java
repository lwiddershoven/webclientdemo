package net.leonw.webclientdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

public class TestUtils {
    public static <T> Mono<ClientResponse> createMockResponse(T value) {
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

    public  static Mono<ClientResponse> createMock404Response() {
        return Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND)
                        .header("content-type", "application/json")
                        .build()
        );
    }
}
