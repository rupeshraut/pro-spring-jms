package com.prospringjms;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the Pro Spring JMS application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ProSpringJmsApplicationTests {

    @Test
    void contextLoads() {
        // Just test that the application context loads successfully
    }
}