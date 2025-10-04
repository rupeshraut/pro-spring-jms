package com.prospringjms.messaging;

import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.sender.ResilientJmsSender;
import com.prospringjms.exception.JmsLibraryException;
import com.prospringjms.messaging.context.SessionAwareProcessingContext;
import com.prospringjms.messaging.context.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.JMSException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Abstract VETRO (Validation, Enrichment, Transformation, Routing, Operation) 
 * message processor using the Template Method design pattern.
 * 
 * This enhanced version supports session-aware listeners and provides retry context 
 * to concrete implementations for better failure handling and transactional control.
 */
public abstract class VetroMessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(VetroMessageProcessor.class);
    
    @Autowired
    protected ResilientJmsSender jmsSender;
    
    @Autowired
    protected JmsListenerRegistry listenerRegistry;
    
    private String responseListenerId;
    private ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(5);
    
    // Retry configuration
    protected int defaultMaxRetryAttempts = 3;
    protected long defaultRetryDelayMs = 1000; // 1 second base delay
    protected double retryBackoffMultiplier = 2.0;
    
    /**
     * Enhanced template method that processes JMS messages with session awareness and retry support.
     * This is the main entry point for session-aware message processing.
     */
    public final ProcessingResult processMessage(Message jmsMessage, Session jmsSession, 
                                               String sourceDestination) {
        SessionAwareProcessingContext context = new SessionAwareProcessingContext(
            jmsMessage, jmsSession, sourceDestination);
        context.setMaxRetryAttempts(defaultMaxRetryAttempts);
        
        return processMessageWithContext(jmsMessage, context);
    }
    
    /**
     * Legacy template method for backward compatibility.
     */
    public final ProcessingResult processMessage(Object inputPayload, ProcessingContext context) {
        // Convert to session-aware context (session will be null in this case)
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(
            null, null, "legacy");
        sessionContext.setMaxRetryAttempts(defaultMaxRetryAttempts);
        sessionContext.setAttribute("payload", inputPayload);
        sessionContext.setAttribute("legacyContext", context);
        
        return processMessageWithContext(inputPayload, sessionContext);
    }
    
    /**
     * Internal processing method that handles both session-aware and legacy processing.
     */
    private ProcessingResult processMessageWithContext(Object inputPayload, 
                                                     SessionAwareProcessingContext context) {
        logger.info("Starting VETRO processing for message: {} (attempt {}/{})", 
            context.getCorrelationId(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        ProcessingResult result = new ProcessingResult(context.getCorrelationId());
        
        try {
            // Step 1: Validation
            ValidationResult validationResult = validateWithRetry(inputPayload, context);
            result.setValidationResult(validationResult);
            
            if (!validationResult.isValid()) {
                logger.warn("Validation failed for message: {}, errors: {}", 
                    context.getCorrelationId(), validationResult.getErrors());
                result.setStatus(ProcessingStatus.VALIDATION_FAILED);
                return result;
            }
            
            // Step 2: Enrichment
            Object enrichedPayload = enrich(inputPayload, context);
            result.setEnrichedPayload(enrichedPayload);
            logger.debug("Message enrichment completed for: {}", context.getCorrelationId());
            
            // Step 3: Transformation
            Object transformedPayload = transform(enrichedPayload, context);
            result.setTransformedPayload(transformedPayload);
            logger.debug("Message transformation completed for: {}", context.getCorrelationId());
            
            // Step 4: Routing
            RoutingDecision routingDecision = route(transformedPayload, context);
            result.setRoutingDecision(routingDecision);
            logger.debug("Message routing decision made for: {}, destination: {}", 
                context.getCorrelationId(), routingDecision.getDestination());
            
            // Step 5: Operation (Send message via JMS)
            OperationResult operationResult = operate(transformedPayload, routingDecision, context);
            result.setOperationResult(operationResult);
            
            if (operationResult.isSuccess()) {
                result.setStatus(ProcessingStatus.SUCCESS);
                logger.info("VETRO processing completed successfully for: {}", context.getCorrelationId());
                
                // Setup response listener if required
                if (routingDecision.isExpectResponse()) {
                    setupResponseListener(routingDecision, context);
                }
            } else {
                result.setStatus(ProcessingStatus.OPERATION_FAILED);
                logger.error("Operation failed for message: {}, error: {}", 
                    context.getCorrelationId(), operationResult.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error during VETRO processing for message: {} (attempt {})", 
                context.getCorrelationId(), context.getCurrentRetryAttempt(), e);
            result.setStatus(ProcessingStatus.ERROR);
            result.setErrorMessage(e.getMessage());
            
            // Handle failure with potential retry
            handleProcessingFailure("processing", e, context);
        }
        
        return result;
    }
    
    /**
     * Validation step with retry support.
     */
    private ValidationResult validateWithRetry(Object payload, SessionAwareProcessingContext context) {
        try {
            return validate(payload, context);
        } catch (Exception e) {
            throw new ProcessingStepException("validation", e);
        }
    }
    
    /**
     * Enrichment step with retry support.
     */
    private Object enrichWithRetry(Object payload, SessionAwareProcessingContext context) {
        try {
            return enrich(payload, context);
        } catch (Exception e) {
            throw new ProcessingStepException("enrichment", e);
        }
    }
    
    /**
     * Transformation step with retry support.
     */
    private Object transformWithRetry(Object payload, SessionAwareProcessingContext context) {
        try {
            return transform(payload, context);
        } catch (Exception e) {
            throw new ProcessingStepException("transformation", e);
        }
    }
    
    /**
     * Routing step with retry support.
     */
    private RoutingDecision routeWithRetry(Object payload, SessionAwareProcessingContext context) {
        try {
            return route(payload, context);
        } catch (Exception e) {
            throw new ProcessingStepException("routing", e);
        }
    }
    
    /**
     * Operation step with retry support.
     */
    private OperationResult operateWithRetry(Object payload, RoutingDecision routingDecision, SessionAwareProcessingContext context) {
        try {
            return operate(payload, routingDecision, context);
        } catch (Exception e) {
            throw new ProcessingStepException("operation", e);
        }
    }
    
    /**
     * Handles processing failures and determines whether to retry.
     */
    private void handleProcessingFailure(String step, Exception exception, SessionAwareProcessingContext context) {
        // Rollback session if transacted
        if (context.isSessionTransacted()) {
            try {
                context.rollbackSession();
            } catch (Exception rollbackEx) {
                logger.error("Failed to rollback session for message: {}", context.getCorrelationId(), rollbackEx);
            }
        }
        
        // Check if we should retry
        if (shouldRetry(step, exception, context)) {
            scheduleRetry(step, exception, context);
        } else {
            // Final failure - let concrete class handle it
            handleFinalFailure(step, exception, context);
        }
    }
    
    /**
     * Determines whether a retry should be attempted.
     */
    private boolean shouldRetry(String step, Exception exception, SessionAwareProcessingContext context) {
        if (context.isLastRetryAttempt()) {
            return false;
        }
        
        // Ask concrete class if this step should be retried
        return shouldRetryStep(step, exception, context);
    }
    
    /**
     * Schedules a retry attempt with exponential backoff.
     */
    private void scheduleRetry(String step, Exception exception, SessionAwareProcessingContext context) {
        SessionAwareProcessingContext retryContext = context.createRetryContext(exception, step);
        long delay = retryContext.getRetryContext().calculateBackoffDelay(defaultRetryDelayMs, retryBackoffMultiplier);
        
        logger.info("Scheduling retry for message: {} in {}ms (attempt {}/{})", 
            context.getCorrelationId(), delay, retryContext.getCurrentRetryAttempt(), retryContext.getMaxRetryAttempts());
        
        retryExecutor.schedule(() -> {
            try {
                Object payload = context.getAttribute("payload");
                if (payload != null) {
                    processMessageWithContext(payload, retryContext);
                }
            } catch (Exception retryEx) {
                logger.error("Error during retry processing for message: {}", 
                    retryContext.getCorrelationId(), retryEx);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Async version of the template method for non-blocking processing.
     */
    public final CompletableFuture<ProcessingResult> processMessageAsync(Object inputPayload, ProcessingContext context) {
        return CompletableFuture.supplyAsync(() -> processMessage(inputPayload, context));
    }
    
    // Template methods to be implemented by concrete classes
    
    /**
     * Enhanced validation method with session awareness and retry context.
     * Concrete implementations should define validation rules specific to their use case.
     * 
     * @param payload the message payload to validate
     * @param context session-aware processing context containing retry information
     * @return validation result indicating success/failure and any error messages
     */
    protected abstract ValidationResult validate(Object payload, SessionAwareProcessingContext context);
    
    /**
     * Legacy validation method for backward compatibility.
     * Default implementation delegates to the session-aware version.
     */
    protected ValidationResult validate(Object payload, ProcessingContext context) {
        // Default implementation for backward compatibility
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(null, null, "legacy");
        sessionContext.setAttribute("legacyContext", context);
        return validate(payload, sessionContext);
    }
    
    /**
     * Enhanced enrichment method with session awareness and retry context.
     * This step can add context, lookup reference data, or augment the message.
     * 
     * @param payload the validated payload
     * @param context session-aware processing context containing retry information
     * @return enriched payload
     */
    protected abstract Object enrich(Object payload, SessionAwareProcessingContext context);
    
    /**
     * Legacy enrichment method for backward compatibility.
     */
    protected Object enrich(Object payload, ProcessingContext context) {
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(null, null, "legacy");
        sessionContext.setAttribute("legacyContext", context);
        return enrich(payload, sessionContext);
    }
    
    /**
     * Enhanced transformation method with session awareness and retry context.
     * This step handles format conversion, field mapping, or protocol translation.
     * 
     * @param payload the enriched payload
     * @param context session-aware processing context containing retry information
     * @return transformed payload
     */
    protected abstract Object transform(Object payload, SessionAwareProcessingContext context);
    
    /**
     * Legacy transformation method for backward compatibility.
     */
    protected Object transform(Object payload, ProcessingContext context) {
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(null, null, "legacy");
        sessionContext.setAttribute("legacyContext", context);
        return transform(payload, sessionContext);
    }
    
    /**
     * Enhanced routing method with session awareness and retry context.
     * This step decides where the message should be sent based on content or context.
     * 
     * @param payload the transformed payload
     * @param context session-aware processing context containing retry information
     * @return routing decision containing destination and delivery options
     */
    protected abstract RoutingDecision route(Object payload, SessionAwareProcessingContext context);
    
    /**
     * Legacy routing method for backward compatibility.
     */
    protected RoutingDecision route(Object payload, ProcessingContext context) {
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(null, null, "legacy");
        sessionContext.setAttribute("legacyContext", context);
        return route(payload, sessionContext);
    }
    
    /**
     * Enhanced operation method with session awareness and retry context.
     * Concrete implementations can override this for custom operations.
     * 
     * @param payload the transformed payload ready for delivery
     * @param routingDecision routing information
     * @param context session-aware processing context containing retry information
     * @return operation result indicating success/failure
     */
    protected OperationResult operate(Object payload, RoutingDecision routingDecision, SessionAwareProcessingContext context) {
        try {
            // Default implementation uses the JMS sender
            Map<String, Object> headers = new HashMap<>();
            headers.put("correlationId", context.getCorrelationId());
            headers.put("messageType", context.getMessageType());
            headers.put("timestamp", System.currentTimeMillis());
            
            // Add any custom headers from routing decision
            if (routingDecision.getHeaders() != null) {
                headers.putAll(routingDecision.getHeaders());
            }
            
            ResilientJmsSender.SendResult sendResult = jmsSender.sendToPrimary(
                routingDecision.getDestination(), payload, headers);
            
            logger.info("Message sent successfully to destination: {}, messageId: {}", 
                routingDecision.getDestination(), sendResult.getMessageId());
            
            return new OperationResult(true, sendResult.getMessageId(), 
                sendResult.getDatacenter(), "Message sent successfully");
                
        } catch (JmsLibraryException e) {
            logger.error("Failed to send message to destination: {}", 
                routingDecision.getDestination(), e);
            return new OperationResult(false, null, null, e.getMessage());
        }
    }
    
    /**
     * Enhanced response handler with session awareness.
     * Concrete implementations should override this to process responses.
     * 
     * @param responsePayload the response message payload
     * @param originalContext the original session-aware processing context
     */
    protected void handleResponse(Object responsePayload, SessionAwareProcessingContext originalContext) {
        logger.info("Received response for correlation ID: {}", originalContext.getCorrelationId());
        // Default implementation logs the response
        // Concrete classes should override for specific response handling
    }
    
    /**
     * Legacy response handler for backward compatibility.
     */
    protected void handleResponse(Object responsePayload, ProcessingContext originalContext) {
        SessionAwareProcessingContext sessionContext = new SessionAwareProcessingContext(null, null, "legacy");
        sessionContext.setAttribute("legacyContext", originalContext);
        handleResponse(responsePayload, sessionContext);
    }
    
    /**
     * Determines whether a processing step should be retried.
     * Concrete implementations can override this to provide custom retry logic.
     * 
     * @param step the VETRO step that failed (validation, enrichment, transformation, routing, operation)
     * @param exception the exception that caused the failure
     * @param context the session-aware processing context with retry information
     * @return true if the step should be retried, false otherwise
     */
    protected boolean shouldRetryStep(String step, Exception exception, SessionAwareProcessingContext context) {
        // Default retry logic - retry all steps except validation
        switch (step) {
            case "validation":
                return false; // Usually don't retry validation failures
            case "enrichment":
            case "transformation": 
            case "routing":
            case "operation":
                return true; // Retry these steps by default
            default:
                return false;
        }
    }
    
    /**
     * Handles final failure after all retry attempts are exhausted.
     * Concrete implementations should override this to provide custom failure handling
     * such as sending to dead letter queue, alerting, or manual intervention.
     * 
     * @param step the VETRO step that ultimately failed
     * @param exception the final exception
     * @param context the session-aware processing context with complete retry history
     */
    protected abstract void handleFinalFailure(String step, Exception exception, SessionAwareProcessingContext context);
    
    /**
     * Sets up a JMS listener for response messages if required.
     */
    private void setupResponseListener(RoutingDecision routingDecision, ProcessingContext context) {
        if (routingDecision.getResponseDestination() != null) {
            try {
                JmsListenerRegistry.ListenerRegistration registration = 
                    new JmsListenerRegistry.ListenerRegistration.Builder()
                        .destination(routingDecision.getResponseDestination())
                        .datacenter(routingDecision.getResponseDatacenter())
                        .messageListener(new ResponseMessageListener(context))
                        .concurrency("1-1") // Single threaded for response handling
                        .build();
                
                responseListenerId = listenerRegistry.registerListener(registration);
                logger.info("Response listener registered with ID: {} for correlation: {}", 
                    responseListenerId, context.getCorrelationId());
                
            } catch (JmsLibraryException e) {
                logger.error("Failed to setup response listener for correlation: {}", 
                    context.getCorrelationId(), e);
            }
        }
    }
    
    /**
     * Cleanup method to stop response listeners.
     */
    public void cleanup() {
        if (responseListenerId != null) {
            try {
                listenerRegistry.unregisterListener(responseListenerId);
                logger.info("Response listener unregistered: {}", responseListenerId);
            } catch (JmsLibraryException e) {
                logger.warn("Failed to unregister response listener: {}", responseListenerId, e);
            }
        }
    }
    
    /**
     * Inner class for handling response messages
     */
    private class ResponseMessageListener implements jakarta.jms.MessageListener {
        private final ProcessingContext originalContext;
        
        public ResponseMessageListener(ProcessingContext originalContext) {
            this.originalContext = originalContext;
        }
        
        @Override
        public void onMessage(Message message) {
            try {
                Object responsePayload = extractPayload(message);
                handleResponse(responsePayload, originalContext);
            } catch (Exception e) {
                logger.error("Error processing response message for correlation: {}", 
                    originalContext.getCorrelationId(), e);
            }
        }
        
        private Object extractPayload(Message message) throws JMSException {
            if (message instanceof TextMessage) {
                return ((TextMessage) message).getText();
            }
            // Add more message type handling as needed
            return message;
        }
    }
    
    // Supporting classes
    
    public static class ProcessingContext {
        private final String correlationId;
        private final String messageType;
        private final Map<String, Object> properties;
        private final long timestamp;
        
        public ProcessingContext(String correlationId, String messageType) {
            this.correlationId = correlationId;
            this.messageType = messageType;
            this.properties = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getCorrelationId() { return correlationId; }
        public String getMessageType() { return messageType; }
        public Map<String, Object> getProperties() { return properties; }
        public long getTimestamp() { return timestamp; }
        
        public void addProperty(String key, Object value) {
            properties.put(key, value);
        }
        
        public Object getProperty(String key) {
            return properties.get(key);
        }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final Map<String, String> errors;
        
        public ValidationResult(boolean valid) {
            this.valid = valid;
            this.errors = new HashMap<>();
        }
        
        public ValidationResult(boolean valid, Map<String, String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new HashMap<>();
        }
        
        public boolean isValid() { return valid; }
        public Map<String, String> getErrors() { return errors; }
        
        public static ValidationResult success() {
            return new ValidationResult(true);
        }
        
        public static ValidationResult failure(String field, String error) {
            Map<String, String> errors = new HashMap<>();
            errors.put(field, error);
            return new ValidationResult(false, errors);
        }
    }
    
    public static class RoutingDecision {
        private final String destination;
        private final String datacenter;
        private final Map<String, Object> headers;
        private final boolean expectResponse;
        private final String responseDestination;
        private final String responseDatacenter;
        
        public RoutingDecision(String destination, String datacenter) {
            this(destination, datacenter, null, false, null, null);
        }
        
        public RoutingDecision(String destination, String datacenter, Map<String, Object> headers,
                             boolean expectResponse, String responseDestination, String responseDatacenter) {
            this.destination = destination;
            this.datacenter = datacenter;
            this.headers = headers;
            this.expectResponse = expectResponse;
            this.responseDestination = responseDestination;
            this.responseDatacenter = responseDatacenter;
        }
        
        public String getDestination() { return destination; }
        public String getDatacenter() { return datacenter; }
        public Map<String, Object> getHeaders() { return headers; }
        public boolean isExpectResponse() { return expectResponse; }
        public String getResponseDestination() { return responseDestination; }
        public String getResponseDatacenter() { return responseDatacenter; }
    }
    
    public static class OperationResult {
        private final boolean success;
        private final String messageId;
        private final String datacenter;
        private final String message;
        
        public OperationResult(boolean success, String messageId, String datacenter, String message) {
            this.success = success;
            this.messageId = messageId;
            this.datacenter = datacenter;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getDatacenter() { return datacenter; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return success ? null : message; }
    }
    
    public enum ProcessingStatus {
        SUCCESS,
        VALIDATION_FAILED,
        OPERATION_FAILED,
        ERROR
    }
    
    public static class ProcessingResult {
        private final String correlationId;
        private ProcessingStatus status;
        private ValidationResult validationResult;
        private Object enrichedPayload;
        private Object transformedPayload;
        private RoutingDecision routingDecision;
        private OperationResult operationResult;
        private String errorMessage;
        
        public ProcessingResult(String correlationId) {
            this.correlationId = correlationId;
        }
        
        // Getters and setters
        public String getCorrelationId() { return correlationId; }
        public ProcessingStatus getStatus() { return status; }
        public void setStatus(ProcessingStatus status) { this.status = status; }
        public ValidationResult getValidationResult() { return validationResult; }
        public void setValidationResult(ValidationResult validationResult) { this.validationResult = validationResult; }
        public Object getEnrichedPayload() { return enrichedPayload; }
        public void setEnrichedPayload(Object enrichedPayload) { this.enrichedPayload = enrichedPayload; }
        public Object getTransformedPayload() { return transformedPayload; }
        public void setTransformedPayload(Object transformedPayload) { this.transformedPayload = transformedPayload; }
        public RoutingDecision getRoutingDecision() { return routingDecision; }
        public void setRoutingDecision(RoutingDecision routingDecision) { this.routingDecision = routingDecision; }
        public OperationResult getOperationResult() { return operationResult; }
        public void setOperationResult(OperationResult operationResult) { this.operationResult = operationResult; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * Configures retry settings for this processor.
     */
    public void configureRetry(int maxAttempts, long retryDelayMs, double backoffMultiplier) {
        this.defaultMaxRetryAttempts = maxAttempts;
        this.defaultRetryDelayMs = retryDelayMs;
        this.retryBackoffMultiplier = backoffMultiplier;
    }
    
    /**
     * Shuts down the retry executor when the processor is no longer needed.
     */
    public void shutdown() {
        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdown();
            try {
                if (!retryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Exception wrapper for processing step failures.
     */
    public static class ProcessingStepException extends RuntimeException {
        private final String step;
        
        public ProcessingStepException(String step, Throwable cause) {
            super("Failed at step: " + step, cause);
            this.step = step;
        }
        
        public String getStep() {
            return step;
        }
    }
}