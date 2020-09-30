package net.leonw.webclientdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

// Junit 5 setup
// Spring boot and junit 5 do require some pom exclusions to work. https://developer.okta.com/blog/2019/03/28/test-java-spring-boot-junit5
// https://www.baeldung.com/mockito-junit-5-extension


// Testing with webclient
// https://www.baeldung.com/spring-mocking-webclient
// https://stackoverflow.com/questions/45301220/how-to-mock-spring-webflux-webclient

// I prefer junit testing here.
// The fake server is nice for integration tests. Which this should not be.


@ExtendWith(MockitoExtension.class)
class ABasicExamplesControllerTest {

    @Test
    void doSomething() {
    }
}