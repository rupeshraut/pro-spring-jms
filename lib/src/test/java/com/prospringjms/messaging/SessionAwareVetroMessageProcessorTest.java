package com.prospringjms.messaging;

import com.prospringjms.messaging.context.SessionAwareProcessingContext;
import com.prospringjms.messaging.context.RetryContext;
import com.prospringjms.messaging.examples.OrderVetroProcessor;
import com.prospringjms.messaging.service.SessionAwareVetroJmsIntegrationService;
import com.prospringjms.sender.ResilientJmsSender;
import com.prospringjms.listener.JmsListenerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.JMSException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Enhanced tests for session-aware VETRO message processing with retry support.
 */
@ExtendWith(MockitoExtension.class)
class SessionAwareVetroMessageProcessorTest {
    
    @Mock private ResilientJmsSender mockJmsSender;
    @Mock private JmsListenerRegistry mockListenerRegistry;
    @Mock private Message mockMessage;
    @Mock private Session mockSession;
    @Mock private TextMessage mockTextMessage;
    
    private OrderVetroProcessor processor;
    private SessionAwareVetroJmsIntegrationService integrationService;
    
    @BeforeEach
    void setUp() throws JMSException {
        processor = new OrderVetroProcessor();
        processor.jmsSender = mockJmsSender;
        processor.listenerRegistry = mockListenerRegistry;
        
        integrationService = new SessionAwareVetroJmsIntegrationService();
        integrationService.jmsListenerRegistry = mockListenerRegistry;
        
        // Setup mock message
        when(mockMessage.getJMSCorrelationID()).thenReturn("test-correlation-123");
        when(mockMessage.getJMSMessageID()).thenReturn("test-message-123");
        when(mockSession.getTransacted()).thenReturn(true);
    }
    
    @Test
    void testSessionAwareProcessing() throws JMSException {
        // Given
        OrderVetroProcessor.OrderMessage orderMessage = createValidOrderMessage();
        
        // When
        ProcessingResult result = processor.processMessage(mockMessage, mockSession, "test.orders.queue");
        
        // Then
        assertNotNull(result);
        assertEquals("test-correlation-123", result.getCorrelationId());
        
        // Verify session operations were available
        // (Note: In real scenario, session operations would be performed based on processing outcome)
    }
    
    @Test
    void testRetryContextCreation() {
        // Given
        SessionAwareProcessingContext originalContext = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        originalContext.setMaxRetryAttempts(3);
        
        Exception testException = new RuntimeException("Test failure");
        
        // When
        SessionAwareProcessingContext retryContext = originalContext.createRetryContext(testException, "validation");
        
        // Then
        assertNotNull(retryContext.getRetryContext());
        assertEquals(2, retryContext.getCurrentRetryAttempt());
        assertEquals(3, retryContext.getMaxRetryAttempts());
        assertEquals("validation", retryContext.getRetryContext().getFailedStep());
        assertEquals(testException, retryContext.getRetryContext().getLastException());
        assertFalse(retryContext.getRetryContext().isLastAttempt());
    }
    
    @Test
    void testRetryContextLastAttempt() {
        // Given
        SessionAwareProcessingContext originalContext = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        originalContext.setMaxRetryAttempts(2);
        
        // Create context at max attempts
        SessionAwareProcessingContext retryContext1 = originalContext.createRetryContext(
            new RuntimeException("First failure"), "enrichment");
        SessionAwareProcessingContext retryContext2 = retryContext1.createRetryContext(
            new RuntimeException("Second failure"), "transformation");
        
        // Then
        assertTrue(retryContext2.getRetryContext().isLastAttempt());
        assertEquals(0, retryContext2.getRetryContext().getRemainingAttempts());
    }
    
    @Test
    void testBackoffDelayCalculation() {
        // Given
        RetryContext.Builder builder = new RetryContext.Builder()
            .currentAttempt(3)
            .maxAttempts(5)
            .correlationId("test-123")
            .firstAttemptTime(LocalDateTime.now().minusMinutes(1))
            .currentAttemptTime(LocalDateTime.now());
        
        RetryContext retryContext = builder.build();
        
        // When
        long delay = retryContext.calculateBackoffDelay(1000, 2.0);
        
        // Then
        assertEquals(4000, delay); // 1000 * 2^(3-1) = 1000 * 4 = 4000
    }
    
    @Test
    void testLastAttemptNoBackoff() {
        // Given
        RetryContext.Builder builder = new RetryContext.Builder()
            .currentAttempt(3)
            .maxAttempts(3)
            .correlationId("test-123")
            .firstAttemptTime(LocalDateTime.now().minusMinutes(1))
            .currentAttemptTime(LocalDateTime.now());
        
        RetryContext retryContext = builder.build();
        
        // When
        long delay = retryContext.calculateBackoffDelay(1000, 2.0);
        
        // Then
        assertEquals(0, delay); // No delay for last attempt
    }
    
    @Test
    void testOrderProcessorRetryLogic() {
        // Given
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        
        // Test validation retry (should not retry)
        assertFalse(processor.shouldRetryStep("validation", 
            new RuntimeException("Invalid data"), context));
        
        // Test enrichment retry with timeout (should retry)
        assertTrue(processor.shouldRetryStep("enrichment", 
            new RuntimeException("Connection timeout"), context));
        
        // Test enrichment retry without timeout (should not retry)
        assertFalse(processor.shouldRetryStep("enrichment", 
            new RuntimeException("Data not found"), context));
        
        // Test operation retry with JMS error (should retry)
        assertTrue(processor.shouldRetryStep("operation", 
            new RuntimeException("JMS connection failed"), context));
    }
    
