package com.prospringjms.test;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit tests for the JMS Library module.
 * Tests core functionality without requiring Spring Boot application context.
 */
public class JmsLibraryBasicTest {
    
    @Test
    public void testJmsLibraryPropertiesCreation() {
        JmsLibraryProperties properties = new JmsLibraryProperties();
        assertNotNull(properties);
        // Don't test getDatacenters() as it might be null until initialized by Spring
    }
    
    @Test
    public void testJmsLibraryExceptionCreation() {
        String message = "Test error message";
        JmsLibraryException exception = new JmsLibraryException(message);
        
        assertEquals(message, exception.getMessage());
        assertNotNull(exception);
    }
    
    @Test
    public void testJmsLibraryExceptionWithDatacenter() {
        String message = "Test error";
        String datacenter = "us-east-1";
        String operation = "sendMessage";
        
        JmsLibraryException exception = new JmsLibraryException(message, datacenter, operation);
        
        assertEquals(message, exception.getMessage());
        assertEquals(datacenter, exception.getDatacenter());
        assertEquals(operation, exception.getOperation());
    }
}