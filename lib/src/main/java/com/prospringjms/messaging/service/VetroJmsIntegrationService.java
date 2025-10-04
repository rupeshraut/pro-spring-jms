package com.prospringjms.messaging.service;

import com.prospringjms.messaging.VetroMessageProcessor;
import com.prospringjms.messaging.examples.OrderVetroProcessor;
import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.exception.JmsLibraryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.jms.JMSException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that integrates VETRO message processing with JMS listeners.
 * 
 * This service demonstrates how to:
 * 1. Set up JMS listeners for incoming messages
 * 2. Process messages through the VETRO pipeline
 * 3. Handle responses and manage listener lifecycle
 */
@Service
public class VetroJmsIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(VetroJmsIntegrationService.class);
    
    @Autowired(required = false)
    private JmsListenerRegistry listenerRegistry;
    
    @Value("${jms.lib.enabled:false}")
    private boolean jmsEnabled;
    
    @Autowired
    private OrderVetroProcessor orderProcessor;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ConcurrentHashMap<String, String> activeListeners = new ConcurrentHashMap<>();
    
    /**
     * Initialize JMS listeners for VETRO processing on startup
     */
    @PostConstruct
    public void initializeListeners() {
        if (!jmsEnabled || listenerRegistry == null) {
            logger.info("JMS is disabled or not available. Skipping VETRO JMS integration service initialization.");
            return;
        }
        
        try {
            setupOrderMessageListener();
            setupPaymentMessageListener();
            setupInventoryMessageListener();
            logger.info("VETRO JMS integration service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize VETRO JMS integration service", e);
        }
    }
    
    /**
     * Sets up a listener for incoming order messages
     */
    private void setupOrderMessageListener() throws JmsLibraryException {
        JmsListenerRegistry.ListenerRegistration registration = 
            new JmsListenerRegistry.ListenerRegistration.Builder()
                .destination("incoming.orders.queue")
                .datacenter("primary")
                .messageListener(new OrderMessageListener())
                .concurrency("3-10") // Scale based on load
                .sessionTransacted(true)
                .build();
                
        String listenerId = listenerRegistry.registerListener(registration);
        activeListeners.put("orders", listenerId);
        logger.info("Order message listener registered with ID: {}", listenerId);
    }
    
    /**
     * Sets up a listener for payment messages that need VETRO processing
     */
    private void setupPaymentMessageListener() throws JmsLibraryException {
        JmsListenerRegistry.ListenerRegistration registration = 
            new JmsListenerRegistry.ListenerRegistration.Builder()
                .destination("incoming.payments.queue")
                .datacenter("primary")
                .messageListener(new PaymentMessageListener())
                .concurrency("2-5")
                .sessionTransacted(true)
                .build();
                
        String listenerId = listenerRegistry.registerListener(registration);
        activeListeners.put("payments", listenerId);
        logger.info("Payment message listener registered with ID: {}", listenerId);
    }
    
    /**
     * Sets up a listener for inventory messages
     */
    private void setupInventoryMessageListener() throws JmsLibraryException {
        JmsListenerRegistry.ListenerRegistration registration = 
            new JmsListenerRegistry.ListenerRegistration.Builder()
                .destination("incoming.inventory.queue")
                .datacenter("secondary") // Load balance to secondary DC
                .messageListener(new InventoryMessageListener())
                .concurrency("1-3")
                .sessionTransacted(false) // Non-transactional for inventory updates
                .build();
                
        String listenerId = listenerRegistry.registerListener(registration);
        activeListeners.put("inventory", listenerId);
        logger.info("Inventory message listener registered with ID: {}", listenerId);
    }
    
    /**
     * Processes a message through the VETRO pipeline asynchronously
     */
    public void processMessageAsync(Object payload, String messageType, String correlationId) {
        executorService.submit(() -> {
            try {
                VetroMessageProcessor.ProcessingContext context = 
                    new VetroMessageProcessor.ProcessingContext(correlationId, messageType);
                
                // Add service-level properties
                context.addProperty("serviceVersion", "1.0.0");
                context.addProperty("processingService", "VetroJmsIntegrationService");
                
                VetroMessageProcessor.ProcessingResult result = 
                    orderProcessor.processMessage(payload, context);
                
                logger.info("VETRO processing completed for correlation: {} with status: {}", 
                    correlationId, result.getStatus());
                    
                // Handle different processing outcomes
                handleProcessingResult(result);
                
            } catch (Exception e) {
                logger.error("Error during async VETRO processing for correlation: {}", 
                    correlationId, e);
            }
        });
    }
    
    /**
     * Handles the result of VETRO processing
     */
    private void handleProcessingResult(VetroMessageProcessor.ProcessingResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                logger.info("Message processed successfully: {}", result.getCorrelationId());
                // Could trigger success notifications or next workflow step
                break;
                
            case VALIDATION_FAILED:
                logger.warn("Message validation failed: {}, errors: {}", 
                    result.getCorrelationId(), result.getValidationResult().getErrors());
                // Send to error queue or notify sender
                handleValidationFailure(result);
                break;
                
            case OPERATION_FAILED:
                logger.error("Message operation failed: {}, error: {}", 
                    result.getCorrelationId(), result.getOperationResult().getErrorMessage());
                // Retry logic or send to DLQ
                handleOperationFailure(result);
                break;
                
            case ERROR:
                logger.error("Processing error for message: {}, error: {}", 
                    result.getCorrelationId(), result.getErrorMessage());
                // Send to error handler
                handleProcessingError(result);
                break;
        }
    }
    
    private void handleValidationFailure(VetroMessageProcessor.ProcessingResult result) {
        // Implementation: send validation errors back to sender or error queue
        logger.info("Handling validation failure for: {}", result.getCorrelationId());
    }
    
    private void handleOperationFailure(VetroMessageProcessor.ProcessingResult result) {
        // Implementation: retry logic or send to DLQ
        logger.info("Handling operation failure for: {}", result.getCorrelationId());
    }
    
    private void handleProcessingError(VetroMessageProcessor.ProcessingResult result) {
        // Implementation: error handling and notification
        logger.info("Handling processing error for: {}", result.getCorrelationId());
    }
    
    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down VETRO JMS integration service");
        
        // Unregister all listeners
        activeListeners.forEach((name, listenerId) -> {
            try {
                listenerRegistry.unregisterListener(listenerId);
                logger.info("Unregistered listener: {} ({})", name, listenerId);
            } catch (JmsLibraryException e) {
                logger.warn("Failed to unregister listener: {} ({})", name, listenerId, e);
            }
        });
        
        // Shutdown executor service
        executorService.shutdown();
        
        // Cleanup VETRO processor
        orderProcessor.cleanup();
        
        logger.info("VETRO JMS integration service shutdown complete");
    }
    
    /**
     * JMS Message Listener for Order messages
     */
    private class OrderMessageListener implements jakarta.jms.MessageListener {
        
        @Override
        public void onMessage(Message message) {
            String correlationId = null;
            try {
                correlationId = extractCorrelationId(message);
                logger.debug("Received order message with correlation ID: {}", correlationId);
                
                // Extract order data from JMS message
                OrderVetroProcessor.OrderMessage orderPayload = extractOrderPayload(message);
                
                // Process through VETRO pipeline
                processMessageAsync(orderPayload, "ORDER", correlationId);
                
            } catch (Exception e) {
                logger.error("Error processing order message with correlation ID: {}", 
                    correlationId, e);
            }
        }
        
        private OrderVetroProcessor.OrderMessage extractOrderPayload(Message message) throws JMSException {
            // Simple extraction - in real implementation, use JSON/XML parsing
            if (message instanceof TextMessage) {
                String text = ((TextMessage) message).getText();
                
                // Parse message content (simplified for demo)
                OrderVetroProcessor.OrderMessage order = new OrderVetroProcessor.OrderMessage();
                order.setCustomerId("CUST001");
                order.setProductId("PROD001");
                order.setQuantity(1);
                order.setAmount(99.99);
                
                return order;
            }
            
            throw new JMSException("Unsupported message type: " + message.getClass().getName());
        }
    }
    
    /**
     * JMS Message Listener for Payment messages
     */
    private class PaymentMessageListener implements jakarta.jms.MessageListener {
        
        @Override
        public void onMessage(Message message) {
            String correlationId = null;
            try {
                correlationId = extractCorrelationId(message);
                logger.debug("Received payment message with correlation ID: {}", correlationId);
                
                // Extract payment data
                Object paymentPayload = extractPaymentPayload(message);
                
                // Process through VETRO pipeline (could use different processor)
                processMessageAsync(paymentPayload, "PAYMENT", correlationId);
                
            } catch (Exception e) {
                logger.error("Error processing payment message with correlation ID: {}", 
                    correlationId, e);
            }
        }
        
        private Object extractPaymentPayload(Message message) throws JMSException {
            // Implementation would parse payment message
            return "Payment payload"; // Simplified for demo
        }
    }
    
    /**
     * JMS Message Listener for Inventory messages
     */
    private class InventoryMessageListener implements jakarta.jms.MessageListener {
        
        @Override
        public void onMessage(Message message) {
            String correlationId = null;
            try {
                correlationId = extractCorrelationId(message);
                logger.debug("Received inventory message with correlation ID: {}", correlationId);
                
                // Extract inventory data
                Object inventoryPayload = extractInventoryPayload(message);
                
                // Process through VETRO pipeline
                processMessageAsync(inventoryPayload, "INVENTORY", correlationId);
                
            } catch (Exception e) {
                logger.error("Error processing inventory message with correlation ID: {}", 
                    correlationId, e);
            }
        }
        
        private Object extractInventoryPayload(Message message) throws JMSException {
            // Implementation would parse inventory message
            return "Inventory payload"; // Simplified for demo
        }
    }
    
    /**
     * Extracts correlation ID from JMS message
     */
    private String extractCorrelationId(Message message) throws JMSException {
        String correlationId = message.getJMSCorrelationID();
        if (correlationId == null) {
            correlationId = message.getStringProperty("correlationId");
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            logger.debug("Generated new correlation ID: {}", correlationId);
        }
        return correlationId;
    }
}