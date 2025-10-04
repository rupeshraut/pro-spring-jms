package com.prospringjms.connector.kafka;

import com.prospringjms.connector.core.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.util.Collections;
import java.util.UUID;

/**
 * Kafka connector supporting both synchronous and asynchronous messaging
 * with comprehensive resilience patterns.
 * 
 * Handles producer and consumer operations with automatic partition management,
 * serialization, and error handling.
 */
@Component
public class KafkaConnector extends AbstractResilientConnector<KafkaRequest, KafkaResponse> {
    
    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaProducer<String, Object> kafkaProducer;
    private AdminClient adminClient;
    
    public KafkaConnector() {
        initializeKafkaComponents();
        logger.info("Kafka connector initialized");
    }
    
    public KafkaConnector(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        logger.info("Kafka connector initialized with custom KafkaTemplate");
    }
    
    private void initializeKafkaComponents() {
        // Initialize Kafka components with default configuration
        // In a real application, these would be configured via Spring Boot properties
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        
        try {
            this.kafkaProducer = new KafkaProducer<>(props);
            this.adminClient = AdminClient.create(props);
        } catch (Exception e) {
            logger.warn("Failed to initialize Kafka producer/admin client: {}", e.getMessage());
        }
    }
    
    @Override
    protected KafkaResponse doSendSync(KafkaRequest request, ConnectorContext context) throws ConnectorException {
        try {
            logger.debug("Sending sync Kafka message to topic: {} with key: {}", 
                request.getTopic(), request.getKey());
            
            if (kafkaTemplate != null) {
                return sendSyncWithTemplate(request, context);
            } else if (kafkaProducer != null) {
                return sendSyncWithProducer(request, context);
            } else {
                throw new ConnectorException("No Kafka producer available", null, getType(), 
                    request.getTopic(), context.getDatacenter(), "sendSync");
            }
            
        } catch (Exception e) {
            throw new ConnectorException("Kafka sync send failed", e, getType(), 
                request.getTopic(), context.getDatacenter(), "sendSync");
        }
    }
    
