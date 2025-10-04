package com.prospringjms.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the connector module.
 */
@ConfigurationProperties(prefix = "prospringjms.connector")
public class ConnectorProperties {
    
    private RestConfig rest = new RestConfig();
    private JmsConfig jms = new JmsConfig();
    private KafkaConfig kafka = new KafkaConfig();
    private GraphQlConfig graphql = new GraphQlConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private RetryConfig retry = new RetryConfig();
    private BulkheadConfig bulkhead = new BulkheadConfig();
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();
    private TimeLimiterConfig timeLimiter = new TimeLimiterConfig();
    
    // Getters and setters
    public RestConfig getRest() { return rest; }
    public void setRest(RestConfig rest) { this.rest = rest; }
    
    public JmsConfig getJms() { return jms; }
    public void setJms(JmsConfig jms) { this.jms = jms; }
    
    public KafkaConfig getKafka() { return kafka; }
    public void setKafka(KafkaConfig kafka) { this.kafka = kafka; }
    
    public GraphQlConfig getGraphql() { return graphql; }
    public void setGraphql(GraphQlConfig graphql) { this.graphql = graphql; }
    
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    
    public RetryConfig getRetry() { return retry; }
    public void setRetry(RetryConfig retry) { this.retry = retry; }
    
    public BulkheadConfig getBulkhead() { return bulkhead; }
    public void setBulkhead(BulkheadConfig bulkhead) { this.bulkhead = bulkhead; }
    
    public RateLimiterConfig getRateLimiter() { return rateLimiter; }
    public void setRateLimiter(RateLimiterConfig rateLimiter) { this.rateLimiter = rateLimiter; }
    
    public TimeLimiterConfig getTimeLimiter() { return timeLimiter; }
    public void setTimeLimiter(TimeLimiterConfig timeLimiter) { this.timeLimiter = timeLimiter; }
    
    public static class RestConfig {
        private boolean enabled = true;
        private int maxInMemorySize = 1048576; // 1MB
        private String defaultTimeout = "PT30S";
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMaxInMemorySize() { return maxInMemorySize; }
        public void setMaxInMemorySize(int maxInMemorySize) { this.maxInMemorySize = maxInMemorySize; }
        
        public String getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(String defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    }
    
    public static class JmsConfig {
        private boolean enabled = true;
        private String defaultTimeout = "PT30S";
        private String correlationIdHeader = "JMSCorrelationID";
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(String defaultTimeout) { this.defaultTimeout = defaultTimeout; }
        
        public String getCorrelationIdHeader() { return correlationIdHeader; }
        public void setCorrelationIdHeader(String correlationIdHeader) { this.correlationIdHeader = correlationIdHeader; }
    }
    
    public static class KafkaConfig {
        private boolean enabled = true;
        private String defaultTimeout = "PT30S";
        private String acks = "all";
        private int retries = 3;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(String defaultTimeout) { this.defaultTimeout = defaultTimeout; }
        
        public String getAcks() { return acks; }
        public void setAcks(String acks) { this.acks = acks; }
        
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
    }
    
    public static class GraphQlConfig {
        private boolean enabled = true;
        private int maxInMemorySize = 2097152; // 2MB
        private String defaultEndpoint = "http://localhost:8080/graphql";
        private String defaultTimeout = "PT30S";
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMaxInMemorySize() { return maxInMemorySize; }
        public void setMaxInMemorySize(int maxInMemorySize) { this.maxInMemorySize = maxInMemorySize; }
        
        public String getDefaultEndpoint() { return defaultEndpoint; }
        public void setDefaultEndpoint(String defaultEndpoint) { this.defaultEndpoint = defaultEndpoint; }
        
        public String getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(String defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    }
    
    public static class CircuitBreakerConfig {
        private float failureRateThreshold = 50.0f;
        private String slowCallDurationThreshold = "PT2S";
        private float slowCallRateThreshold = 50.0f;
        private String waitDurationInOpenState = "PT30S";
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        
        // Getters and setters
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        
        public String getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public void setSlowCallDurationThreshold(String slowCallDurationThreshold) { this.slowCallDurationThreshold = slowCallDurationThreshold; }
        
        public float getSlowCallRateThreshold() { return slowCallRateThreshold; }
        public void setSlowCallRateThreshold(float slowCallRateThreshold) { this.slowCallRateThreshold = slowCallRateThreshold; }
        
        public String getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(String waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }
        
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
    }
    
    public static class RetryConfig {
        private int maxAttempts = 3;
        private String waitDuration = "PT1S";
        private double exponentialBackoffMultiplier = 2.0;
        
        // Getters and setters
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public String getWaitDuration() { return waitDuration; }
        public void setWaitDuration(String waitDuration) { this.waitDuration = waitDuration; }
        
        public double getExponentialBackoffMultiplier() { return exponentialBackoffMultiplier; }
        public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) { this.exponentialBackoffMultiplier = exponentialBackoffMultiplier; }
    }
    
    public static class BulkheadConfig {
        private int maxConcurrentCalls = 10;
        private String maxWaitDuration = "PT5S";
        
        // Getters and setters
        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }
        
        public String getMaxWaitDuration() { return maxWaitDuration; }
        public void setMaxWaitDuration(String maxWaitDuration) { this.maxWaitDuration = maxWaitDuration; }
    }
    
    public static class RateLimiterConfig {
        private int limitForPeriod = 100;
        private String limitRefreshPeriod = "PT1M";
        private String timeoutDuration = "PT10S";
        
        // Getters and setters
        public int getLimitForPeriod() { return limitForPeriod; }
        public void setLimitForPeriod(int limitForPeriod) { this.limitForPeriod = limitForPeriod; }
        
        public String getLimitRefreshPeriod() { return limitRefreshPeriod; }
        public void setLimitRefreshPeriod(String limitRefreshPeriod) { this.limitRefreshPeriod = limitRefreshPeriod; }
        
        public String getTimeoutDuration() { return timeoutDuration; }
        public void setTimeoutDuration(String timeoutDuration) { this.timeoutDuration = timeoutDuration; }
    }
    
    public static class TimeLimiterConfig {
        private String timeoutDuration = "PT5S";
        private boolean cancelRunningFuture = true;
        
        // Getters and setters
        public String getTimeoutDuration() { return timeoutDuration; }
        public void setTimeoutDuration(String timeoutDuration) { this.timeoutDuration = timeoutDuration; }
        
        public boolean isCancelRunningFuture() { return cancelRunningFuture; }
        public void setCancelRunningFuture(boolean cancelRunningFuture) { this.cancelRunningFuture = cancelRunningFuture; }
    }
}