package com.prospringjms.messaging.context;

import jakarta.jms.Message;
import jakarta.jms.Session;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe enhanced processing context with improved concurrency support.
 * This version provides better thread safety and performance for concurrent processing scenarios.
 */
public class ThreadSafeProcessingContext {
    
    private final String correlationId;
    private final LocalDateTime processingStartTime;
    private final Map<String, Object> attributes;
    private final Message originalMessage;
    private final Session jmsSession;
    private final String sourceDestination;
    private final boolean sessionTransacted;
    
    // Thread-safe retry tracking
    private volatile RetryContext retryContext;
    private final AtomicInteger currentRetryAttempt = new AtomicInteger(1);
    private final AtomicInteger maxRetryAttempts = new AtomicInteger(3);
    private final LocalDateTime firstAttemptTime;
    private volatile Exception lastFailure;
    private volatile String lastFailedStep;
    
    // Performance tracking
    private final AtomicLong processingTimeMs = new AtomicLong(0);
    private final Map<String, Long> stepDurations = new ConcurrentHashMap<>();
    private final ReadWriteLock contextLock = new ReentrantReadWriteLock();
    
    // Audit trail
    private final Map<String, LocalDateTime> stepStartTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> stepEndTimes = new ConcurrentHashMap<>();
    private final Map<String, String> stepResults = new ConcurrentHashMap<>();
    
    public ThreadSafeProcessingContext(Message originalMessage, Session jmsSession, 
                                     String sourceDestination) {
        this.correlationId = generateCorrelationId(originalMessage);
        this.processingStartTime = LocalDateTime.now();
        this.firstAttemptTime = this.processingStartTime;
        this.attributes = new ConcurrentHashMap<>();
        this.originalMessage = originalMessage;
        this.jmsSession = jmsSession;
        this.sourceDestination = sourceDestination;
        this.sessionTransacted = isSessionTransacted(jmsSession);
    }
    
    // Copy constructor for retry scenarios with thread safety
    private ThreadSafeProcessingContext(ThreadSafeProcessingContext original, 
                                      RetryContext retryContext) {
        this.correlationId = original.correlationId;
        this.processingStartTime = LocalDateTime.now();
        this.firstAttemptTime = original.firstAttemptTime;
        this.attributes = new ConcurrentHashMap<>(original.getAttributes());
        this.originalMessage = original.originalMessage;
        this.jmsSession = original.jmsSession;
        this.sourceDestination = original.sourceDestination;
        this.sessionTransacted = original.sessionTransacted;
        this.retryContext = retryContext;
        this.currentRetryAttempt.set(retryContext.getCurrentAttempt());
        this.maxRetryAttempts.set(retryContext.getMaxAttempts());
        this.lastFailure = retryContext.getLastException();
        this.lastFailedStep = retryContext.getFailedStep();
    }
    
    /**
     * Thread-safe step tracking.
     */
    public void startStep(String stepName) {
        stepStartTimes.put(stepName, LocalDateTime.now());
    }
    
    public void endStep(String stepName, boolean successful, String result) {
        LocalDateTime endTime = LocalDateTime.now();
        stepEndTimes.put(stepName, endTime);
        stepResults.put(stepName, successful ? "SUCCESS" : "FAILURE: " + result);
        
        LocalDateTime startTime = stepStartTimes.get(stepName);
        if (startTime != null) {
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            stepDurations.put(stepName, durationMs);
        }
    }
    
