package com.prospringjms.connector.manager;

import com.prospringjms.connector.core.*;
import com.prospringjms.connector.rest.RestConnector;
import com.prospringjms.connector.jms.JmsConnector;
import com.prospringjms.connector.kafka.KafkaConnector;
import com.prospringjms.connector.graphql.GraphQlConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;

/**
 * Central manager for all backend connectors.
 * Provides a unified interface for routing requests to appropriate connectors
 * based on connector type and handles connector lifecycle management.
 */
@Service
public class ConnectorManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectorManager.class);
    
    private final Map<ConnectorType, BackendConnector<?, ?>> connectors;
    
    @Autowired
    private RestConnector restConnector;
    
    @Autowired
    private JmsConnector jmsConnector;
    
    @Autowired
    private KafkaConnector kafkaConnector;
    
    @Autowired
    private GraphQlConnector graphQlConnector;
    
    public ConnectorManager() {
        this.connectors = new EnumMap<>(ConnectorType.class);
        logger.info("Connector manager initialized");
    }
    
    /**
     * Initialize all connectors after dependency injection.
     */
    @Autowired
    public void initializeConnectors() {
        connectors.put(ConnectorType.REST, restConnector);
        connectors.put(ConnectorType.JMS, jmsConnector);
        connectors.put(ConnectorType.KAFKA, kafkaConnector);
        connectors.put(ConnectorType.GRAPHQL, graphQlConnector);
        
        logger.info("Initialized {} connectors: {}", connectors.size(), connectors.keySet());
    }
    
    /**
     * Send synchronous request using the appropriate connector.
     */
    @SuppressWarnings("unchecked")
    public <TRequest, TResponse> TResponse sendSync(ConnectorType type, TRequest request, 
                                                  ConnectorContext context) throws ConnectorException {
        BackendConnector<TRequest, TResponse> connector = getConnector(type);
        logger.debug("Sending sync request via {} connector to: {}", type, context.getEndpoint());
        
        return connector.sendSync(request, context);
    }
    
    /**
     * Send asynchronous request using the appropriate connector.
     */
    @SuppressWarnings("unchecked")
    public <TRequest, TResponse> CompletableFuture<TResponse> sendAsync(ConnectorType type, 
                                                                      TRequest request, 
                                                                      ConnectorContext context) {
        BackendConnector<TRequest, TResponse> connector = getConnector(type);
        logger.debug("Sending async request via {} connector to: {}", type, context.getEndpoint());
        
        return connector.sendAsync(request, context);
    }
    
    /**
     * Send fire-and-forget request using the appropriate connector.
     */
    @SuppressWarnings("unchecked")
    public <TRequest> CompletableFuture<Void> sendAsyncNoResponse(ConnectorType type, 
                                                                TRequest request, 
                                                                ConnectorContext context) {
        BackendConnector<TRequest, ?> connector = getConnector(type);
        logger.debug("Sending fire-and-forget request via {} connector to: {}", 
            type, context.getEndpoint());
        
        return connector.sendAsyncNoResponse(request, context);
    }
    
    /**
     * Check health of a specific connector.
     */
    public boolean isConnectorHealthy(ConnectorType type, ConnectorContext context) {
        try {
            BackendConnector<?, ?> connector = getConnector(type);
            return connector.isHealthy(context);
        } catch (Exception e) {
            logger.warn("Health check failed for {} connector: {}", type, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check health of all connectors.
     */
    public Map<ConnectorType, Boolean> getAllConnectorHealth() {
        Map<ConnectorType, Boolean> healthStatus = new EnumMap<>(ConnectorType.class);
        
        for (ConnectorType type : ConnectorType.values()) {
            try {
                // Create a default context for health check
                ConnectorContext context = ConnectorContext.builder()
                    .endpoint("http://localhost:8080/health")
                    .build();
                
                boolean healthy = isConnectorHealthy(type, context);
                healthStatus.put(type, healthy);
                
            } catch (Exception e) {
                logger.warn("Failed to check health for {} connector: {}", type, e.getMessage());
                healthStatus.put(type, false);
            }
        }
        
        return healthStatus;
    }
    
    /**
     * Get connector metrics for monitoring.
     */
    public Map<ConnectorType, AbstractResilientConnector.ConnectorMetrics> getAllConnectorMetrics() {
        Map<ConnectorType, AbstractResilientConnector.ConnectorMetrics> metricsMap = 
            new EnumMap<>(ConnectorType.class);
        
        for (Map.Entry<ConnectorType, BackendConnector<?, ?>> entry : connectors.entrySet()) {
            try {
                if (entry.getValue() instanceof AbstractResilientConnector) {
                    AbstractResilientConnector<?, ?> resilientConnector = 
                        (AbstractResilientConnector<?, ?>) entry.getValue();
                    metricsMap.put(entry.getKey(), resilientConnector.getMetrics());
                }
            } catch (Exception e) {
                logger.warn("Failed to get metrics for {} connector: {}", 
                    entry.getKey(), e.getMessage());
            }
        }
        
        return metricsMap;
    }
    
    /**
     * Get connector by type.
     */
    @SuppressWarnings("unchecked")
    private <TRequest, TResponse> BackendConnector<TRequest, TResponse> getConnector(ConnectorType type) {
        BackendConnector<?, ?> connector = connectors.get(type);
        if (connector == null) {
            throw new IllegalArgumentException("No connector available for type: " + type);
        }
        return (BackendConnector<TRequest, TResponse>) connector;
    }
    
    /**
     * Shutdown all connectors gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down connector manager...");
        
        for (Map.Entry<ConnectorType, BackendConnector<?, ?>> entry : connectors.entrySet()) {
            try {
                entry.getValue().close();
                logger.info("Closed {} connector", entry.getKey());
            } catch (Exception e) {
                logger.error("Error closing {} connector", entry.getKey(), e);
            }
        }
        
        logger.info("Connector manager shutdown completed");
    }
    
    /**
     * Get REST connector for convenience methods.
     */
    public RestConnector getRestConnector() {
        return restConnector;
    }
    
    /**
     * Get JMS connector for convenience methods.
     */
    public JmsConnector getJmsConnector() {
        return jmsConnector;
    }
    
    /**
     * Get Kafka connector for convenience methods.
     */
    public KafkaConnector getKafkaConnector() {
        return kafkaConnector;
    }
    
    /**
     * Get GraphQL connector for convenience methods.
     */
    public GraphQlConnector getGraphQlConnector() {
        return graphQlConnector;
    }
}