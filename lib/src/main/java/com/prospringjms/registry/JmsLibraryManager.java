package com.prospringjms.registry;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import com.prospringjms.health.HealthCheckManager;
import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.resilience.Resilience4jManager;
import com.prospringjms.routing.DatacenterRouter;
import com.prospringjms.security.SecurityManager;
import com.prospringjms.sender.ResilientJmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.ConnectionFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central registry and manager for JMS library components.
 * Provides lifecycle management, health monitoring, and centralized access to all library features.
 */
@Component
public class JmsLibraryManager implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsLibraryManager.class);
    
    private final JmsLibraryProperties properties;
    private final DatacenterRouter router;
    private final ResilientJmsSender sender;
    private final JmsListenerRegistry listenerRegistry;
    private final Map<String, ConnectionFactory> connectionFactories;
    private final Map<String, JmsTemplate> jmsTemplates;
    private final ScheduledExecutorService healthMonitor;
    private final Map<String, DatacenterHealth> datacenterHealthMap = new ConcurrentHashMap<>();
    
    // Phase 2: Enhanced production components
    private final HealthCheckManager healthCheckManager;
    private final Resilience4jManager resilienceManager;
    private final SecurityManager securityManager;
    
    public JmsLibraryManager(JmsLibraryProperties properties,
                            DatacenterRouter router,
                            ResilientJmsSender sender,
                            JmsListenerRegistry listenerRegistry,
                            Map<String, ConnectionFactory> connectionFactories,
                            Map<String, JmsTemplate> jmsTemplates,
                            Resilience4jManager resilienceManager) {
        this.properties = properties;
        this.router = router;
        this.sender = sender;
        this.listenerRegistry = listenerRegistry;
        this.connectionFactories = connectionFactories;
        this.jmsTemplates = jmsTemplates;
        this.healthMonitor = Executors.newScheduledThreadPool(2);
        
        // Initialize enhanced production components
        this.healthCheckManager = new HealthCheckManager(properties, router, connectionFactories);
        this.resilienceManager = resilienceManager;
        this.securityManager = new SecurityManager(properties);
        
        initializeHealthMonitoring();
        logger.info("JMS Library Manager initialized with {} datacenters and enhanced production features", 
            properties.getDatacenters() != null ? properties.getDatacenters().size() : 0);
    }
    
    /**
     * Gets the resilient JMS sender.
     */
    public ResilientJmsSender getSender() {
        return sender;
    }
    
    /**
     * Gets the JMS listener registry.
     */
    public JmsListenerRegistry getListenerRegistry() {
        return listenerRegistry;
    }
    
    /**
     * Gets the datacenter router.
     */
    public DatacenterRouter getRouter() {
        return router;
    }
    
    /**
     * Gets the health check manager.
     */
    public HealthCheckManager getHealthCheckManager() {
        return healthCheckManager;
    }
    
    /**
     * Gets the Resilience4j manager.
     */
    public Resilience4jManager getResilienceManager() {
        return resilienceManager;
    }
    
    /**
     * Gets the security manager.
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
    
    /**
     * Gets a JMS template for a specific datacenter.
     */
    public JmsTemplate getJmsTemplate(String datacenter) throws JmsLibraryException {
        JmsTemplate template = jmsTemplates.get(datacenter);
        if (template == null) {
            throw new JmsLibraryException("No JMS template found for datacenter: " + datacenter);
        }
        return template;
    }
    
    /**
     * Gets a connection factory for a specific datacenter.
     */
    public ConnectionFactory getConnectionFactory(String datacenter) throws JmsLibraryException {
        ConnectionFactory factory = connectionFactories.get(datacenter);
        if (factory == null) {
            throw new JmsLibraryException("No connection factory found for datacenter: " + datacenter);
        }
        return factory;
    }
    
    /**
     * Gets all available datacenters.
     */
    public List<String> getAvailableDatacenters() {
        return List.copyOf(connectionFactories.keySet());
    }
    
    /**
     * Gets the primary datacenter.
     */
    public String getPrimaryDatacenter() {
        return properties.getPrimaryDatacenter();
    }
    
    /**
     * Performs a failover from one datacenter to another.
     */
    public void performFailover(String fromDatacenter, String toDatacenter) throws JmsLibraryException {
        logger.info("Performing failover from {} to {}", fromDatacenter, toDatacenter);
        
        // Stop listeners on the failed datacenter
        listenerRegistry.stopDatacenterListeners(fromDatacenter);
        
        // Update router health
        router.updateDatacenterHealth(fromDatacenter, false);
        router.updateDatacenterHealth(toDatacenter, true);
        
        // Start listeners on the target datacenter (if configured for failover)
        if (properties.getFailover() != null && properties.getFailover().getEnabled()) {
            listenerRegistry.startDatacenterListeners(toDatacenter);
        }
        
        logger.info("Failover completed from {} to {}", fromDatacenter, toDatacenter);
    }
    
    /**
     * Performs a graceful shutdown of all components.
     */
    public void shutdown() {
        logger.info("Shutting down JMS Library Manager");
        
        // Stop health monitoring
        healthMonitor.shutdown();
        
        // Stop all listeners
        listenerRegistry.getAllListeners().keySet().forEach(listenerId -> {
            try {
                listenerRegistry.unregisterListener(listenerId);
            } catch (Exception e) {
                logger.error("Error unregistering listener: {}", listenerId, e);
            }
        });
        
        logger.info("JMS Library Manager shutdown completed");
    }
    
    /**
     * Gets comprehensive health status for all datacenters.
     */
    public JmsLibraryHealth getHealthStatus() {
        Map<String, DatacenterHealth> healthMap = new ConcurrentHashMap<>(datacenterHealthMap);
        
        boolean allHealthy = healthMap.values().stream()
            .allMatch(DatacenterHealth::isHealthy);
        
        long totalListeners = listenerRegistry.getAllListeners().values().stream()
            .mapToLong(status -> status.isRunning() ? 1 : 0)
            .sum();
        
        return new JmsLibraryHealth(
            allHealthy,
            healthMap,
            totalListeners,
            properties.getPrimaryDatacenter()
        );
    }
    
    @Override
    public Health health() {
        JmsLibraryHealth healthStatus = getHealthStatus();
        
        Health.Builder builder = healthStatus.isOverallHealthy() ? 
            Health.up() : Health.down();
        
        builder.withDetail("primaryDatacenter", healthStatus.getPrimaryDatacenter())
               .withDetail("totalRunningListeners", healthStatus.getTotalRunningListeners())
               .withDetail("datacenters", healthStatus.getDatacenterHealth());
        
        return builder.build();
    }
    
    private void initializeHealthMonitoring() {
        if (properties.getHealthCheck() == null || !properties.getHealthCheck().getEnabled()) {
            return;
        }
        
        long intervalMs = properties.getHealthCheck().getIntervalMs();
        
        // Initialize health status for all datacenters
        connectionFactories.keySet().forEach(datacenter -> 
            datacenterHealthMap.put(datacenter, new DatacenterHealth(datacenter, true, System.currentTimeMillis())));
        
        // Schedule periodic health checks
        healthMonitor.scheduleAtFixedRate(this::performHealthChecks, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        
        logger.info("Health monitoring initialized with {} ms interval", intervalMs);
    }
    
    private void performHealthChecks() {
        connectionFactories.forEach((datacenter, connectionFactory) -> {
            try {
                // Simple health check by creating a connection
                connectionFactory.createConnection().close();
                
                DatacenterHealth health = new DatacenterHealth(datacenter, true, System.currentTimeMillis());
                datacenterHealthMap.put(datacenter, health);
                router.updateDatacenterHealth(datacenter, true);
                
            } catch (Exception e) {
                logger.warn("Health check failed for datacenter: {}", datacenter, e);
                
                DatacenterHealth health = new DatacenterHealth(datacenter, false, System.currentTimeMillis(), e.getMessage());
                datacenterHealthMap.put(datacenter, health);
                router.updateDatacenterHealth(datacenter, false);
                
                // Trigger failover if primary datacenter is unhealthy
                if (datacenter.equals(properties.getPrimaryDatacenter()) && 
                    properties.getFailover() != null && properties.getFailover().getEnabled()) {
                    triggerFailover(datacenter);
                }
            }
        });
    }
    
    private void triggerFailover(String unhealthyDatacenter) {
        try {
            List<String> failoverCandidates = router.getFailoverDatacenters(unhealthyDatacenter);
            
            if (!failoverCandidates.isEmpty()) {
                String targetDatacenter = failoverCandidates.get(0);
                performFailover(unhealthyDatacenter, targetDatacenter);
            } else {
                logger.error("No healthy failover datacenter available for: {}", unhealthyDatacenter);
            }
        } catch (Exception e) {
            logger.error("Failed to trigger failover for datacenter: {}", unhealthyDatacenter, e);
        }
    }
    
    /**
     * Health information for a datacenter.
     */
    public static class DatacenterHealth {
        private final String datacenter;
        private final boolean healthy;
        private final long lastCheckTime;
        private final String error;
        
        public DatacenterHealth(String datacenter, boolean healthy, long lastCheckTime) {
            this(datacenter, healthy, lastCheckTime, null);
        }
        
        public DatacenterHealth(String datacenter, boolean healthy, long lastCheckTime, String error) {
            this.datacenter = datacenter;
            this.healthy = healthy;
            this.lastCheckTime = lastCheckTime;
            this.error = error;
        }
        
        public String getDatacenter() { return datacenter; }
        public boolean isHealthy() { return healthy; }
        public long getLastCheckTime() { return lastCheckTime; }
        public String getError() { return error; }
        
        @Override
        public String toString() {
            return String.format("DatacenterHealth{datacenter='%s', healthy=%s, lastCheck=%d, error='%s'}",
                datacenter, healthy, lastCheckTime, error);
        }
    }
    
    /**
     * Overall health status of the JMS library.
     */
    public static class JmsLibraryHealth {
        private final boolean overallHealthy;
        private final Map<String, DatacenterHealth> datacenterHealth;
        private final long totalRunningListeners;
        private final String primaryDatacenter;
        
        public JmsLibraryHealth(boolean overallHealthy, 
                               Map<String, DatacenterHealth> datacenterHealth,
                               long totalRunningListeners,
                               String primaryDatacenter) {
            this.overallHealthy = overallHealthy;
            this.datacenterHealth = datacenterHealth;
            this.totalRunningListeners = totalRunningListeners;
            this.primaryDatacenter = primaryDatacenter;
        }
        
        public boolean isOverallHealthy() { return overallHealthy; }
        public Map<String, DatacenterHealth> getDatacenterHealth() { return datacenterHealth; }
        public long getTotalRunningListeners() { return totalRunningListeners; }
        public String getPrimaryDatacenter() { return primaryDatacenter; }
        
        @Override
        public String toString() {
            return String.format("JmsLibraryHealth{healthy=%s, listeners=%d, primary='%s', datacenters=%s}",
                overallHealthy, totalRunningListeners, primaryDatacenter, datacenterHealth.keySet());
        }
    }
}