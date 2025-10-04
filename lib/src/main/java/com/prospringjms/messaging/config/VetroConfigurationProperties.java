package com.prospringjms.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Advanced VETRO configuration properties with comprehensive settings.
 * Provides fine-grained control over all aspects of VETRO processing.
 */
@Configuration
@ConfigurationProperties(prefix = "jms.vetro")
public class VetroConfigurationProperties {
    
    private boolean enabled = true;
    private Processing processing = new Processing();
    private Map<String, ListenerConfig> listeners = new HashMap<>();
    private Validation validation = new Validation();
    private Enrichment enrichment = new Enrichment();
    private Transformation transformation = new Transformation();
    private Routing routing = new Routing();
    private ResponseHandling responseHandling = new ResponseHandling();
    private SessionManagement sessionManagement = new SessionManagement();
    private DeadLetterQueues deadLetterQueues = new DeadLetterQueues();
    private Monitoring monitoring = new Monitoring();
    private Tracing tracing = new Tracing();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public Processing getProcessing() { return processing; }
    public void setProcessing(Processing processing) { this.processing = processing; }
    
    public Map<String, ListenerConfig> getListeners() { return listeners; }
    public void setListeners(Map<String, ListenerConfig> listeners) { this.listeners = listeners; }
    
    public Validation getValidation() { return validation; }
    public void setValidation(Validation validation) { this.validation = validation; }
    
    public Enrichment getEnrichment() { return enrichment; }
    public void setEnrichment(Enrichment enrichment) { this.enrichment = enrichment; }
    
