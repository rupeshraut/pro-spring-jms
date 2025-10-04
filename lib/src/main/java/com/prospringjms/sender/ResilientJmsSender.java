package com.prospringjms.sender;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import com.prospringjms.resilience.Resilience4jManager;
import com.prospringjms.routing.DatacenterRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Resilient JMS message sender with datacenter affinity and failover capabilities.
 */
@Component
public class ResilientJmsSender {
    
    private static final Logger logger = LoggerFactory.getLogger(ResilientJmsSender.class);
    
    private final JmsLibraryProperties properties;
    private final DatacenterRouter router;
    private final Map<String, JmsTemplate> jmsTemplates;
    private final Resilience4jManager resilienceManager;
    
    public ResilientJmsSender(JmsLibraryProperties properties, 
                             DatacenterRouter router,
                             Map<String, JmsTemplate> jmsTemplates,
                             Resilience4jManager resilienceManager) {
        this.properties = properties;
        this.router = router;
        this.jmsTemplates = jmsTemplates;
        this.resilienceManager = resilienceManager;
    }
    
    /**
     * Sends a message to the primary datacenter with automatic failover.
     */
    public SendResult sendToPrimary(String destination, Object message) throws JmsLibraryException {
        return sendToPrimary(destination, message, null);
    }
    
    /**
     * Sends a message to the primary datacenter with headers and automatic failover.
     */
    public SendResult sendToPrimary(String destination, Object message, Map<String, Object> headers) throws JmsLibraryException {
        String primaryDatacenter = router.getPrimaryDatacenter();
        
        try {
            return sendToDatacenter(primaryDatacenter, destination, message, headers);
        } catch (JmsLibraryException e) {
            logger.warn("Failed to send to primary datacenter {}, attempting failover", primaryDatacenter, e);
            return sendWithFailover(destination, message, headers, primaryDatacenter);
        }
    }
    
    /**
     * Sends a message to a specific datacenter.
     */
    public SendResult sendToDatacenter(String datacenter, String destination, Object message) throws JmsLibraryException {
        return sendToDatacenter(datacenter, destination, message, null);
    }
    
    /**
     * Sends a message to a specific datacenter with headers.
     */
    public SendResult sendToDatacenter(String datacenter, String destination, Object message, Map<String, Object> headers) throws JmsLibraryException {
        JmsTemplate template = jmsTemplates.get(datacenter);
        if (template == null) {
            throw new JmsLibraryException("No JMS template found for datacenter: " + datacenter, datacenter, "send");
        }
        
        return resilienceManager.executeWithFullResilience(datacenter, () -> {
            try {
                long startTime = System.currentTimeMillis();
                
                if (headers != null && !headers.isEmpty()) {
                    template.convertAndSend(destination, message, messagePostProcessor -> {
                        headers.forEach((key, value) -> {
                            try {
                                messagePostProcessor.setObjectProperty(key, value);
                            } catch (Exception ex) {
                                logger.error("Failed to set message property: {}", key, ex);
                            }
                        });
                        return messagePostProcessor;
                    });
                } else {
                    template.convertAndSend(destination, message);
                }
                
                long duration = System.currentTimeMillis() - startTime;
                router.updateDatacenterHealth(datacenter, true);
                
                logger.debug("Message sent successfully to datacenter {} in {}ms", datacenter, duration);
                return new SendResult(datacenter, destination, true, duration, null);
                
            } catch (Exception e) {
                router.updateDatacenterHealth(datacenter, false);
                if (e instanceof JmsLibraryException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(new JmsLibraryException("Failed to send message to datacenter: " + datacenter, datacenter, "send", e));
                }
            }
        });
    }
    
    /**
     * Sends a message with routing based on affinity rules.
     */
    public SendResult sendWithAffinity(SendRequest request) throws JmsLibraryException {
        DatacenterRouter.RouteRequest routeRequest = new DatacenterRouter.RouteRequest.Builder()
            .region(request.getRegion())
            .zone(request.getZone())
            .preferredDatacenters(request.getPreferredDatacenters())
            .excludedDatacenters(request.getExcludedDatacenters())
            .messageType(request.getMessageType())
            .build();
        
        List<String> candidates = router.routeMessage(routeRequest);
        
        for (String datacenter : candidates) {
            try {
                return sendToDatacenter(datacenter, request.getDestination(), request.getMessage(), request.getHeaders());
            } catch (JmsLibraryException e) {
                logger.warn("Failed to send to datacenter {}, trying next", datacenter, e);
            }
        }
        
        throw new JmsLibraryException("Failed to send message to any available datacenter");
    }
    
    /**
     * Sends a message asynchronously to the primary datacenter.
     */
    public CompletableFuture<SendResult> sendToPrimaryAsync(String destination, Object message) {
        return sendToPrimaryAsync(destination, message, null);
    }
    
