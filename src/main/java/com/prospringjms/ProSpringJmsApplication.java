package com.prospringjms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

/**
 * Main Spring Boot application class for Pro Spring JMS showcase
 * with multi-datacenter support for IBM MQ and ActiveMQ Artemis.
 * 
 * The JMS Library will be auto-configured when included as a dependency.
 */
@SpringBootApplication
@EnableJms
public class ProSpringJmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProSpringJmsApplication.class, args);
    }
}