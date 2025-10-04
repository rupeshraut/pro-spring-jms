package com.prospringjms.connector.jms;

/**
 * JMS response model containing message ID, correlation ID, destination, and response data.
 */
public class JmsResponse {
    
    private final String messageId;
    private final String correlationId;
    private final String destination;
    private final String datacenter;
    private final String message;
    private final boolean success;
    private final long timestamp;
    private final Object responsePayload;
    
    public JmsResponse(String messageId, String correlationId, String destination, 
                      String datacenter, String message, boolean success, 
                      long timestamp, Object responsePayload) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.destination = destination;
        this.datacenter = datacenter;
        this.message = message;
        this.success = success;
        this.timestamp = timestamp;
        this.responsePayload = responsePayload;
    }
    
    // Getters
    public String getMessageId() { return messageId; }
    public String getCorrelationId() { return correlationId; }
    public String getDestination() { return destination; }
    public String getDatacenter() { return datacenter; }
    public String getMessage() { return message; }
    public boolean isSuccess() { return success; }
    public long getTimestamp() { return timestamp; }
    public Object getResponsePayload() { return responsePayload; }
    
    public boolean hasResponse() {
        return responsePayload != null;
    }
    
    @Override
    public String toString() {
        return String.format("JmsResponse{messageId='%s', correlationId='%s', destination='%s', " +
                           "datacenter='%s', success=%s, hasResponse=%s, message='%s'}",
            messageId, correlationId, destination, datacenter, success, hasResponse(), message);
    }
}