package com.prospringjms.messaging.context;

import jakarta.jms.Message;
import jakarta.jms.Session;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced processing context that includes JMS session awareness and retry context.
 * This context is passed through the VETRO pipeline and provides access to JMS session
 * for transactional operations and retry information for failure handling.
 */
public class SessionAwareProcessingContext {
    
    private final String correlationId;
    private final LocalDateTime processingStartTime;
    private final Map<String, Object> attributes;
    private final Message originalMessage;
    private final Session jmsSession;
    private final String sourceDestination;
    private final boolean sessionTransacted;
    
    // Retry-related fields
    private RetryContext retryContext;
    private int currentRetryAttempt = 0;
    private int maxRetryAttempts = 3;
    private LocalDateTime firstAttemptTime;
    private Exception lastFailure;
    private String lastFailedStep;
    
    public SessionAwareProcessingContext(Message originalMessage, Session jmsSession, 
                                       String sourceDestination) {
        this.correlationId = generateCorrelationId(originalMessage);
        this.processingStartTime = LocalDateTime.now();
        this.firstAttemptTime = this.processingStartTime;
        this.attributes = new HashMap<>();
        this.originalMessage = originalMessage;
        this.jmsSession = jmsSession;
        this.sourceDestination = sourceDestination;
        this.sessionTransacted = isSessionTransacted(jmsSession);
        this.currentRetryAttempt = 1;
    }
    
    // Copy constructor for retry scenarios
    private SessionAwareProcessingContext(SessionAwareProcessingContext original, 
                                        RetryContext retryContext) {
        this.correlationId = original.correlationId;
        this.processingStartTime = LocalDateTime.now(); // New processing start time for retry
        this.firstAttemptTime = original.firstAttemptTime; // Keep original first attempt time
        this.attributes = new HashMap<>(original.attributes);
        this.originalMessage = original.originalMessage;
        this.jmsSession = original.jmsSession;
        this.sourceDestination = original.sourceDestination;
        this.sessionTransacted = original.sessionTransacted;
        this.retryContext = retryContext;
        this.currentRetryAttempt = retryContext.getCurrentAttempt();
        this.maxRetryAttempts = retryContext.getMaxAttempts();
        this.lastFailure = retryContext.getLastException();
        this.lastFailedStep = retryContext.getFailedStep();
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public LocalDateTime getProcessingStartTime() {
        return processingStartTime;
    }
    
    public LocalDateTime getFirstAttemptTime() {
        return firstAttemptTime;
    }
    
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public Message getOriginalMessage() {
        return originalMessage;
    }
    
    public Session getJmsSession() {
        return jmsSession;
    }
    
    public String getSourceDestination() {
        return sourceDestination;
    }
    
    public boolean isSessionTransacted() {
        return sessionTransacted;
    }
    
    public RetryContext getRetryContext() {
        return retryContext;
    }
    
    public boolean isRetryAttempt() {
        return retryContext != null && currentRetryAttempt > 1;
    }
    
    public int getCurrentRetryAttempt() {
        return currentRetryAttempt;
    }
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public Exception getLastFailure() {
        return lastFailure;
    }
    
    public String getLastFailedStep() {
        return lastFailedStep;
    }
    
    public boolean isLastRetryAttempt() {
        return currentRetryAttempt >= maxRetryAttempts;
    }
    
    /**
     * Creates a new context for retry processing.
     * 
     * @param failedException The exception that caused the failure
     * @param failedStep The VETRO step that failed
     * @return New context for retry processing
     */
    public SessionAwareProcessingContext createRetryContext(Exception failedException, String failedStep) {
        int nextAttempt = this.currentRetryAttempt + 1;
        
        RetryContext newRetryContext = new RetryContext.Builder()
            .currentAttempt(nextAttempt)
            .maxAttempts(maxRetryAttempts)
            .correlationId(correlationId)
            .firstAttemptTime(firstAttemptTime)
            .currentAttemptTime(LocalDateTime.now())
            .lastException(failedException)
            .failedStep(failedStep)
            .attributes(attributes)
            .build();
        
        return new SessionAwareProcessingContext(this, newRetryContext);
    }
    
    /**
     * Commits the JMS session if it's transacted.
     * Should be called after successful processing.
     */
    public void commitSession() {
        if (sessionTransacted && jmsSession != null) {
            try {
                jmsSession.commit();
            } catch (Exception e) {
                throw new RuntimeException("Failed to commit JMS session", e);
            }
        }
    }
    
    /**
     * Rolls back the JMS session if it's transacted.
     * Should be called after processing failure.
     */
    public void rollbackSession() {
        if (sessionTransacted && jmsSession != null) {
            try {
                jmsSession.rollback();
            } catch (Exception e) {
                throw new RuntimeException("Failed to rollback JMS session", e);
            }
        }
    }
    
    private String generateCorrelationId(Message message) {
        try {
            String jmsCorrelationId = message != null ? message.getJMSCorrelationID() : null;
            return jmsCorrelationId != null ? jmsCorrelationId : UUID.randomUUID().toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
    
    private boolean isSessionTransacted(Session session) {
        try {
            return session != null && session.getTransacted();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return String.format("SessionAwareProcessingContext{correlationId='%s', attempt=%d/%d, transacted=%s, step='%s'}", 
            correlationId, currentRetryAttempt, maxRetryAttempts, sessionTransacted, lastFailedStep);
    }
}