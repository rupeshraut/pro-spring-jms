package com.prospringjms.health;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.routing.DatacenterRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Advanced health check manager with sophisticated monitoring capabilities.
 * Provides deep health validation, performance metrics, and proactive failure detection.
 */
@Component
public class HealthCheckManager {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckManager.class);
    
    private final JmsLibraryProperties properties;
    private final DatacenterRouter router;
    private final Map<String, ConnectionFactory> connectionFactories;
    private final ScheduledExecutorService healthScheduler;
    private final ExecutorService healthCheckExecutor;
    private final Map<String, DatacenterHealthMetrics> healthMetrics = new ConcurrentHashMap<>();
    
    public HealthCheckManager(JmsLibraryProperties properties,
                             DatacenterRouter router,
                             Map<String, ConnectionFactory> connectionFactories) {
        this.properties = properties;
        this.router = router;
        this.connectionFactories = connectionFactories;
        this.healthScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "JMS-HealthCheck-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.healthCheckExecutor = Executors.newFixedThreadPool(
            Math.max(2, connectionFactories.size()), 
            r -> {
                Thread t = new Thread(r, "JMS-HealthCheck-Worker");
                t.setDaemon(true);
                return t;
            }
        );
        
        initializeHealthMonitoring();
    }
    
    /**
     * Initializes health monitoring for all configured datacenters.
     */
    private void initializeHealthMonitoring() {
        if (properties.getHealthCheck() == null || !properties.getHealthCheck().getEnabled()) {
            logger.info("Health monitoring is disabled");
            return;
        }
        
        // Initialize health metrics for all datacenters
        connectionFactories.keySet().forEach(datacenter -> {
            DatacenterHealthMetrics metrics = new DatacenterHealthMetrics(datacenter);
            healthMetrics.put(datacenter, metrics);
        });
        
        long intervalMs = properties.getHealthCheck().getIntervalMs();
        
        // Schedule periodic health checks
        healthScheduler.scheduleAtFixedRate(
            this::performAllHealthChecks, 
            0, 
            intervalMs, 
            TimeUnit.MILLISECONDS
        );
        
        // Schedule performance analysis
        healthScheduler.scheduleAtFixedRate(
            this::analyzePerformanceMetrics,
            intervalMs * 5, // Start after initial checks
            intervalMs * 10, // Run less frequently
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Advanced health monitoring initialized with {} ms interval for {} datacenters", 
            intervalMs, connectionFactories.size());
    }
    
    /**
     * Performs health checks for all datacenters concurrently.
     */
    private void performAllHealthChecks() {
        logger.debug("Starting health checks for {} datacenters", connectionFactories.size());
        
        CompletableFuture<Void> allChecks = CompletableFuture.allOf(
            connectionFactories.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(
                    () -> performDatacenterHealthCheck(entry.getKey(), entry.getValue()),
                    healthCheckExecutor
                ))
                .toArray(CompletableFuture[]::new)
        );
        
        try {
            // Wait for all health checks to complete with timeout
            allChecks.get(properties.getHealthCheck().getTimeoutMs() * 2, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("Health check batch execution failed", e);
        }
    }
    
    /**
     * Performs comprehensive health check for a single datacenter.
     */
    private void performDatacenterHealthCheck(String datacenter, ConnectionFactory connectionFactory) {
        DatacenterHealthMetrics metrics = healthMetrics.get(datacenter);
        if (metrics == null) {
            logger.warn("No health metrics found for datacenter: {}", datacenter);
            return;
        }
        
        Instant checkStart = Instant.now();
        boolean healthy = false;
        String errorMessage = null;
        long responseTime = 0;
        
        try {
            // Perform deep health validation
            HealthCheckResult result = performDeepHealthCheck(datacenter, connectionFactory);
            healthy = result.isHealthy();
            errorMessage = result.getErrorMessage();
            responseTime = result.getResponseTimeMs();
            
            if (healthy) {
                metrics.recordSuccess(responseTime);
                router.updateDatacenterHealth(datacenter, true);
                logger.debug("Health check passed for datacenter: {} ({}ms)", datacenter, responseTime);
            } else {
                metrics.recordFailure(errorMessage);
                router.updateDatacenterHealth(datacenter, false);
                logger.warn("Health check failed for datacenter: {} - {}", datacenter, errorMessage);
            }
            
        } catch (Exception e) {
            errorMessage = "Health check exception: " + e.getMessage();
            metrics.recordFailure(errorMessage);
            router.updateDatacenterHealth(datacenter, false);
            logger.error("Health check error for datacenter: {}", datacenter, e);
        }
        
        // Update performance metrics
        Duration checkDuration = Duration.between(checkStart, Instant.now());
        metrics.updateLastCheck(checkDuration.toMillis(), healthy, errorMessage);
        
        // Trigger alerts if necessary
        checkForHealthAlerts(datacenter, metrics);
    }
    
    /**
     * Performs deep health validation including connection, session, and message broker tests.
     */
    private HealthCheckResult performDeepHealthCheck(String datacenter, ConnectionFactory connectionFactory) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Level 1: Connection test
            try (Connection connection = connectionFactory.createConnection()) {
                connection.start();
                
                // Level 2: Session test
                try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    
                    // Level 3: Broker connectivity test
                    // Test queue creation/lookup
                    try {
                        session.createTemporaryQueue();
                    } catch (JMSException e) {
                        return new HealthCheckResult(false, 
                            System.currentTimeMillis() - startTime,
                            "Failed to create temporary queue: " + e.getMessage());
                    }
                    
                    // Level 4: Performance validation
                    long responseTime = System.currentTimeMillis() - startTime;
                    long timeoutMs = properties.getHealthCheck().getTimeoutMs();
                    
                    if (responseTime > timeoutMs) {
                        return new HealthCheckResult(false, responseTime,
                            String.format("Health check timeout: %dms > %dms", responseTime, timeoutMs));
                    }
                    
                    return new HealthCheckResult(true, responseTime, null);
                }
            }
        } catch (JMSException e) {
            return new HealthCheckResult(false, 
                System.currentTimeMillis() - startTime,
                "JMS connection failed: " + e.getMessage());
        } catch (Exception e) {
            return new HealthCheckResult(false, 
                System.currentTimeMillis() - startTime,
                "Unexpected health check error: " + e.getMessage());
        }
    }
    
    /**
     * Analyzes performance metrics and adjusts routing behavior.
     */
    private void analyzePerformanceMetrics() {
        logger.debug("Analyzing performance metrics for {} datacenters", healthMetrics.size());
        
        healthMetrics.forEach((datacenter, metrics) -> {
            // Check for performance degradation
            if (metrics.getConsecutiveSlowChecks() >= 3) {
                logger.warn("Performance degradation detected for datacenter: {} (avg: {}ms)", 
                    datacenter, metrics.getAverageResponseTime());
                
                // Optionally reduce routing weight
                adjustDatacenterWeight(datacenter, 0.8f);
            }
            
            // Check for recovery
            if (metrics.getConsecutiveSuccesses() >= 5 && metrics.getAverageResponseTime() < 100) {
                logger.info("Performance recovery detected for datacenter: {}", datacenter);
                adjustDatacenterWeight(datacenter, 1.0f);
            }
        });
    }
    
    /**
     * Checks for health alerts and triggers notifications.
     */
    private void checkForHealthAlerts(String datacenter, DatacenterHealthMetrics metrics) {
        // Critical: Multiple consecutive failures
        if (metrics.getConsecutiveFailures() >= 3) {
            logger.error("CRITICAL: Datacenter {} has {} consecutive failures", 
                datacenter, metrics.getConsecutiveFailures());
        }
        
        // Warning: Intermittent failures
        if (metrics.getFailureRate() > 0.2 && metrics.getTotalChecks() > 10) {
            logger.warn("WARNING: Datacenter {} has high failure rate: {:.1%}", 
                datacenter, metrics.getFailureRate());
        }
        
        // Info: Recovery after failures
        if (metrics.getConsecutiveSuccesses() == 3 && metrics.getConsecutiveFailures() > 0) {
            logger.info("INFO: Datacenter {} is recovering (3 consecutive successes)", datacenter);
        }
    }
    
    /**
     * Adjusts datacenter routing weight based on performance.
     */
    private void adjustDatacenterWeight(String datacenter, float weightMultiplier) {
        // This would integrate with load balancing configuration
        logger.debug("Adjusting weight for datacenter {} by factor {}", datacenter, weightMultiplier);
        // Implementation would depend on load balancer integration
    }
    
    /**
     * Gets comprehensive health metrics for all datacenters.
     */
    public Map<String, DatacenterHealthMetrics> getAllHealthMetrics() {
        return Map.copyOf(healthMetrics);
    }
    
    /**
     * Gets health metrics for a specific datacenter.
     */
    public DatacenterHealthMetrics getHealthMetrics(String datacenter) {
        return healthMetrics.get(datacenter);
    }
    
    /**
     * Forces an immediate health check for all datacenters.
     */
    public CompletableFuture<Void> forceHealthCheck() {
        return CompletableFuture.runAsync(this::performAllHealthChecks, healthCheckExecutor);
    }
    
    /**
     * Shuts down health monitoring gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down health monitoring");
        healthScheduler.shutdown();
        healthCheckExecutor.shutdown();
        
        try {
            if (!healthScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                healthScheduler.shutdownNow();
            }
            if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            healthScheduler.shutdownNow();
            healthCheckExecutor.shutdownNow();
        }
    }
    
    /**
     * Result of a health check operation.
     */
    public static class HealthCheckResult {
        private final boolean healthy;
        private final long responseTimeMs;
        private final String errorMessage;
        
        public HealthCheckResult(boolean healthy, long responseTimeMs, String errorMessage) {
            this.healthy = healthy;
            this.responseTimeMs = responseTimeMs;
            this.errorMessage = errorMessage;
        }
        
        public boolean isHealthy() { return healthy; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Comprehensive health metrics for a datacenter.
     */
    public static class DatacenterHealthMetrics {
        private final String datacenter;
        private volatile long totalChecks = 0;
        private volatile long successfulChecks = 0;
        private volatile long consecutiveSuccesses = 0;
        private volatile long consecutiveFailures = 0;
        private volatile long consecutiveSlowChecks = 0;
        private volatile long totalResponseTime = 0;
        private volatile long lastCheckTime = 0;
        private volatile boolean lastCheckHealthy = true;
        private volatile String lastError = null;
        
        public DatacenterHealthMetrics(String datacenter) {
            this.datacenter = datacenter;
        }
        
        public synchronized void recordSuccess(long responseTime) {
            totalChecks++;
            successfulChecks++;
            consecutiveSuccesses++;
            consecutiveFailures = 0;
            totalResponseTime += responseTime;
            lastCheckTime = System.currentTimeMillis();
            lastCheckHealthy = true;
            lastError = null;
            
            // Track slow checks (> 1 second)
            if (responseTime > 1000) {
                consecutiveSlowChecks++;
            } else {
                consecutiveSlowChecks = 0;
            }
        }
        
        public synchronized void recordFailure(String error) {
            totalChecks++;
            consecutiveFailures++;
            consecutiveSuccesses = 0;
            consecutiveSlowChecks = 0;
            lastCheckTime = System.currentTimeMillis();
            lastCheckHealthy = false;
            lastError = error;
        }
        
        public synchronized void updateLastCheck(long responseTime, boolean healthy, String error) {
            // Additional update method for external usage
        }
        
        // Getters
        public String getDatacenter() { return datacenter; }
        public long getTotalChecks() { return totalChecks; }
        public long getSuccessfulChecks() { return successfulChecks; }
        public long getConsecutiveSuccesses() { return consecutiveSuccesses; }
        public long getConsecutiveFailures() { return consecutiveFailures; }
        public long getConsecutiveSlowChecks() { return consecutiveSlowChecks; }
        public long getLastCheckTime() { return lastCheckTime; }
        public boolean isLastCheckHealthy() { return lastCheckHealthy; }
        public String getLastError() { return lastError; }
        
        public double getFailureRate() {
            return totalChecks > 0 ? (double) (totalChecks - successfulChecks) / totalChecks : 0.0;
        }
        
        public double getAverageResponseTime() {
            return successfulChecks > 0 ? (double) totalResponseTime / successfulChecks : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("HealthMetrics{dc='%s', total=%d, success=%d, failures=%d, avgTime=%.1fms, failureRate=%.1%%}",
                datacenter, totalChecks, successfulChecks, consecutiveFailures, 
                getAverageResponseTime(), getFailureRate() * 100);
        }
    }
}