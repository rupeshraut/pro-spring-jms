package com.prospringjms.messaging.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Routing decision for VETRO message processing.
 * Contains destination information and routing options.
 */
public class RoutingDecision {
    
    private final String destination;
    private final String datacenter;
    private final boolean expectResponse;
    private final String responseDestination;
    private final Map<String, Object> headers;
    private final int priority;
    private final long timeToLive;
    
    private RoutingDecision(Builder builder) {
        this.destination = builder.destination;
        this.datacenter = builder.datacenter;
        this.expectResponse = builder.expectResponse;
        this.responseDestination = builder.responseDestination;
        this.headers = new HashMap<>(builder.headers);
        this.priority = builder.priority;
        this.timeToLive = builder.timeToLive;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public String getDatacenter() {
        return datacenter;
    }
    
    public boolean isExpectResponse() {
        return expectResponse;
    }
    
    public String getResponseDestination() {
        return responseDestination;
    }
    
    public Map<String, Object> getHeaders() {
        return new HashMap<>(headers);
    }
    
    public int getPriority() {
        return priority;
    }
    
    public long getTimeToLive() {
        return timeToLive;
    }
    
    public static Builder builder(String destination) {
        return new Builder(destination);
    }
    
    public static class Builder {
        private final String destination;
        private String datacenter = "primary";
        private boolean expectResponse = false;
        private String responseDestination;
        private Map<String, Object> headers = new HashMap<>();
        private int priority = 4; // Normal priority
        private long timeToLive = 0; // No expiration
        
        public Builder(String destination) {
            this.destination = destination;
        }
        
        public Builder datacenter(String datacenter) {
            this.datacenter = datacenter;
            return this;
        }
        
        public Builder expectResponse(boolean expectResponse) {
            this.expectResponse = expectResponse;
            return this;
        }
        
        public Builder responseDestination(String responseDestination) {
            this.responseDestination = responseDestination;
            this.expectResponse = true;
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
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder timeToLive(long timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }
        
        public RoutingDecision build() {
            return new RoutingDecision(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("RoutingDecision{destination='%s', datacenter='%s', expectResponse=%s}", 
            destination, datacenter, expectResponse);
    }
}