    public Transformation getTransformation() { return transformation; }
    public void setTransformation(Transformation transformation) { this.transformation = transformation; }
    
    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }
    
    public ResponseHandling getResponseHandling() { return responseHandling; }
    public void setResponseHandling(ResponseHandling responseHandling) { this.responseHandling = responseHandling; }
    
    public SessionManagement getSessionManagement() { return sessionManagement; }
    public void setSessionManagement(SessionManagement sessionManagement) { this.sessionManagement = sessionManagement; }
    
    public DeadLetterQueues getDeadLetterQueues() { return deadLetterQueues; }
    public void setDeadLetterQueues(DeadLetterQueues deadLetterQueues) { this.deadLetterQueues = deadLetterQueues; }
    
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }
    
    public Tracing getTracing() { return tracing; }
    public void setTracing(Tracing tracing) { this.tracing = tracing; }
    
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    
    // Nested configuration classes
    public static class Processing {
        private Async async = new Async();
        private Timeout timeout = new Timeout();
        private ErrorHandling errorHandling = new ErrorHandling();
        
        // Getters and setters
        public Async getAsync() { return async; }
        public void setAsync(Async async) { this.async = async; }
        public Timeout getTimeout() { return timeout; }
        public void setTimeout(Timeout timeout) { this.timeout = timeout; }
        public ErrorHandling getErrorHandling() { return errorHandling; }
        public void setErrorHandling(ErrorHandling errorHandling) { this.errorHandling = errorHandling; }
        
        public static class Async {
            private boolean enabled = true;
            private int threadPoolSize = 20;
            private int queueCapacity = 1000;
            private Duration keepAliveTime = Duration.ofSeconds(60);
            
            // Getters and setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getThreadPoolSize() { return threadPoolSize; }
            public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
            public int getQueueCapacity() { return queueCapacity; }
            public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
            public Duration getKeepAliveTime() { return keepAliveTime; }
            public void setKeepAliveTime(Duration keepAliveTime) { this.keepAliveTime = keepAliveTime; }
        }
        
        public static class Timeout {
            private Duration validation = Duration.ofSeconds(5);
            private Duration enrichment = Duration.ofSeconds(10);
            private Duration transformation = Duration.ofSeconds(15);
            private Duration routing = Duration.ofSeconds(2);
            private Duration operation = Duration.ofSeconds(30);
            
            // Getters and setters
            public Duration getValidation() { return validation; }
            public void setValidation(Duration validation) { this.validation = validation; }
            public Duration getEnrichment() { return enrichment; }
            public void setEnrichment(Duration enrichment) { this.enrichment = enrichment; }
            public Duration getTransformation() { return transformation; }
            public void setTransformation(Duration transformation) { this.transformation = transformation; }
            public Duration getRouting() { return routing; }
            public void setRouting(Duration routing) { this.routing = routing; }
            public Duration getOperation() { return operation; }
            public void setOperation(Duration operation) { this.operation = operation; }
        }
        
        public static class ErrorHandling {
            private boolean retryFailedValidation = false;
            private boolean retryFailedEnrichment = true;
            private boolean retryFailedTransformation = true;
            private boolean retryFailedOperation = true;
            private int maxRetryAttempts = 3;
            private double retryBackoffMultiplier = 2.0;
            
            // Getters and setters
            public boolean isRetryFailedValidation() { return retryFailedValidation; }
            public void setRetryFailedValidation(boolean retryFailedValidation) { this.retryFailedValidation = retryFailedValidation; }
            public boolean isRetryFailedEnrichment() { return retryFailedEnrichment; }
            public void setRetryFailedEnrichment(boolean retryFailedEnrichment) { this.retryFailedEnrichment = retryFailedEnrichment; }
            public boolean isRetryFailedTransformation() { return retryFailedTransformation; }
            public void setRetryFailedTransformation(boolean retryFailedTransformation) { this.retryFailedTransformation = retryFailedTransformation; }
            public boolean isRetryFailedOperation() { return retryFailedOperation; }
            public void setRetryFailedOperation(boolean retryFailedOperation) { this.retryFailedOperation = retryFailedOperation; }
            public int getMaxRetryAttempts() { return maxRetryAttempts; }
            public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
            public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
            public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }
        }
    }
    
    public static class ListenerConfig {
        private String destination;
        private String datacenter = "primary";
        private String concurrency = "1-3";
        private boolean sessionTransacted = true;
        private String processorClass;
        private RetryConfig retry = new RetryConfig();
        
        // Getters and setters
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
        public String getDatacenter() { return datacenter; }
        public void setDatacenter(String datacenter) { this.datacenter = datacenter; }
        public String getConcurrency() { return concurrency; }
        public void setConcurrency(String concurrency) { this.concurrency = concurrency; }
        public boolean isSessionTransacted() { return sessionTransacted; }
        public void setSessionTransacted(boolean sessionTransacted) { this.sessionTransacted = sessionTransacted; }
        public String getProcessorClass() { return processorClass; }
        public void setProcessorClass(String processorClass) { this.processorClass = processorClass; }
        public RetryConfig getRetry() { return retry; }
        public void setRetry(RetryConfig retry) { this.retry = retry; }
        
        public static class RetryConfig {
            private int maxAttempts = 3;
            private long baseDelayMs = 1000;
            private double backoffMultiplier = 2.0;
            private boolean retryValidation = false;
            private boolean retryEnrichment = true;
            private boolean retryTransformation = true;
            private boolean retryRouting = true;
            private boolean retryOperation = true;
            
            // Getters and setters
            public int getMaxAttempts() { return maxAttempts; }
            public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
            public long getBaseDelayMs() { return baseDelayMs; }
            public void setBaseDelayMs(long baseDelayMs) { this.baseDelayMs = baseDelayMs; }
            public double getBackoffMultiplier() { return backoffMultiplier; }
            public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
            public boolean isRetryValidation() { return retryValidation; }
            public void setRetryValidation(boolean retryValidation) { this.retryValidation = retryValidation; }
            public boolean isRetryEnrichment() { return retryEnrichment; }
            public void setRetryEnrichment(boolean retryEnrichment) { this.retryEnrichment = retryEnrichment; }
            public boolean isRetryTransformation() { return retryTransformation; }
            public void setRetryTransformation(boolean retryTransformation) { this.retryTransformation = retryTransformation; }
            public boolean isRetryRouting() { return retryRouting; }
            public void setRetryRouting(boolean retryRouting) { this.retryRouting = retryRouting; }
            public boolean isRetryOperation() { return retryOperation; }
            public void setRetryOperation(boolean retryOperation) { this.retryOperation = retryOperation; }
        }
    }
    
    public static class SessionManagement {
        private boolean defaultTransacted = true;
        private String defaultAcknowledgeMode = "AUTO_ACKNOWLEDGE";
        private Timeout timeout = new Timeout();
        private Pooling pooling = new Pooling();
        
        // Getters and setters
        public boolean isDefaultTransacted() { return defaultTransacted; }
        public void setDefaultTransacted(boolean defaultTransacted) { this.defaultTransacted = defaultTransacted; }
        public String getDefaultAcknowledgeMode() { return defaultAcknowledgeMode; }
        public void setDefaultAcknowledgeMode(String defaultAcknowledgeMode) { this.defaultAcknowledgeMode = defaultAcknowledgeMode; }
        public Timeout getTimeout() { return timeout; }
        public void setTimeout(Timeout timeout) { this.timeout = timeout; }
        public Pooling getPooling() { return pooling; }
        public void setPooling(Pooling pooling) { this.pooling = pooling; }
        
        public static class Timeout {
            private Duration transaction = Duration.ofMinutes(5);
            private Duration idle = Duration.ofMinutes(10);
            
            public Duration getTransaction() { return transaction; }
            public void setTransaction(Duration transaction) { this.transaction = transaction; }
            public Duration getIdle() { return idle; }
            public void setIdle(Duration idle) { this.idle = idle; }
        }
        
        public static class Pooling {
            private boolean enabled = true;
            private int minSessions = 1;
            private int maxSessions = 20;
            private Duration idleTimeout = Duration.ofMinutes(5);
            
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getMinSessions() { return minSessions; }
            public void setMinSessions(int minSessions) { this.minSessions = minSessions; }
            public int getMaxSessions() { return maxSessions; }
            public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
            public Duration getIdleTimeout() { return idleTimeout; }
            public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        }
    }
    
    public static class Monitoring {
        private boolean enabled = true;
        private Metrics metrics = new Metrics();
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Metrics getMetrics() { return metrics; }
        public void setMetrics(Metrics metrics) { this.metrics = metrics; }
        
        public static class Metrics {
            private boolean processingTime = true;
            private boolean throughput = true;
            private boolean errorRate = true;
            private boolean validationFailures = true;
            
            public boolean isProcessingTime() { return processingTime; }
            public void setProcessingTime(boolean processingTime) { this.processingTime = processingTime; }
            public boolean isThroughput() { return throughput; }
            public void setThroughput(boolean throughput) { this.throughput = throughput; }
            public boolean isErrorRate() { return errorRate; }
            public void setErrorRate(boolean errorRate) { this.errorRate = errorRate; }
            public boolean isValidationFailures() { return validationFailures; }
            public void setValidationFailures(boolean validationFailures) { this.validationFailures = validationFailures; }
        }
    }
    
    public static class Tracing {
        private boolean enabled = true;
        private boolean includeMessagePayload = false;
        private boolean includeHeaders = true;
        private int maxPayloadSize = 1024;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isIncludeMessagePayload() { return includeMessagePayload; }
        public void setIncludeMessagePayload(boolean includeMessagePayload) { this.includeMessagePayload = includeMessagePayload; }
        public boolean isIncludeHeaders() { return includeHeaders; }
        public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }
        public int getMaxPayloadSize() { return maxPayloadSize; }
        public void setMaxPayloadSize(int maxPayloadSize) { this.maxPayloadSize = maxPayloadSize; }
    }
    
    public static class CircuitBreaker {
        private boolean enabled = true;
        private float failureRateThreshold = 50.0f;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int minimumNumberOfCalls = 10;
        private int slidingWindowSize = 20;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
    }
    
    // Placeholder classes for other nested configurations
    public static class Validation {
        // Add validation-specific configuration properties
    }
    
    public static class Enrichment {
        // Add enrichment-specific configuration properties
    }
    
    public static class Transformation {
        // Add transformation-specific configuration properties
    }
    
    public static class Routing {
        // Add routing-specific configuration properties
    }
    
    public static class ResponseHandling {
        // Add response handling-specific configuration properties
    }
    
    public static class DeadLetterQueues {
        private boolean enabled = true;
        private Default defaultConfig = new Default();
        private Map<String, ProcessorDlqConfig> processors = new HashMap<>();
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Default getDefaultConfig() { return defaultConfig; }
        public void setDefaultConfig(Default defaultConfig) { this.defaultConfig = defaultConfig; }
        public Map<String, ProcessorDlqConfig> getProcessors() { return processors; }
        public void setProcessors(Map<String, ProcessorDlqConfig> processors) { this.processors = processors; }
        
        public static class Default {
            private String destination = "vetro.processing.dlq";
            private String datacenter = "primary";
            
            public String getDestination() { return destination; }
            public void setDestination(String destination) { this.destination = destination; }
            public String getDatacenter() { return datacenter; }
            public void setDatacenter(String datacenter) { this.datacenter = datacenter; }
        }
        
        public static class ProcessorDlqConfig {
            private String destination;
            private String maxMessageSize = "1024kb";
            private String retentionPolicy = "7d";
            
            public String getDestination() { return destination; }
            public void setDestination(String destination) { this.destination = destination; }
            public String getMaxMessageSize() { return maxMessageSize; }
            public void setMaxMessageSize(String maxMessageSize) { this.maxMessageSize = maxMessageSize; }
            public String getRetentionPolicy() { return retentionPolicy; }
            public void setRetentionPolicy(String retentionPolicy) { this.retentionPolicy = retentionPolicy; }
        }
    }
}