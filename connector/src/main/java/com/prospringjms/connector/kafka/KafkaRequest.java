package com.prospringjms.connector.kafka;

import java.util.Map;
import java.util.HashMap;

/**
 * Kafka request model containing topic, key, value, partition, and headers.
 */
public class KafkaRequest {
    
    private final String topic;
    private final String key;
    private final Object value;
    private final Integer partition;
    private final Map<String, Object> headers;
    private final Long timestamp;
    
    private KafkaRequest(Builder builder) {
        this.topic = builder.topic;
        this.key = builder.key;
        this.value = builder.value;
        this.partition = builder.partition;
        this.headers = new HashMap<>(builder.headers);
        this.timestamp = builder.timestamp;
    }
    
    // Getters
    public String getTopic() { return topic; }
    public String getKey() { return key; }
    public Object getValue() { return value; }
    public Integer getPartition() { return partition; }
    public Map<String, Object> getHeaders() { return new HashMap<>(headers); }
    public Long getTimestamp() { return timestamp; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String topic;
        private String key;
        private Object value;
        private Integer partition;
        private Map<String, Object> headers = new HashMap<>();
        private Long timestamp;
        
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }
        
        public Builder key(String key) {
            this.key = key;
            return this;
        }
        
        public Builder value(Object value) {
            this.value = value;
            return this;
        }
        
        public Builder partition(Integer partition) {
            this.partition = partition;
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
        
        public Builder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public KafkaRequest build() {
            if (topic == null) {
                throw new IllegalArgumentException("Topic is required");
            }
            if (value == null) {
                throw new IllegalArgumentException("Value is required");
            }
            return new KafkaRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("KafkaRequest{topic='%s', key='%s', partition=%s, hasValue=%s, " +
                           "headersCount=%d, timestamp=%s}",
            topic, key, partition, value != null, headers.size(), timestamp);
    }
}