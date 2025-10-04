package com.prospringjms.connector.core;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Connection context for backend connectors containing routing information,
 * configuration, headers, and other metadata required for backend communication.
 */
public class ConnectorContext {
    
    private final String endpoint;
    private final String datacenter;
    private final Map<String, Object> headers;
    private final Map<String, Object> properties;
    private final Duration timeout;
    private final String correlationId;
    private final boolean async;
    private final RetryConfig retryConfig;
    private final CircuitBreakerConfig circuitBreakerConfig;
    
    private ConnectorContext(Builder builder) {
        this.endpoint = builder.endpoint;
        this.datacenter = builder.datacenter;
        this.headers = new HashMap<>(builder.headers);
        this.properties = new HashMap<>(builder.properties);
        this.timeout = builder.timeout;
        this.correlationId = builder.correlationId;
        this.async = builder.async;
        this.retryConfig = builder.retryConfig;
        this.circuitBreakerConfig = builder.circuitBreakerConfig;
    }
    
    // Getters
    public String getEndpoint() { return endpoint; }
    public String getDatacenter() { return datacenter; }
    public Map<String, Object> getHeaders() { return new HashMap<>(headers); }
    public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    public Duration getTimeout() { return timeout; }
    public String getCorrelationId() { return correlationId; }
    public boolean isAsync() { return async; }
    public RetryConfig getRetryConfig() { return retryConfig; }
    public CircuitBreakerConfig getCircuitBreakerConfig() { return circuitBreakerConfig; }
    
    public Object getHeader(String key) { return headers.get(key); }
    public Object getProperty(String key) { return properties.get(key); }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String endpoint;
        private String datacenter = "primary";
        private Map<String, Object> headers = new HashMap<>();
        private Map<String, Object> properties = new HashMap<>();
        private Duration timeout = Duration.ofSeconds(30);
        private String correlationId;
        private boolean async = false;
        private RetryConfig retryConfig = RetryConfig.defaultConfig();
        private CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.defaultConfig();
        
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public Builder datacenter(String datacenter) {
            this.datacenter = datacenter;
            return this;
        }
        
        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Builder headers(Map<String, Object> headers) {
            this.headers.putAll(headers);
            return this;
        }
        
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            this.properties.putAll(properties);
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder async(boolean async) {
            this.async = async;
            return this;
        }
        
        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }
        
        public Builder circuitBreakerConfig(CircuitBreakerConfig circuitBreakerConfig) {
            this.circuitBreakerConfig = circuitBreakerConfig;
            return this;
        }
        
        public ConnectorContext build() {
            if (endpoint == null) {
                throw new IllegalArgumentException("Endpoint is required");
            }
            return new ConnectorContext(this);
        }
    }
    
    /**
     * Retry configuration for resilience patterns.
     */
    public static class RetryConfig {
        private final int maxAttempts;
        private final Duration waitDuration;
        private final double exponentialBackoffMultiplier;
        private final Duration maxWaitDuration;
        
        public RetryConfig(int maxAttempts, Duration waitDuration, 
                          double exponentialBackoffMultiplier, Duration maxWaitDuration) {
            this.maxAttempts = maxAttempts;
            this.waitDuration = waitDuration;
            this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
            this.maxWaitDuration = maxWaitDuration;
        }
        
        public static RetryConfig defaultConfig() {
            return new RetryConfig(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10));
        }
        
        public int getMaxAttempts() { return maxAttempts; }
        public Duration getWaitDuration() { return waitDuration; }
        public double getExponentialBackoffMultiplier() { return exponentialBackoffMultiplier; }
        public Duration getMaxWaitDuration() { return maxWaitDuration; }
    }
    
    /**
     * Circuit breaker configuration for resilience patterns.
     */
    public static class CircuitBreakerConfig {
        private final int failureRateThreshold;
        private final int slowCallRateThreshold;
        private final Duration slowCallDurationThreshold;
        private final int minimumNumberOfCalls;
        private final int slidingWindowSize;
        private final Duration waitDurationInOpenState;
        private final int permittedNumberOfCallsInHalfOpenState;
        
        public CircuitBreakerConfig(int failureRateThreshold, int slowCallRateThreshold,
                                  Duration slowCallDurationThreshold, int minimumNumberOfCalls,
                                  int slidingWindowSize, Duration waitDurationInOpenState,
                                  int permittedNumberOfCallsInHalfOpenState) {
            this.failureRateThreshold = failureRateThreshold;
            this.slowCallRateThreshold = slowCallRateThreshold;
            this.slowCallDurationThreshold = slowCallDurationThreshold;
            this.minimumNumberOfCalls = minimumNumberOfCalls;
            this.slidingWindowSize = slidingWindowSize;
            this.waitDurationInOpenState = waitDurationInOpenState;
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
        
        public static CircuitBreakerConfig defaultConfig() {
            return new CircuitBreakerConfig(
                50, // 50% failure rate threshold
                100, // 100% slow call rate threshold
                Duration.ofSeconds(5), // 5 seconds slow call threshold
                10, // minimum 10 calls
                20, // sliding window of 20 calls
                Duration.ofSeconds(30), // wait 30 seconds in open state
                5 // 5 calls allowed in half-open state
            );
        }
        
        public int getFailureRateThreshold() { return failureRateThreshold; }
        public int getSlowCallRateThreshold() { return slowCallRateThreshold; }
        public Duration getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }
    }
}