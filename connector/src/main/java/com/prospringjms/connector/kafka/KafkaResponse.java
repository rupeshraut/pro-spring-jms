package com.prospringjms.connector.kafka;

/**
 * Kafka response model containing topic, partition, offset, and response data.
 */
public class KafkaResponse {
    
    private final String topic;
    private final int partition;
    private final long offset;
    private final String key;
    private final boolean success;
    private final String message;
    private final long timestamp;
    private final Object responsePayload;
    
    public KafkaResponse(String topic, int partition, long offset, String key, 
                        boolean success, String message, long timestamp, Object responsePayload) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.success = success;
        this.message = message;
        this.timestamp = timestamp;
        this.responsePayload = responsePayload;
    }
    
    // Getters
    public String getTopic() { return topic; }
    public int getPartition() { return partition; }
    public long getOffset() { return offset; }
    public String getKey() { return key; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public Object getResponsePayload() { return responsePayload; }
    
    public boolean hasResponse() {
        return responsePayload != null;
    }
    
    /**
     * Get unique message identifier combining topic, partition, and offset.
     */
    public String getMessageId() {
        return String.format("%s-%d-%d", topic, partition, offset);
    }
    
    @Override
    public String toString() {
        return String.format("KafkaResponse{topic='%s', partition=%d, offset=%d, key='%s', " +
                           "success=%s, hasResponse=%s, message='%s'}",
            topic, partition, offset, key, success, hasResponse(), message);
    }
}