package com.prospringjms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-datacenter JMS library configuration properties.
 * Supports datacenter affinity, failover, and load balancing strategies.
 */
@ConfigurationProperties(prefix = "jms.lib")
public class JmsLibraryProperties {

    private String primaryDatacenter;
    private FailoverConfig failover;
    private LoadBalancingConfig loadBalancing;
    private ResiliencyConfig resiliency;
    private HealthCheckConfig healthCheck;
    private Map<String, DataCenter> datacenters;

    // Getters and setters
    public String getPrimaryDatacenter() {
        return primaryDatacenter;
    }

    public void setPrimaryDatacenter(String primaryDatacenter) {
        this.primaryDatacenter = primaryDatacenter;
    }

    public FailoverConfig getFailover() {
        return failover;
    }

    public void setFailover(FailoverConfig failover) {
        this.failover = failover;
    }

    public LoadBalancingConfig getLoadBalancing() {
        return loadBalancing;
    }

    public void setLoadBalancing(LoadBalancingConfig loadBalancing) {
        this.loadBalancing = loadBalancing;
    }

    public ResiliencyConfig getResiliency() {
        return resiliency;
    }

    public void setResiliency(ResiliencyConfig resiliency) {
        this.resiliency = resiliency;
    }

    public HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheckConfig healthCheck) {
        this.healthCheck = healthCheck;
    }

    public Map<String, DataCenter> getDatacenters() {
        return datacenters;
    }

    public void setDatacenters(Map<String, DataCenter> datacenters) {
        this.datacenters = datacenters;
    }

    public static class DataCenter {
        private String type; // "ibmmq" or "artemis"
        private String host;
        private Integer port;
        private String queueManager; // For IBM MQ
        private String channel; // For IBM MQ
        private String username;
        private String password;
        private String clientId;
        private ConnectionPool connectionPool;
        private Map<String, String> queues;
        private Map<String, String> topics;
        private DataCenterAffinity affinity;
        private Integer priority = 100; // Lower number = higher priority
        private Boolean enabled = true;
        private HealthCheck healthCheck;

        // Getters and setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getQueueManager() {
            return queueManager;
        }

        public void setQueueManager(String queueManager) {
            this.queueManager = queueManager;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public ConnectionPool getConnectionPool() {
            return connectionPool;
        }

        public void setConnectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        public Map<String, String> getQueues() {
            return queues;
        }

        public void setQueues(Map<String, String> queues) {
            this.queues = queues;
        }

        public Map<String, String> getTopics() {
            return topics;
        }

        public void setTopics(Map<String, String> topics) {
            this.topics = topics;
        }

        public DataCenterAffinity getAffinity() {
            return affinity;
        }

        public void setAffinity(DataCenterAffinity affinity) {
            this.affinity = affinity;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public HealthCheck getHealthCheck() {
            return healthCheck;
        }

        public void setHealthCheck(HealthCheck healthCheck) {
            this.healthCheck = healthCheck;
        }

        public static class ConnectionPool {
            private Integer maxConnections = 10;
            private Integer idleTimeout = 30000;
            private Integer maxSessionsPerConnection = 10;
            private Integer connectionTimeout = 5000;
            private Integer reconnectInterval = 10000;

            public Integer getMaxConnections() {
                return maxConnections;
            }

            public void setMaxConnections(Integer maxConnections) {
                this.maxConnections = maxConnections;
            }

            public Integer getIdleTimeout() {
                return idleTimeout;
            }

            public void setIdleTimeout(Integer idleTimeout) {
                this.idleTimeout = idleTimeout;
            }

            public Integer getMaxSessionsPerConnection() {
                return maxSessionsPerConnection;
            }

            public void setMaxSessionsPerConnection(Integer maxSessionsPerConnection) {
                this.maxSessionsPerConnection = maxSessionsPerConnection;
            }

            public Integer getConnectionTimeout() {
                return connectionTimeout;
            }

            public void setConnectionTimeout(Integer connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
            }

            public Integer getReconnectInterval() {
                return reconnectInterval;
            }

            public void setReconnectInterval(Integer reconnectInterval) {
                this.reconnectInterval = reconnectInterval;
            }
        }

        public static class DataCenterAffinity {
            private String region;
            private String zone;
            private List<String> preferredDatacenters;
            private List<String> excludedDatacenters;

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public String getZone() {
                return zone;
            }

            public void setZone(String zone) {
                this.zone = zone;
            }

            public List<String> getPreferredDatacenters() {
                return preferredDatacenters;
            }

            public void setPreferredDatacenters(List<String> preferredDatacenters) {
                this.preferredDatacenters = preferredDatacenters;
            }

            public List<String> getExcludedDatacenters() {
                return excludedDatacenters;
            }

            public void setExcludedDatacenters(List<String> excludedDatacenters) {
                this.excludedDatacenters = excludedDatacenters;
            }
        }

        public static class HealthCheck {
            private Integer intervalMs = 30000;
            private Integer timeoutMs = 5000;
            private Integer failureThreshold = 3;
            private Boolean enabled = true;

            public Integer getIntervalMs() {
                return intervalMs;
            }

            public void setIntervalMs(Integer intervalMs) {
                this.intervalMs = intervalMs;
            }

            public Integer getTimeoutMs() {
                return timeoutMs;
            }

            public void setTimeoutMs(Integer timeoutMs) {
                this.timeoutMs = timeoutMs;
            }

            public Integer getFailureThreshold() {
                return failureThreshold;
            }

            public void setFailureThreshold(Integer failureThreshold) {
                this.failureThreshold = failureThreshold;
            }

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static class FailoverConfig {
        private Boolean enabled = true;
        private String strategy = "priority"; // priority, round-robin, nearest
        private Integer maxRetries = 3;
        private Integer retryDelayMs = 1000;
        private Boolean autoFailback = true;
        private Integer failbackDelayMs = 30000;
        private Boolean crossRegionEnabled = false;
        private Boolean excludeUnhealthyDatacenters = true;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Integer getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(Integer retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }

        public Boolean getAutoFailback() {
            return autoFailback;
        }

        public void setAutoFailback(Boolean autoFailback) {
            this.autoFailback = autoFailback;
        }

        public Integer getFailbackDelayMs() {
            return failbackDelayMs;
        }

        public void setFailbackDelayMs(Integer failbackDelayMs) {
            this.failbackDelayMs = failbackDelayMs;
        }

        public Boolean getCrossRegionEnabled() {
            return crossRegionEnabled;
        }

        public void setCrossRegionEnabled(Boolean crossRegionEnabled) {
            this.crossRegionEnabled = crossRegionEnabled;
        }

        public Boolean getExcludeUnhealthyDatacenters() {
            return excludeUnhealthyDatacenters;
        }

        public void setExcludeUnhealthyDatacenters(Boolean excludeUnhealthyDatacenters) {
            this.excludeUnhealthyDatacenters = excludeUnhealthyDatacenters;
        }
    }

    public static class LoadBalancingConfig {
        private Boolean enabled = true;
        private String strategy = "weighted-round-robin"; // round-robin, weighted-round-robin, least-connections, random
        private Boolean stickySession = false;
        private Map<String, Integer> weights = new HashMap<>();
        private Boolean healthCheckWeightAdjustment = true;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public Boolean getStickySession() {
            return stickySession;
        }

        public void setStickySession(Boolean stickySession) {
            this.stickySession = stickySession;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, Integer> getWeights() {
            return weights;
        }

        public void setWeights(Map<String, Integer> weights) {
            this.weights = weights;
        }

        public Boolean getHealthCheckWeightAdjustment() {
            return healthCheckWeightAdjustment;
        }

        public void setHealthCheckWeightAdjustment(Boolean healthCheckWeightAdjustment) {
            this.healthCheckWeightAdjustment = healthCheckWeightAdjustment;
        }
    }

    public static class ResiliencyConfig {
        private CircuitBreaker circuitBreaker;
        private RateLimiting rateLimiting;
        private BulkHead bulkHead;
        private Retry retry;

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public RateLimiting getRateLimiting() {
            return rateLimiting;
        }

        public void setRateLimiting(RateLimiting rateLimiting) {
            this.rateLimiting = rateLimiting;
        }

        public BulkHead getBulkHead() {
            return bulkHead;
        }

        public void setBulkHead(BulkHead bulkHead) {
            this.bulkHead = bulkHead;
        }

        public Retry getRetry() {
            return retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry;
        }

        public static class CircuitBreaker {
            private Boolean enabled = true;
            private Integer failureThreshold = 5;
            private Integer timeoutMs = 10000;
            private Integer resetTimeoutMs = 60000;

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Integer getFailureThreshold() {
                return failureThreshold;
            }

            public void setFailureThreshold(Integer failureThreshold) {
                this.failureThreshold = failureThreshold;
            }

            public Integer getTimeoutMs() {
                return timeoutMs;
            }

            public void setTimeoutMs(Integer timeoutMs) {
                this.timeoutMs = timeoutMs;
            }

            public Integer getResetTimeoutMs() {
                return resetTimeoutMs;
            }

            public void setResetTimeoutMs(Integer resetTimeoutMs) {
                this.resetTimeoutMs = resetTimeoutMs;
            }
        }

        public static class RateLimiting {
            private Boolean enabled = false;
            private Integer maxRequestsPerSecond = 1000;
            private Integer windowSizeMs = 1000;

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Integer getMaxRequestsPerSecond() {
                return maxRequestsPerSecond;
            }

            public void setMaxRequestsPerSecond(Integer maxRequestsPerSecond) {
                this.maxRequestsPerSecond = maxRequestsPerSecond;
            }

            public Integer getWindowSizeMs() {
                return windowSizeMs;
            }

            public void setWindowSizeMs(Integer windowSizeMs) {
                this.windowSizeMs = windowSizeMs;
            }
        }

        public static class BulkHead {
            private Boolean enabled = true;
            private Integer maxConcurrentCalls = 100;
            private Integer maxWaitMs = 5000;

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Integer getMaxConcurrentCalls() {
                return maxConcurrentCalls;
            }

            public void setMaxConcurrentCalls(Integer maxConcurrentCalls) {
                this.maxConcurrentCalls = maxConcurrentCalls;
            }

            public Integer getMaxWaitMs() {
                return maxWaitMs;
            }

            public void setMaxWaitMs(Integer maxWaitMs) {
                this.maxWaitMs = maxWaitMs;
            }
        }

        public static class Retry {
            private Boolean enabled = true;
            private Integer maxAttempts = 3;
            private Long initialDelayMs = 1000L;
            private Double backoffMultiplier = 2.0;
            private Long maxDelayMs = 30000L;
            private List<String> retryableExceptions = List.of("javax.jms.JMSException");

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Integer getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(Integer maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public Long getInitialDelayMs() {
                return initialDelayMs;
            }

            public void setInitialDelayMs(Long initialDelayMs) {
                this.initialDelayMs = initialDelayMs;
            }

            public Double getBackoffMultiplier() {
                return backoffMultiplier;
            }

            public void setBackoffMultiplier(Double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
            }

            public Long getMaxDelayMs() {
                return maxDelayMs;
            }

            public void setMaxDelayMs(Long maxDelayMs) {
                this.maxDelayMs = maxDelayMs;
            }

            public List<String> getRetryableExceptions() {
                return retryableExceptions;
            }

            public void setRetryableExceptions(List<String> retryableExceptions) {
                this.retryableExceptions = retryableExceptions;
            }
        }
    }

    /**
     * Health check configuration for monitoring datacenter connectivity.
     */
    public static class HealthCheckConfig {
        private Boolean enabled = true;
        private Long intervalMs = 30000L; // 30 seconds default
        private Long timeoutMs = 5000L; // 5 seconds timeout
        private Integer retryCount = 3;
        private Long retryDelayMs = 1000L;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(Long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public Long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Integer getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }

        public Long getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(Long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }
    }
}