    /**
     * Thread-safe attribute management.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public Map<String, Object> getAttributes() {
        return new ConcurrentHashMap<>(attributes);
    }
    
    /**
     * Thread-safe retry context creation.
     */
    public ThreadSafeProcessingContext createRetryContext(Exception failedException, String failedStep) {
        contextLock.writeLock().lock();
        try {
            int nextAttempt = this.currentRetryAttempt.incrementAndGet();
            
            RetryContext newRetryContext = new RetryContext.Builder()
                .currentAttempt(nextAttempt)
                .maxAttempts(maxRetryAttempts.get())
                .correlationId(correlationId)
                .firstAttemptTime(firstAttemptTime)
                .currentAttemptTime(LocalDateTime.now())
                .lastException(failedException)
                .failedStep(failedStep)
                .attributes(attributes)
                .build();
            
            return new ThreadSafeProcessingContext(this, newRetryContext);
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    /**
     * Thread-safe session operations.
     */
    public void commitSession() {
        if (sessionTransacted && jmsSession != null) {
            contextLock.readLock().lock();
            try {
                jmsSession.commit();
            } catch (Exception e) {
                throw new RuntimeException("Failed to commit JMS session", e);
            } finally {
                contextLock.readLock().unlock();
            }
        }
    }
    
    public void rollbackSession() {
        if (sessionTransacted && jmsSession != null) {
            contextLock.readLock().lock();
            try {
                jmsSession.rollback();
            } catch (Exception e) {
                throw new RuntimeException("Failed to rollback JMS session", e);
            } finally {
                contextLock.readLock().unlock();
            }
        }
    }
    
    /**
     * Performance and audit methods.
     */
    public long getStepDuration(String stepName) {
        return stepDurations.getOrDefault(stepName, 0L);
    }
    
    public Map<String, Long> getAllStepDurations() {
        return new ConcurrentHashMap<>(stepDurations);
    }
    
    public long getTotalProcessingTimeMs() {
        return processingTimeMs.updateAndGet(current -> {
            if (current == 0) {
                return java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            }
            return current;
        });
    }
    
    public ProcessingAuditTrail getAuditTrail() {
        return new ProcessingAuditTrail(
            correlationId,
            firstAttemptTime,
            processingStartTime,
            new ConcurrentHashMap<>(stepStartTimes),
            new ConcurrentHashMap<>(stepEndTimes),
            new ConcurrentHashMap<>(stepResults),
            new ConcurrentHashMap<>(stepDurations),
            currentRetryAttempt.get(),
            lastFailedStep
        );
    }
    
    // Getters with thread safety
    public String getCorrelationId() { return correlationId; }
    public LocalDateTime getProcessingStartTime() { return processingStartTime; }
    public LocalDateTime getFirstAttemptTime() { return firstAttemptTime; }
    public Message getOriginalMessage() { return originalMessage; }
    public Session getJmsSession() { return jmsSession; }
    public String getSourceDestination() { return sourceDestination; }
    public boolean isSessionTransacted() { return sessionTransacted; }
    public RetryContext getRetryContext() { return retryContext; }
    public boolean isRetryAttempt() { return retryContext != null && currentRetryAttempt.get() > 1; }
    public int getCurrentRetryAttempt() { return currentRetryAttempt.get(); }
    public int getMaxRetryAttempts() { return maxRetryAttempts.get(); }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts.set(maxRetryAttempts);
    }
    
    public Exception getLastFailure() { return lastFailure; }
    public String getLastFailedStep() { return lastFailedStep; }
    public boolean isLastRetryAttempt() { return currentRetryAttempt.get() >= maxRetryAttempts.get(); }
    
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
        return String.format("ThreadSafeProcessingContext{correlationId='%s', attempt=%d/%d, transacted=%s, step='%s'}", 
            correlationId, currentRetryAttempt.get(), maxRetryAttempts.get(), sessionTransacted, lastFailedStep);
    }
    
    /**
     * Audit trail data class.
     */
    public static class ProcessingAuditTrail {
        private final String correlationId;
        private final LocalDateTime firstAttemptTime;
        private final LocalDateTime currentAttemptTime;
        private final Map<String, LocalDateTime> stepStartTimes;
        private final Map<String, LocalDateTime> stepEndTimes;
        private final Map<String, String> stepResults;
        private final Map<String, Long> stepDurations;
        private final int attemptNumber;
        private final String lastFailedStep;
        
        public ProcessingAuditTrail(String correlationId, LocalDateTime firstAttemptTime, 
                                  LocalDateTime currentAttemptTime, Map<String, LocalDateTime> stepStartTimes,
                                  Map<String, LocalDateTime> stepEndTimes, Map<String, String> stepResults,
                                  Map<String, Long> stepDurations, int attemptNumber, String lastFailedStep) {
            this.correlationId = correlationId;
            this.firstAttemptTime = firstAttemptTime;
            this.currentAttemptTime = currentAttemptTime;
            this.stepStartTimes = stepStartTimes;
            this.stepEndTimes = stepEndTimes;
            this.stepResults = stepResults;
            this.stepDurations = stepDurations;
            this.attemptNumber = attemptNumber;
            this.lastFailedStep = lastFailedStep;
        }
        
        // Getters
        public String getCorrelationId() { return correlationId; }
        public LocalDateTime getFirstAttemptTime() { return firstAttemptTime; }
        public LocalDateTime getCurrentAttemptTime() { return currentAttemptTime; }
        public Map<String, LocalDateTime> getStepStartTimes() { return stepStartTimes; }
        public Map<String, LocalDateTime> getStepEndTimes() { return stepEndTimes; }
        public Map<String, String> getStepResults() { return stepResults; }
        public Map<String, Long> getStepDurations() { return stepDurations; }
        public int getAttemptNumber() { return attemptNumber; }
        public String getLastFailedStep() { return lastFailedStep; }
    }
}