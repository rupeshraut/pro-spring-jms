package com.prospringjms.messaging.test;

import com.prospringjms.messaging.VetroMessageProcessor;
import com.prospringjms.messaging.examples.OrderVetroProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for VETRO message processing functionality.
 * 
 * Demonstrates how to test the Template Method pattern implementation
 * and verify each step of the VETRO pipeline.
 */
@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig
public class VetroMessageProcessorTest {
    
    private static final Logger logger = LoggerFactory.getLogger(VetroMessageProcessorTest.class);
    
    private OrderVetroProcessor processor;
    
    @BeforeEach
    void setUp() {
        processor = new OrderVetroProcessor();
        // In a real test, you'd inject mocked dependencies
    }
    
    @Test
    void testSuccessfulOrderProcessing() {
        // Given
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("CUST001");
        order.setProductId("PROD001");
        order.setQuantity(2);
        order.setAmount(199.98);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-001", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        assertEquals(VetroMessageProcessor.ProcessingStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getValidationResult());
        assertTrue(result.getValidationResult().isValid());
        assertNotNull(result.getEnrichedPayload());
        assertNotNull(result.getTransformedPayload());
        assertNotNull(result.getRoutingDecision());
        assertNotNull(result.getOperationResult());
    }
    
    @Test
    void testValidationFailure() {
        // Given - Invalid order (missing customer ID)
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setProductId("PROD001");
        order.setQuantity(2);
        order.setAmount(199.98);
        // Missing customerId
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-002", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        assertEquals(VetroMessageProcessor.ProcessingStatus.VALIDATION_FAILED, result.getStatus());
        assertFalse(result.getValidationResult().isValid());
        assertTrue(result.getValidationResult().getErrors().containsKey("customerId"));
    }
    
    @Test
    void testHighValueOrderHandling() {
        // Given - High value order
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("CUST001");
        order.setProductId("PROD001");
        order.setQuantity(1);
        order.setAmount(15000.00); // High value
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-003", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        assertEquals(VetroMessageProcessor.ProcessingStatus.SUCCESS, result.getStatus());
        assertTrue((Boolean) context.getProperty("highValueOrder"));
        
        // Check that high value orders are routed to approval queue
        VetroMessageProcessor.RoutingDecision routing = result.getRoutingDecision();
        assertEquals("approval.orders.queue", routing.getDestination());
        assertTrue(routing.isExpectResponse());
        assertNotNull(routing.getResponseDestination());
    }
    
    @Test
    void testPremiumCustomerRouting() {
        // Given - Premium customer order
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("PREM001"); // Premium customer (starts with PREM)
        order.setProductId("PROD001");
        order.setQuantity(1);
        order.setAmount(99.99);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-004", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        assertEquals(VetroMessageProcessor.ProcessingStatus.SUCCESS, result.getStatus());
        
        // Check premium customer routing
        VetroMessageProcessor.RoutingDecision routing = result.getRoutingDecision();
        assertEquals("premium.orders.queue", routing.getDestination());
        assertEquals("primary", routing.getDatacenter());
        assertEquals("HIGH", routing.getHeaders().get("priority"));
    }
    
    @Test
    void testStandardCustomerRouting() {
        // Given - Standard customer order
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("CUST001"); // Standard customer
        order.setProductId("PROD001");
        order.setQuantity(1);
        order.setAmount(99.99);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-005", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        assertEquals(VetroMessageProcessor.ProcessingStatus.SUCCESS, result.getStatus());
        
        // Check standard customer routing (load balanced to secondary)
        VetroMessageProcessor.RoutingDecision routing = result.getRoutingDecision();
        assertEquals("standard.orders.queue", routing.getDatacenter());
        assertEquals("secondary", routing.getDatacenter());
        assertEquals("NORMAL", routing.getHeaders().get("priority"));
    }
    
