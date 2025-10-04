package com.prospringjms.messaging.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processing context for VETRO message processing.
 * Contains metadata and state information for message processing pipeline.
 */
public class ProcessingContext {
    
    private final String correlationId;
    private final LocalDateTime processingStartTime;
    private final Map<String, Object> properties;
    private final String messageType;
    private final String sourceDestination;
    
    public ProcessingContext(String correlationId, String messageType, String sourceDestination) {
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.messageType = messageType;
        this.sourceDestination = sourceDestination;
        this.processingStartTime = LocalDateTime.now();
        this.properties = new HashMap<>();
    }
    
    public ProcessingContext(String messageType, String sourceDestination) {
        this(null, messageType, sourceDestination);
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public LocalDateTime getProcessingStartTime() {
        return processingStartTime;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public String getSourceDestination() {
        return sourceDestination;
    }
    
    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    @Override
    public String toString() {
        return String.format("ProcessingContext{correlationId='%s', messageType='%s', sourceDestination='%s'}", 
            correlationId, messageType, sourceDestination);
    }
}