    /**
     * Sends a message asynchronously to the primary datacenter with headers.
     */
    public CompletableFuture<SendResult> sendToPrimaryAsync(String destination, Object message, Map<String, Object> headers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendToPrimary(destination, message, headers);
            } catch (JmsLibraryException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Broadcasts a message to all available datacenters.
     */
    public List<SendResult> broadcast(String destination, Object message) throws JmsLibraryException {
        return broadcast(destination, message, null);
    }
    
    /**
     * Broadcasts a message to all available datacenters with headers.
     */
    public List<SendResult> broadcast(String destination, Object message, Map<String, Object> headers) throws JmsLibraryException {
        List<String> availableDatacenters = router.routeMessage(new DatacenterRouter.RouteRequest.Builder().build());
        
        return availableDatacenters.parallelStream()
            .map(datacenter -> {
                try {
                    return sendToDatacenter(datacenter, destination, message, headers);
                } catch (JmsLibraryException e) {
                    logger.error("Failed to broadcast to datacenter: {}", datacenter, e);
                    return new SendResult(datacenter, destination, false, 0, e.getMessage());
                }
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    private SendResult sendWithFailover(String destination, Object message, Map<String, Object> headers, String excludedDatacenter) throws JmsLibraryException {
        List<String> failoverDatacenters = router.getFailoverDatacenters(excludedDatacenter);
        
        if (failoverDatacenters.isEmpty()) {
            throw new JmsLibraryException("No failover datacenters available");
        }
        
        JmsLibraryException lastException = null;
        
        for (String datacenter : failoverDatacenters) {
            try {
                logger.info("Attempting failover to datacenter: {}", datacenter);
                return sendToDatacenter(datacenter, destination, message, headers);
            } catch (JmsLibraryException e) {
                lastException = e;
                logger.warn("Failover to datacenter {} failed", datacenter, e);
            }
        }
        
        throw new JmsLibraryException("All failover attempts failed", "failover", "send", lastException);
    }
    
    private boolean isDatacenterAvailable(String datacenter) {
        if (!router.isDatacenterHealthy(datacenter)) {
            return false;
        }
        
        // Circuit breaker availability is now handled by Resilience4j
        return resilienceManager.getCircuitBreaker(datacenter).getState() != 
               io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
    }
    
    /**
     * Request object for sending messages with affinity.
     */
    public static class SendRequest {
        private final String destination;
        private final Object message;
        private final Map<String, Object> headers;
        private final String region;
        private final String zone;
        private final List<String> preferredDatacenters;
        private final List<String> excludedDatacenters;
        private final String messageType;
        
        private SendRequest(Builder builder) {
            this.destination = builder.destination;
            this.message = builder.message;
            this.headers = builder.headers;
            this.region = builder.region;
            this.zone = builder.zone;
            this.preferredDatacenters = builder.preferredDatacenters;
            this.excludedDatacenters = builder.excludedDatacenters;
            this.messageType = builder.messageType;
        }
        
        // Getters
        public String getDestination() { return destination; }
        public Object getMessage() { return message; }
        public Map<String, Object> getHeaders() { return headers; }
        public String getRegion() { return region; }
        public String getZone() { return zone; }
        public List<String> getPreferredDatacenters() { return preferredDatacenters; }
        public List<String> getExcludedDatacenters() { return excludedDatacenters; }
        public String getMessageType() { return messageType; }
        
        public static class Builder {
            private String destination;
            private Object message;
            private Map<String, Object> headers;
            private String region;
            private String zone;
            private List<String> preferredDatacenters;
            private List<String> excludedDatacenters;
            private String messageType;
            
            public Builder destination(String destination) {
                this.destination = destination;
                return this;
            }
            
            public Builder message(Object message) {
                this.message = message;
                return this;
            }
            
            public Builder headers(Map<String, Object> headers) {
                this.headers = headers;
                return this;
            }
            
            public Builder region(String region) {
                this.region = region;
                return this;
            }
            
            public Builder zone(String zone) {
                this.zone = zone;
                return this;
            }
            
            public Builder preferredDatacenters(List<String> datacenters) {
                this.preferredDatacenters = datacenters;
                return this;
            }
            
            public Builder excludedDatacenters(List<String> datacenters) {
                this.excludedDatacenters = datacenters;
                return this;
            }
            
            public Builder messageType(String messageType) {
                this.messageType = messageType;
                return this;
            }
            
            public SendRequest build() {
                return new SendRequest(this);
            }
        }
    }
    
    /**
     * Result of a message send operation.
     */
    public static class SendResult {
        private final String datacenter;
        private final String destination;
        private final boolean success;
        private final long durationMs;
        private final String error;
        
        public SendResult(String datacenter, String destination, boolean success, long durationMs, String error) {
            this.datacenter = datacenter;
            this.destination = destination;
            this.success = success;
            this.durationMs = durationMs;
            this.error = error;
        }
        
        public String getDatacenter() { return datacenter; }
        public String getDestination() { return destination; }
        public boolean isSuccess() { return success; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
        
        @Override
        public String toString() {
            return String.format("SendResult{datacenter='%s', destination='%s', success=%s, duration=%dms, error='%s'}", 
                datacenter, destination, success, durationMs, error);
        }
    }
    

}