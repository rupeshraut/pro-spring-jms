package com.prospringjms.messaging.context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object that provides retry information to VETRO processors.
 * Contains details about the current retry attempt, failure history, and processing metadata.
 */
public class RetryContext {
    
    private final int currentAttempt;
    private final int maxAttempts;
    private final String correlationId;
    private final LocalDateTime firstAttemptTime;
    private final LocalDateTime currentAttemptTime;
    private final Map<String, Object> attributes;
    private final Exception lastException;
    private final String failedStep;
    private final long totalElapsedTimeMs;
    private final boolean isLastAttempt;
    
    private RetryContext(Builder builder) {
        this.currentAttempt = builder.currentAttempt;
        this.maxAttempts = builder.maxAttempts;
        this.correlationId = builder.correlationId;
        this.firstAttemptTime = builder.firstAttemptTime;
        this.currentAttemptTime = builder.currentAttemptTime;
        this.attributes = new HashMap<>(builder.attributes);
        this.lastException = builder.lastException;
        this.failedStep = builder.failedStep;
        this.totalElapsedTimeMs = builder.totalElapsedTimeMs;
        this.isLastAttempt = builder.isLastAttempt;
    }
    
    public int getCurrentAttempt() {
        return currentAttempt;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public LocalDateTime getFirstAttemptTime() {
        return firstAttemptTime;
    }
    
    public LocalDateTime getCurrentAttemptTime() {
        return currentAttemptTime;
    }
    
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public Exception getLastException() {
        return lastException;
    }
    
    public String getFailedStep() {
        return failedStep;
    }
    
    public long getTotalElapsedTimeMs() {
        return totalElapsedTimeMs;
    }
    
    public boolean isLastAttempt() {
        return isLastAttempt;
    }
    
    public boolean isFirstAttempt() {
        return currentAttempt == 1;
    }
    
    public int getRemainingAttempts() {
        return Math.max(0, maxAttempts - currentAttempt);
    }
    
    /**
     * Calculates the exponential backoff delay for the next retry attempt.
     * 
     * @param baseDelayMs Base delay in milliseconds
     * @param multiplier Backoff multiplier
     * @return Calculated delay in milliseconds
     */
    public long calculateBackoffDelay(long baseDelayMs, double multiplier) {
        if (isLastAttempt) {
            return 0;
        }
        return (long) (baseDelayMs * Math.pow(multiplier, currentAttempt - 1));
    }
    
    @Override
    public String toString() {
        return String.format("RetryContext{attempt=%d/%d, correlationId='%s', failedStep='%s', isLast=%s}", 
            currentAttempt, maxAttempts, correlationId, failedStep, isLastAttempt);
    }
    
    /**
     * Builder for creating RetryContext instances.
     */
    public static class Builder {
        private int currentAttempt;
        private int maxAttempts;
        private String correlationId;
        private LocalDateTime firstAttemptTime;
        private LocalDateTime currentAttemptTime;
        private Map<String, Object> attributes = new HashMap<>();
        private Exception lastException;
        private String failedStep;
        private long totalElapsedTimeMs;
        private boolean isLastAttempt;
        
        public Builder currentAttempt(int currentAttempt) {
            this.currentAttempt = currentAttempt;
            return this;
        }
        
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            this.isLastAttempt = (currentAttempt >= maxAttempts);
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder firstAttemptTime(LocalDateTime firstAttemptTime) {
            this.firstAttemptTime = firstAttemptTime;
            return this;
        }
        
        public Builder currentAttemptTime(LocalDateTime currentAttemptTime) {
            this.currentAttemptTime = currentAttemptTime;
            if (firstAttemptTime != null) {
                this.totalElapsedTimeMs = java.time.Duration.between(firstAttemptTime, currentAttemptTime).toMillis();
            }
            return this;
        }
        
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }
        
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }
        
        public Builder lastException(Exception lastException) {
            this.lastException = lastException;
            return this;
        }
        
        public Builder failedStep(String failedStep) {
            this.failedStep = failedStep;
            return this;
        }
        
        public Builder totalElapsedTimeMs(long totalElapsedTimeMs) {
            this.totalElapsedTimeMs = totalElapsedTimeMs;
            return this;
        }
        
        public RetryContext build() {
            if (maxAttempts > 0) {
                this.isLastAttempt = (currentAttempt >= maxAttempts);
            }
            return new RetryContext(this);
        }
    }
}