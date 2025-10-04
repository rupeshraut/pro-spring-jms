package com.prospringjms.connector.jms;

import java.util.Map;
import java.util.HashMap;

/**
 * JMS request model containing destination, payload, headers, and messaging options.
 */
public class JmsRequest {
    
    public enum MessageType {
        TEXT, OBJECT, MAP, BYTES, STREAM
    }
    
    private final String destination;
    private final Object payload;
    private final Map<String, Object> headers;
    private final MessageType messageType;
    private final boolean requestResponse;
    private final String replyTo;
    private final String correlationId;
    private final long timeToLive;
    private final int priority;
    private final boolean persistent;
    
    private JmsRequest(Builder builder) {
        this.destination = builder.destination;
        this.payload = builder.payload;
        this.headers = new HashMap<>(builder.headers);
        this.messageType = builder.messageType;
        this.requestResponse = builder.requestResponse;
        this.replyTo = builder.replyTo;
        this.correlationId = builder.correlationId;
        this.timeToLive = builder.timeToLive;
        this.priority = builder.priority;
        this.persistent = builder.persistent;
    }
    
    // Getters
    public String getDestination() { return destination; }
    public Object getPayload() { return payload; }
    public Map<String, Object> getHeaders() { return new HashMap<>(headers); }
    public MessageType getMessageType() { return messageType; }
    public boolean isRequestResponse() { return requestResponse; }
    public String getReplyTo() { return replyTo; }
    public String getCorrelationId() { return correlationId; }
    public long getTimeToLive() { return timeToLive; }
    public int getPriority() { return priority; }
    public boolean isPersistent() { return persistent; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String destination;
        private Object payload;
        private Map<String, Object> headers = new HashMap<>();
        private MessageType messageType = MessageType.OBJECT;
        private boolean requestResponse = false;
        private String replyTo;
        private String correlationId;
        private long timeToLive = 0; // 0 means no expiration
        private int priority = 4; // Default JMS priority
        private boolean persistent = true;
        
        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }
        
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Builder headers(Map<String, Object> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }
        
        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }
        
        public Builder requestResponse(boolean requestResponse) {
            this.requestResponse = requestResponse;
            return this;
        }
        
        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder timeToLive(long timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }
        
        public Builder priority(int priority) {
            if (priority < 0 || priority > 9) {
                throw new IllegalArgumentException("Priority must be between 0 and 9");
            }
            this.priority = priority;
            return this;
        }
        
        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }
        
        public JmsRequest build() {
            if (destination == null) {
                throw new IllegalArgumentException("Destination is required");
            }
            if (payload == null) {
                throw new IllegalArgumentException("Payload is required");
            }
            if (requestResponse && replyTo == null) {
                throw new IllegalArgumentException("ReplyTo destination is required for request-response");
            }
            return new JmsRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("JmsRequest{destination='%s', messageType=%s, requestResponse=%s, " +
                           "replyTo='%s', correlationId='%s', priority=%d, persistent=%s}",
            destination, messageType, requestResponse, replyTo, correlationId, priority, persistent);
    }
}