    @Test
    void testFinalFailureHandling() throws Exception {
        // Given
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        context.setAttribute("payload", createValidOrderMessage());
        
        RetryContext retryContext = new RetryContext.Builder()
            .currentAttempt(3)
            .maxAttempts(3)
            .correlationId("test-correlation-123")
            .firstAttemptTime(LocalDateTime.now().minusMinutes(5))
            .currentAttemptTime(LocalDateTime.now())
            .lastException(new RuntimeException("Final failure"))
            .failedStep("operation")
            .build();
        
        SessionAwareProcessingContext finalContext = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        // Simulate final context
        
        ResilientJmsSender.SendResult mockSendResult = mock(ResilientJmsSender.SendResult.class);
        when(mockSendResult.getMessageId()).thenReturn("dlq-message-123");
        when(mockJmsSender.sendToPrimary(eq("order.processing.dlq"), any(), any())).thenReturn(mockSendResult);
        
        // When
        processor.handleFinalFailure("operation", new RuntimeException("Final failure"), finalContext);
        
        // Then
        verify(mockJmsSender).sendToPrimary(eq("order.processing.dlq"), any(), any());
        verify(mockSession).rollback(); // Session should be rolled back
    }
    
    @Test
    void testSessionTransactionCommit() throws JMSException {
        // Given
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        
        when(mockSession.getTransacted()).thenReturn(true);
        
        // When
        context.commitSession();
        
        // Then
        verify(mockSession).commit();
    }
    
    @Test
    void testSessionTransactionRollback() throws JMSException {
        // Given
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        
        when(mockSession.getTransacted()).thenReturn(true);
        
        // When
        context.rollbackSession();
        
        // Then
        verify(mockSession).rollback();
    }
    
    @Test
    void testNonTransactedSessionOperations() throws JMSException {
        // Given
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            mockMessage, mockSession, "test.orders.queue");
        
        when(mockSession.getTransacted()).thenReturn(false);
        
        // When
        context.commitSession();
        context.rollbackSession();
        
        // Then
        verify(mockSession, never()).commit();
        verify(mockSession, never()).rollback();
    }
    
    @Test
    void testIntegrationServiceRegistration() {
        // Given
        String destination = "test.orders.queue";
        String datacenter = "primary";
        boolean sessionTransacted = true;
        int concurrency = 2;
        
        when(mockListenerRegistry.registerListener(any(), any(), any(), anyBoolean(), anyInt()))
            .thenReturn("listener-123");
        
        // When
        String registrationId = integrationService.registerSessionAwareProcessor(
            destination, processor, datacenter, sessionTransacted, concurrency);
        
        // Then
        assertNotNull(registrationId);
        assertTrue(registrationId.contains(destination));
        assertTrue(registrationId.contains(datacenter));
        verify(mockListenerRegistry).registerListener(
            eq(destination), any(), eq(datacenter), eq(sessionTransacted), eq(concurrency));
    }
    
    @Test
    void testMultipleProcessorRegistration() {
        // Given
        Map<String, SessionAwareVetroJmsIntegrationService.ProcessorConfig> configs = new HashMap<>();
        
        SessionAwareVetroJmsIntegrationService.ProcessorConfig config1 = 
            new SessionAwareVetroJmsIntegrationService.ProcessorConfig();
        config1.setDestination("orders.queue");
        config1.setProcessor(processor);
        config1.setDatacenter("primary");
        config1.setSessionTransacted(true);
        config1.setConcurrency(3);
        
        SessionAwareVetroJmsIntegrationService.ProcessorConfig config2 = 
            new SessionAwareVetroJmsIntegrationService.ProcessorConfig();
        config2.setDestination("payments.queue");
        config2.setProcessor(processor);
        config2.setDatacenter("secondary");
        config2.setSessionTransacted(false);
        config2.setConcurrency(1);
        
        configs.put("orderProcessor", config1);
        configs.put("paymentProcessor", config2);
        
        when(mockListenerRegistry.registerListener(any(), any(), any(), anyBoolean(), anyInt()))
            .thenReturn("listener-1", "listener-2");
        
        // When
        Map<String, String> registrationIds = integrationService.registerMultipleProcessors(configs);
        
        // Then
        assertEquals(2, registrationIds.size());
        assertTrue(registrationIds.containsKey("orderProcessor"));
        assertTrue(registrationIds.containsKey("paymentProcessor"));
        verify(mockListenerRegistry, times(2)).registerListener(any(), any(), any(), anyBoolean(), anyInt());
    }
    
    @Test
    void testProcessorShutdown() {
        // Given
        processor.configureRetry(5, 2000, 1.5);
        
        // When
        processor.shutdown();
        
        // Then
        // Should complete without errors - testing that executor shuts down properly
        assertDoesNotThrow(() -> processor.shutdown());
    }
    
    private OrderVetroProcessor.OrderMessage createValidOrderMessage() {
        OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
        order.setOrderId("ORD-123");
        order.setCustomerId("CUST-456");
        order.setProductId("PROD-789");
        order.setQuantity(2);
        order.setAmount(99.99);
        return order;
    }
}