    @Override
    protected CompletableFuture<KafkaResponse> doSendAsync(KafkaRequest request, ConnectorContext context) {
        logger.debug("Sending async Kafka message to topic: {} with key: {}", 
            request.getTopic(), request.getKey());
        
        if (kafkaTemplate != null) {
            return sendAsyncWithTemplate(request, context);
        } else if (kafkaProducer != null) {
            return sendAsyncWithProducer(request, context);
        } else {
            CompletableFuture<KafkaResponse> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ConnectorException(
                "No Kafka producer available", null, getType(), 
                request.getTopic(), context.getDatacenter(), "sendAsync"));
            return failedFuture;
        }
    }
    
    @Override
    protected CompletableFuture<Void> doSendAsyncNoResponse(KafkaRequest request, ConnectorContext context) {
        logger.debug("Sending fire-and-forget Kafka message to topic: {} with key: {}", 
            request.getTopic(), request.getKey());
        
        return CompletableFuture.runAsync(() -> {
            try {
                if (kafkaTemplate != null) {
                    kafkaTemplate.send(request.getTopic(), request.getKey(), request.getValue());
                } else if (kafkaProducer != null) {
                    ProducerRecord<String, Object> record = createProducerRecord(request);
                    kafkaProducer.send(record);
                } else {
                    throw new ConnectorException("No Kafka producer available", null, getType(), 
                        request.getTopic(), context.getDatacenter(), "sendAsyncNoResponse");
                }
                
                logger.debug("Fire-and-forget Kafka message sent to topic: {}", request.getTopic());
                
            } catch (Exception e) {
                throw new CompletionException(new ConnectorException(
                    "Kafka fire-and-forget send failed", e, getType(), 
                    request.getTopic(), context.getDatacenter(), "sendAsyncNoResponse"));
            }
        });
    }
    
    /**
     * Send synchronous message using KafkaTemplate.
     */
    private KafkaResponse sendSyncWithTemplate(KafkaRequest request, ConnectorContext context) 
            throws ConnectorException {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(
                request.getTopic(), request.getKey(), request.getValue()).get(
                context.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            
            RecordMetadata metadata = result.getRecordMetadata();
            
            return new KafkaResponse(
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                request.getKey(),
                true,
                "Message sent successfully",
                System.currentTimeMillis(),
                null
            );
            
        } catch (Exception e) {
            throw new ConnectorException("KafkaTemplate sync send failed", e, getType(), 
                request.getTopic(), context.getDatacenter(), "sendSyncWithTemplate");
        }
    }
    
    /**
     * Send asynchronous message using KafkaTemplate.
     */
    private CompletableFuture<KafkaResponse> sendAsyncWithTemplate(KafkaRequest request, ConnectorContext context) {
        CompletableFuture<KafkaResponse> future = new CompletableFuture<>();
        
        kafkaTemplate.send(request.getTopic(), request.getKey(), request.getValue())
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    RecordMetadata metadata = result.getRecordMetadata();
                    KafkaResponse response = new KafkaResponse(
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        request.getKey(),
                        true,
                        "Message sent successfully",
                        System.currentTimeMillis(),
                        null
                    );
                    future.complete(response);
                } else {
                    future.completeExceptionally(new ConnectorException(
                        "KafkaTemplate async send failed", ex, getType(), 
                        request.getTopic(), context.getDatacenter(), "sendAsyncWithTemplate"));
                }
            });
        
        return future;
    }
    
    /**
     * Send synchronous message using KafkaProducer.
     */
    private KafkaResponse sendSyncWithProducer(KafkaRequest request, ConnectorContext context) 
            throws ConnectorException {
        try {
            ProducerRecord<String, Object> record = createProducerRecord(request);
            Future<RecordMetadata> future = kafkaProducer.send(record);
            RecordMetadata metadata = future.get(context.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            
            return new KafkaResponse(
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                request.getKey(),
                true,
                "Message sent successfully",
                System.currentTimeMillis(),
                null
            );
            
        } catch (Exception e) {
            throw new ConnectorException("KafkaProducer sync send failed", e, getType(), 
                request.getTopic(), context.getDatacenter(), "sendSyncWithProducer");
        }
    }
    
    /**
     * Send asynchronous message using KafkaProducer.
     */
    private CompletableFuture<KafkaResponse> sendAsyncWithProducer(KafkaRequest request, ConnectorContext context) {
        CompletableFuture<KafkaResponse> future = new CompletableFuture<>();
        
        try {
            ProducerRecord<String, Object> record = createProducerRecord(request);
            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(new ConnectorException(
                        "KafkaProducer async send failed", exception, getType(), 
                        request.getTopic(), context.getDatacenter(), "sendAsyncWithProducer"));
                } else {
                    KafkaResponse response = new KafkaResponse(
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        request.getKey(),
                        true,
                        "Message sent successfully",
                        System.currentTimeMillis(),
                        null
                    );
                    future.complete(response);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(new ConnectorException(
                "Failed to initiate KafkaProducer async send", e, getType(), 
                request.getTopic(), context.getDatacenter(), "sendAsyncWithProducer"));
        }
        
        return future;
    }
    
    /**
     * Create ProducerRecord from KafkaRequest.
     */
    private ProducerRecord<String, Object> createProducerRecord(KafkaRequest request) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            request.getTopic(), 
            request.getPartition(), 
            request.getKey(), 
            request.getValue()
        );
        
        // Add headers if present
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((key, value) -> 
                record.headers().add(key, value.toString().getBytes()));
        }
        
        return record;
    }
    
    @Override
    protected boolean doHealthCheck(ConnectorContext context) {
        try {
            if (adminClient != null) {
                // Try to describe topics as a health check
                DescribeTopicsResult result = adminClient.describeTopics(Collections.singleton("__consumer_offsets"));
                result.all().get(5, TimeUnit.SECONDS);
                return true;
            }
            return kafkaTemplate != null || kafkaProducer != null;
        } catch (Exception e) {
            logger.debug("Kafka health check failed", e);
            return false;
        }
    }
    
    @Override
    public ConnectorType getType() {
        return ConnectorType.KAFKA;
    }
    
    @Override
    protected void doClose() {
        try {
            if (kafkaProducer != null) {
                kafkaProducer.close();
            }
            if (adminClient != null) {
                adminClient.close();
            }
        } catch (Exception e) {
            logger.error("Error closing Kafka connector", e);
        }
        logger.info("Kafka connector closed");
    }
    
    /**
     * Convenience method for sending string messages.
     */
    public KafkaResponse sendMessage(String topic, String key, String message, ConnectorContext context) 
            throws ConnectorException {
        KafkaRequest request = KafkaRequest.builder()
            .topic(topic)
            .key(key)
            .value(message)
            .build();
        
        return sendSync(request, context);
    }
    
    /**
     * Convenience method for sending object messages.
     */
    public KafkaResponse sendObject(String topic, String key, Object object, ConnectorContext context) 
            throws ConnectorException {
        KafkaRequest request = KafkaRequest.builder()
            .topic(topic)
            .key(key)
            .value(object)
            .build();
        
        return sendSync(request, context);
    }
    
    /**
     * Convenience method for sending to a specific partition.
     */
    public KafkaResponse sendToPartition(String topic, int partition, String key, 
                                       Object value, ConnectorContext context) 
            throws ConnectorException {
        KafkaRequest request = KafkaRequest.builder()
            .topic(topic)
            .partition(partition)
            .key(key)
            .value(value)
            .build();
        
        return sendSync(request, context);
    }
}