    @Test
    void testEnrichmentProcess() {
        // Given
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("CUST001");
        order.setProductId("PROD001");
        order.setQuantity(1);
        order.setAmount(99.99);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-006", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        OrderVetroProcessor.OrderMessage enrichedOrder = 
            (OrderVetroProcessor.OrderMessage) result.getEnrichedPayload();
        
        assertNotNull(enrichedOrder.getCustomerInfo());
        assertNotNull(enrichedOrder.getProductInfo());
        assertNotNull(enrichedOrder.getPricingInfo());
        assertEquals("Customer CUST001", enrichedOrder.getCustomerInfo().getName());
        assertEquals("STANDARD", enrichedOrder.getCustomerInfo().getTier());
        assertTrue(enrichedOrder.getEnrichmentTimestamp() > 0);
        assertEquals("OrderVetroProcessor", enrichedOrder.getEnrichedBy());
    }
    
    @Test
    void testTransformationProcess() {
        // Given
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("CUST001");
        order.setProductId("PROD001");
        order.setQuantity(2);
        order.setAmount(199.98);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("TEST-007", "ORDER");
        
        // When
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then
        OrderVetroProcessor.TransformedOrderMessage transformed = 
            (OrderVetroProcessor.TransformedOrderMessage) result.getTransformedPayload();
        
        assertNotNull(transformed);
        assertEquals(order.getOrderId(), transformed.getOrderId());
        assertEquals("Customer CUST001", transformed.getCustomerName());
        assertEquals("STANDARD", transformed.getCustomerTier());
        assertEquals("Product PROD001", transformed.getProductName());
        assertEquals(2, transformed.getQuantity());
        assertEquals("USD", transformed.getCurrency());
        assertEquals("TRANSFORMED_ORDER", transformed.getMessageType());
        assertEquals(context.getCorrelationId(), transformed.getCorrelationId());
        assertFalse(transformed.isRequiresApproval()); // Not high value
        assertEquals("NORMAL", transformed.getPriority());
    }
    
    /**
     * Integration test demonstrating the complete VETRO workflow
     */
    @Test
    void testCompleteVetroWorkflow() {
        // Given - A complete order scenario
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setCustomerId("PREM001"); // Premium customer
        order.setProductId("PROD001");
        order.setQuantity(3);
        order.setAmount(299.97);
        
        VetroMessageProcessor.ProcessingContext context = 
            new VetroMessageProcessor.ProcessingContext("WORKFLOW-001", "ORDER");
        context.addProperty("sourceSystem", "WebStore");
        context.addProperty("userId", "user123");
        
        // When - Process through complete VETRO pipeline
        VetroMessageProcessor.ProcessingResult result = processor.processMessage(order, context);
        
        // Then - Verify complete workflow
        assertEquals(VetroMessageProcessor.ProcessingStatus.SUCCESS, result.getStatus());
        
        // Validation passed
        assertTrue(result.getValidationResult().isValid());
        
        // Enrichment occurred
        OrderVetroProcessor.OrderMessage enriched = 
            (OrderVetroProcessor.OrderMessage) result.getEnrichedPayload();
        assertNotNull(enriched.getCustomerInfo());
        assertNotNull(enriched.getProductInfo());
        assertNotNull(enriched.getPricingInfo());
        
        // Transformation occurred
        OrderVetroProcessor.TransformedOrderMessage transformed = 
            (OrderVetroProcessor.TransformedOrderMessage) result.getTransformedPayload();
        assertEquals("PREMIUM", transformed.getCustomerTier());
        assertEquals(299.97 * 1.08, transformed.getTotalAmount() + transformed.getTaxAmount(), 0.01);
        
        // Routing decision made
        VetroMessageProcessor.RoutingDecision routing = result.getRoutingDecision();
        assertEquals("premium.orders.queue", routing.getDestination());
        assertEquals("primary", routing.getDatacenter());
        
        // Operation completed
        assertTrue(result.getOperationResult().isSuccess());
        assertNotNull(result.getOperationResult().getMessageId());
        
        logger.info("Complete VETRO workflow test completed successfully for correlation: {}", 
            result.getCorrelationId());
    }
}