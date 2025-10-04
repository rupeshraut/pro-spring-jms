package com.prospringjms.routing;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Routes messages to appropriate datacenters based on affinity rules and availability.
 * Implements various load balancing and failover strategies.
 */
@Component
public class DatacenterRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(DatacenterRouter.class);
    
    private final JmsLibraryProperties properties;
    private final Map<String, DatacenterHealth> healthStatus = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    
    public DatacenterRouter(JmsLibraryProperties properties) {
        this.properties = properties;
        initializeHealthStatus();
    }
    
    /**
     * Routes a message to the best available datacenter based on configuration.
     */
    public List<String> routeMessage(RouteRequest request) throws JmsLibraryException {
        List<String> availableDatacenters = getAvailableDatacenters(request);
        
        if (availableDatacenters.isEmpty()) {
            throw new JmsLibraryException("No available datacenters for routing");
        }
        
        return applyRoutingStrategy(availableDatacenters, request);
    }
    
    /**
     * Gets the primary datacenter for sending messages.
     */
    public String getPrimaryDatacenter() {
        String primary = properties.getPrimaryDatacenter();
        if (isDatacenterHealthy(primary)) {
            return primary;
        }
        
        // Failover to next available datacenter
        return getAvailableDatacenters(new RouteRequest.Builder().build())
            .stream()
            .findFirst()
            .orElse(primary); // Return primary even if unhealthy as last resort
    }
    
    /**
     * Gets available datacenters with failover support.
     */
    public List<String> getFailoverDatacenters(String primaryDatacenter) {
        return getAvailableDatacenters(new RouteRequest.Builder().build())
            .stream()
            .filter(dc -> !dc.equals(primaryDatacenter))
            .collect(Collectors.toList());
    }
    
    /**
     * Updates health status for a datacenter.
     */
    public void updateDatacenterHealth(String datacenter, boolean healthy) {
        DatacenterHealth health = healthStatus.computeIfAbsent(datacenter, k -> new DatacenterHealth());
        health.setHealthy(healthy);
        health.setLastUpdate(Instant.now());
        
        if (healthy) {
            health.resetFailures();
        } else {
            health.incrementFailures();
        }
        
        logger.debug("Updated health status for datacenter {}: {}", datacenter, healthy);
    }
    
    /**
     * Checks if a datacenter is healthy and available.
     */
    public boolean isDatacenterHealthy(String datacenter) {
        DatacenterHealth health = healthStatus.get(datacenter);
        if (health == null) {
            return true; // Assume healthy if no health info
        }
        
        JmsLibraryProperties.DataCenter dc = properties.getDatacenters().get(datacenter);
        if (dc == null || !dc.getEnabled()) {
            return false;
        }
        
        return health.isHealthy();
    }
    
    private void initializeHealthStatus() {
        if (properties.getDatacenters() != null) {
            properties.getDatacenters().keySet().forEach(dc -> 
                healthStatus.put(dc, new DatacenterHealth()));
        }
    }
    
    private List<String> getAvailableDatacenters(RouteRequest request) {
        return properties.getDatacenters().entrySet().stream()
            .filter(entry -> entry.getValue().getEnabled())
            .filter(entry -> isDatacenterHealthy(entry.getKey()))
            .filter(entry -> matchesAffinity(entry.getKey(), request))
            .sorted(Comparator.comparing(entry -> entry.getValue().getPriority()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private boolean matchesAffinity(String datacenter, RouteRequest request) {
        if (request.getPreferredDatacenters() != null && 
            !request.getPreferredDatacenters().isEmpty()) {
            return request.getPreferredDatacenters().contains(datacenter);
        }
        
        if (request.getExcludedDatacenters() != null && 
            !request.getExcludedDatacenters().isEmpty()) {
            return !request.getExcludedDatacenters().contains(datacenter);
        }
        
        JmsLibraryProperties.DataCenter dc = properties.getDatacenters().get(datacenter);
        if (dc.getAffinity() != null && request.getRegion() != null) {
            return Objects.equals(dc.getAffinity().getRegion(), request.getRegion());
        }
        
        return true;
    }
    
    private List<String> applyRoutingStrategy(List<String> availableDatacenters, RouteRequest request) {
        if (properties.getLoadBalancing() == null) {
            return availableDatacenters;
        }
        
        String strategy = properties.getLoadBalancing().getStrategy();
        switch (strategy.toLowerCase()) {
            case "round-robin":
                return applyRoundRobinStrategy(availableDatacenters);
            case "weighted":
                return applyWeightedStrategy(availableDatacenters);
            case "priority":
            default:
                return availableDatacenters; // Already sorted by priority
        }
    }
    
    private List<String> applyRoundRobinStrategy(List<String> datacenters) {
        if (datacenters.isEmpty()) {
            return datacenters;
        }
        
        String key = String.join(",", datacenters);
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        
        int index = counter.getAndIncrement() % datacenters.size();
        List<String> result = new ArrayList<>(datacenters);
        
        // Move selected datacenter to front
        String selected = result.remove(index);
        result.add(0, selected);
        
        return result;
    }
    
    private List<String> applyWeightedStrategy(List<String> datacenters) {
        // Implement weighted random selection based on datacenter priorities
        // Lower priority number = higher weight
        List<WeightedDatacenter> weighted = datacenters.stream()
            .map(dc -> {
                int priority = properties.getDatacenters().get(dc).getPriority();
                int weight = Math.max(1, 101 - priority); // Invert priority to weight
                return new WeightedDatacenter(dc, weight);
            })
            .collect(Collectors.toList());
        
        return selectByWeight(weighted);
    }
    
    private List<String> selectByWeight(List<WeightedDatacenter> weighted) {
        int totalWeight = weighted.stream().mapToInt(WeightedDatacenter::getWeight).sum();
        int random = new Random().nextInt(totalWeight);
        
        int current = 0;
        for (WeightedDatacenter wdc : weighted) {
            current += wdc.getWeight();
            if (random < current) {
                List<String> result = new ArrayList<>();
                result.add(wdc.getDatacenter());
                // Add remaining datacenters in original order
                weighted.stream()
                    .map(WeightedDatacenter::getDatacenter)
                    .filter(dc -> !dc.equals(wdc.getDatacenter()))
                    .forEach(result::add);
                return result;
            }
        }
        
        return weighted.stream()
            .map(WeightedDatacenter::getDatacenter)
            .collect(Collectors.toList());
    }
    
    /**
     * Request object for routing decisions.
     */
    public static class RouteRequest {
        private final String region;
        private final String zone;
        private final List<String> preferredDatacenters;
        private final List<String> excludedDatacenters;
        private final String messageType;
        
        private RouteRequest(Builder builder) {
            this.region = builder.region;
            this.zone = builder.zone;
            this.preferredDatacenters = builder.preferredDatacenters;
            this.excludedDatacenters = builder.excludedDatacenters;
            this.messageType = builder.messageType;
        }
        
        public String getRegion() { return region; }
        public String getZone() { return zone; }
        public List<String> getPreferredDatacenters() { return preferredDatacenters; }
        public List<String> getExcludedDatacenters() { return excludedDatacenters; }
        public String getMessageType() { return messageType; }
        
        public static class Builder {
            private String region;
            private String zone;
            private List<String> preferredDatacenters;
            private List<String> excludedDatacenters;
            private String messageType;
            
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
            
            public RouteRequest build() {
                return new RouteRequest(this);
            }
        }
    }
    
    private static class DatacenterHealth {
        private boolean healthy = true;
        private Instant lastUpdate = Instant.now();
        private int consecutiveFailures = 0;
        
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public Instant getLastUpdate() { return lastUpdate; }
        public void setLastUpdate(Instant lastUpdate) { this.lastUpdate = lastUpdate; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void incrementFailures() { this.consecutiveFailures++; }
        public void resetFailures() { this.consecutiveFailures = 0; }
    }
    
    private static class WeightedDatacenter {
        private final String datacenter;
        private final int weight;
        
        public WeightedDatacenter(String datacenter, int weight) {
            this.datacenter = datacenter;
            this.weight = weight;
        }
        
        public String getDatacenter() { return datacenter; }
        public int getWeight() { return weight; }
    }
}