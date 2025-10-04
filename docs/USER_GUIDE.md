# Pro Spring JMS Multi-Datacenter Library - User Guide

## Table of Contents
1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Installation](#installation)
4. [Configuration](#configuration)
5. [Core Concepts](#core-concepts)
6. [Usage Examples](#usage-examples)
7. [Advanced Features](#advanced-features)
8. [Monitoring & Observability](#monitoring--observability)
9. [Security](#security)
10. [Resilience Patterns](#resilience-patterns)
11. [Troubleshooting](#troubleshooting)
12. [Best Practices](#best-practices)
13. [Migration Guide](#migration-guide)
14. [API Reference](#api-reference)

## Overview

The **Pro Spring JMS Multi-Datacenter Library** is a production-ready Spring Boot library that provides enterprise-grade JMS messaging capabilities across multiple datacenters. It features intelligent routing, automatic failover, comprehensive resilience patterns, and enterprise security.

### Key Features

- **Multi-Datacenter Support** - Route messages across multiple datacenters with intelligent failover
- **Resilience4j Integration** - Industry-standard circuit breaker, retry, rate limiting, and bulkhead patterns
- **Enterprise Security** - AES-256 encryption, secure message handling, and comprehensive audit trails
- **Advanced Health Monitoring** - Deep health checks with performance metrics and alerting
- **Auto-Configuration** - Zero-configuration setup with Spring Boot auto-configuration
- **Multi-Broker Support** - Native support for ActiveMQ Artemis and IBM MQ
- **Production Ready** - Comprehensive monitoring, metrics, and observability features

### Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Your App      │───▶│  JMS Library     │───▶│  Datacenters    │
│                 │    │                  │    │                 │
│ - Controllers   │    │ - Routing        │    │ - DC1 (Primary) │
│ - Services      │    │ - Resilience     │    │ - DC2 (Backup)  │
│ - Components    │    │ - Security       │    │ - DC3 (DR)      │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Quick Start

### 1. Add Dependency

**Gradle:**
```gradle
implementation 'com.example:pro-spring-jms:1.0.0'
```

**Maven:**
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>pro-spring-jms</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Basic Configuration

Create `application.yml`:

```yaml
jms:
  default-datacenter: dc1
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
      username: admin
      password: admin
```

### 3. Send Your First Message

```java
@RestController
public class MessageController {
    
    @Autowired
    private JmsLibraryManager jmsLibrary;
    
    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody String message) {
        SendResult result = jmsLibrary.getSender()
            .sendToPrimary("orders.queue", message);
        
        return ResponseEntity.ok("Message sent to " + result.getDatacenter());
    }
}
```

### 4. Listen for Messages

```java
@Component
public class OrderProcessor {
    
    @JmsListener(destination = "orders.queue")
    public void processOrder(String orderMessage) {
        // Process the order
        System.out.println("Processing order: " + orderMessage);
    }
}
```

## Installation

### Prerequisites

- **Java 17+** - The library requires Java 17 or higher
- **Spring Boot 3.2+** - Compatible with Spring Boot 3.2.0 and above
- **Message Broker** - ActiveMQ Artemis or IBM MQ instance

### Dependency Management

The library automatically includes all necessary dependencies:

- Spring JMS integration
- Jakarta JMS API 3.1
- Resilience4j components
- Jackson for JSON processing
- Micrometer for metrics

### Auto-Configuration

The library uses Spring Boot's auto-configuration mechanism. Simply add the dependency and the library will be automatically configured and ready to use.

## Configuration

### Complete Configuration Reference

```yaml
jms:
  # Default datacenter for message routing
  default-datacenter: dc1
  
  # Datacenter definitions
  datacenters:
    dc1:
      type: artemis                    # artemis | ibmmq
      host: localhost
      port: 61616
      username: admin
      password: admin
      client-id: app-dc1
      
      # Connection pool settings
      connection-pool:
        max-connections: 20
        idle-timeout: 30000
        max-sessions-per-connection: 10
      
      # Queue and topic mappings
      queues:
        orders: orders.queue
        notifications: notifications.queue
        dlq: dlq.queue
      topics:
        events: events.topic
        
    dc2:
      type: ibmmq
      host: mq.company.com
      port: 1414
      queue-manager: QM1
      channel: SYSTEM.DEF.SVRCONN
      username: mquser
      password: mqpass
      
      queues:
        orders: DEV.QUEUE.ORDERS
        notifications: DEV.QUEUE.NOTIFICATIONS
  
  # Routing configuration
  routing:
    strategy: primary-backup           # primary-backup | round-robin | weighted
    health-check-interval: 30s
    failover:
      enabled: true
      cross-region-enabled: false
      exclude-unhealthy-datacenters: true
    load-balancing:
      enabled: false
      weights:
        dc1: 70
        dc2: 30
      health-check-weight-adjustment: true
  
  # Resilience configuration (Resilience4j)
  resiliency:
    circuit-breaker:
      enabled: true
      failure-threshold: 5             # Number of failures before opening
      reset-timeout-ms: 60000          # Time before attempting to close
      timeout-ms: 10000                # Operation timeout
    
    retry:
      enabled: true
      max-attempts: 3
      initial-delay-ms: 1000
      backoff-multiplier: 2.0
      retryable-exceptions:
        - "javax.jms.JMSException"
        - "JmsLibraryException"
    
    rate-limiting:
      enabled: true
      max-requests-per-second: 1000
      window-size-ms: 1000
    
    bulk-head:
      enabled: true
      max-concurrent-calls: 100
      max-wait-ms: 5000
  
  # Security configuration
  security:
    encryption:
      enabled: true
      algorithm: AES-256-GCM
      key-rotation-interval: 24h
    
    audit:
      enabled: true
      log-level: INFO
      include-message-content: false
    
    message-validation:
      enabled: true
      max-message-size: 10MB
      allowed-content-types:
        - "application/json"
        - "text/plain"

# Resilience4j configuration (optional - for fine-tuning)
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 60s
    instances:
      dc1:
        base-config: default
      dc2:
        base-config: default
        
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
```

### Environment-Specific Configuration

#### Development (`application-dev.yml`)
```yaml
jms:
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
  security:
    encryption:
      enabled: false
  resiliency:
    circuit-breaker:
      failure-threshold: 10  # More lenient for development
```

#### Production (`application-prod.yml`)
```yaml
jms:
  datacenters:
    dc1:
      type: ibmmq
      host: ${MQ_HOST_DC1:mq1.prod.company.com}
      port: ${MQ_PORT_DC1:1414}
      username: ${MQ_USER_DC1}
      password: ${MQ_PASS_DC1}
    dc2:
      type: ibmmq
      host: ${MQ_HOST_DC2:mq2.prod.company.com}
      port: ${MQ_PORT_DC2:1414}
      username: ${MQ_USER_DC2}
      password: ${MQ_PASS_DC2}
  
  security:
    encryption:
      enabled: true
    audit:
      enabled: true
  
  resiliency:
    circuit-breaker:
      failure-threshold: 3   # Strict for production
```

## Core Concepts

### 1. Datacenter Management

The library treats each message broker as a "datacenter" with the following capabilities:

- **Health Monitoring** - Continuous health checks with configurable intervals
- **Intelligent Routing** - Route messages based on datacenter health and strategy
- **Automatic Failover** - Transparent failover when primary datacenter fails
- **Load Balancing** - Distribute load across healthy datacenters

### 2. Message Routing Strategies

#### Primary-Backup
```java
// Messages go to primary datacenter, failover to backup if primary fails
SendResult result = sender.sendToPrimary("orders.queue", message);
```

#### Affinity-Based
```java
// Route based on message characteristics or preferences
SendResult result = sender.sendWithAffinity(
    SendRequest.builder()
        .destination("orders.queue")
        .message(message)
        .region("us-east")
        .preferredDatacenters(List.of("dc1", "dc3"))
        .build()
);
```

### 3. Resilience Patterns

The library implements industry-standard resilience patterns using Resilience4j:

- **Circuit Breaker** - Prevents cascading failures
- **Retry with Exponential Backoff** - Handles transient failures
- **Rate Limiting** - Prevents system overload
- **Bulkhead** - Isolates failures between datacenters
- **Time Limiter** - Provides timeout protection

## Usage Examples

### Basic Message Sending

```java
@Service
public class OrderService {
    
    @Autowired
    private JmsLibraryManager jmsLibrary;
    
    public void processOrder(Order order) {
        try {
            // Send to primary datacenter with automatic resilience
            SendResult result = jmsLibrary.getSender()
                .sendToPrimary("orders.queue", order);
            
            log.info("Order sent successfully to {} in {}ms", 
                result.getDatacenter(), result.getDurationMs());
                
        } catch (JmsLibraryException e) {
            log.error("Failed to send order: {}", e.getMessage());
            // Handle failure (e.g., store for retry, send alert)
        }
    }
}
```

### Advanced Message Routing

```java
@Service
public class NotificationService {
    
    @Autowired
    private ResilientJmsSender sender;
    
    public void sendNotification(Notification notification) {
        Map<String, Object> headers = Map.of(
            "priority", notification.getPriority(),
            "region", notification.getRegion(),
            "timestamp", System.currentTimeMillis()
        );
        
        SendRequest request = SendRequest.builder()
            .destination("notifications.queue")
            .message(notification)
            .headers(headers)
            .region(notification.getRegion())
            .messageType("notification")
            .preferredDatacenters(getPreferredDatacenters(notification))
            .build();
            
        SendResult result = sender.sendWithAffinity(request);
        
        // Update metrics or audit logs
        updateDeliveryMetrics(result);
    }
    
    private List<String> getPreferredDatacenters(Notification notification) {
        return switch (notification.getRegion()) {
            case "us-east" -> List.of("dc1", "dc2");
            case "us-west" -> List.of("dc3", "dc1");
            case "eu" -> List.of("dc4", "dc5");
            default -> List.of();
        };
    }
}
```

### Message Listening with Error Handling

```java
@Component
public class MessageProcessor {
    
    @JmsListener(destination = "orders.queue")
    public void processOrder(
            @Payload Order order,
            @Header Map<String, Object> headers,
            Session session) throws JMSException {
        
        try {
            // Process the order
            orderProcessingService.process(order);
            
            // Commit the transaction
            session.commit();
            
        } catch (BusinessException e) {
            log.error("Business error processing order {}: {}", 
                order.getId(), e.getMessage());
            
            // Send to error queue for manual review
            sendToErrorQueue(order, e.getMessage());
            session.commit();
            
        } catch (Exception e) {
            log.error("Technical error processing order {}: {}", 
                order.getId(), e.getMessage());
            
            // Rollback and let JMS retry
            session.rollback();
        }
    }
    
    @JmsListener(destination = "notifications.topic")
    public void handleNotification(
            @Payload Notification notification,
            @Header("region") String region) {
        
        log.info("Received notification for region {}: {}", 
            region, notification.getMessage());
            
        // Process notification based on region
        notificationService.handle(notification, region);
    }
}
```

### Dynamic Listener Management

```java
@Service
public class DynamicListenerService {
    
    @Autowired
    private JmsListenerRegistry listenerRegistry;
    
    public void registerTenantListener(String tenantId) {
        String queueName = "tenant." + tenantId + ".orders";
        
        listenerRegistry.registerListener(
            queueName,
            message -> processTenantMessage(tenantId, message),
            "dc1"  // Specific datacenter
        );
        
        log.info("Registered listener for tenant {} on queue {}", 
            tenantId, queueName);
    }
    
    public void unregisterTenantListener(String tenantId) {
        String queueName = "tenant." + tenantId + ".orders";
        
        listenerRegistry.unregisterListener(queueName);
        
        log.info("Unregistered listener for tenant {} from queue {}", 
            tenantId, queueName);
    }
    
    private void processTenantMessage(String tenantId, Object message) {
        // Process message for specific tenant
        tenantService.processMessage(tenantId, message);
    }
}
```

## Advanced Features

### 1. Custom Message Routing

```java
@Component
public class CustomRoutingService {
    
    @Autowired
    private DatacenterRouter router;
    
    public String selectDatacenterForMessage(Object message) {
        if (message instanceof PriorityMessage pm && pm.isHighPriority()) {
            // High priority messages go to fastest datacenter
            return router.getFastestHealthyDatacenter();
        }
        
        if (message instanceof RegionalMessage rm) {
            // Route based on message region
            return router.getDatacenterForRegion(rm.getRegion());
        }
        
        // Default routing
        return router.getPrimaryDatacenter();
    }
}
```

### 2. Security Integration

```java
@Service
public class SecureMessageService {
    
    @Autowired
    private SecurityManager securityManager;
    
    public void sendSecureMessage(String destination, SensitiveData data) {
        // Create security context
        SecurityContext context = securityManager.createSecurityContext(
            getCurrentUser(), 
            "SEND_MESSAGE",
            destination
        );
        
        try {
            // Encrypt sensitive data
            String encryptedData = securityManager.encryptMessage(
                data.toString(), 
                context
            );
            
            // Send with security headers
            Map<String, Object> headers = Map.of(
                "security-context", context.getContextId(),
                "encryption-algorithm", "AES-256-GCM",
                "user", getCurrentUser()
            );
            
            SendResult result = jmsLibrary.getSender()
                .sendToPrimary(destination, encryptedData, headers);
                
            // Audit the operation
            securityManager.auditMessageSent(context, result);
            
        } finally {
            securityManager.clearSecurityContext(context);
        }
    }
    
    @JmsListener(destination = "secure.queue")
    public void receiveSecureMessage(
            @Payload String encryptedMessage,
            @Header("security-context") String contextId) {
        
        try {
            // Validate security context
            SecurityContext context = securityManager.getSecurityContext(contextId);
            securityManager.validateMessageAccess(context, getCurrentUser());
            
            // Decrypt message
            String decryptedMessage = securityManager.decryptMessage(
                encryptedMessage, 
                context
            );
            
            // Process decrypted message
            processSecureMessage(decryptedMessage);
            
            // Audit message receipt
            securityManager.auditMessageReceived(context);
            
        } catch (SecurityException e) {
            log.error("Security validation failed: {}", e.getMessage());
            // Send security alert
            securityManager.sendSecurityAlert(e);
        }
    }
}
```

### 3. Health Monitoring Integration

```java
@Component
public class HealthMonitoringService {
    
    @Autowired
    private HealthCheckManager healthManager;
    
    @EventListener
    public void onDatacenterHealthChange(DatacenterHealthEvent event) {
        if (event.getHealthStatus() == HealthStatus.UNHEALTHY) {
            log.warn("Datacenter {} became unhealthy: {}", 
                event.getDatacenter(), event.getReason());
                
            // Trigger alerts
            alertService.sendDatacenterAlert(event);
            
            // Adjust routing weights
            adjustRoutingWeights(event.getDatacenter());
        }
    }
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void checkSystemHealth() {
        HealthCheckResult result = healthManager.performComprehensiveHealthCheck();
        
        if (result.getOverallHealth() == HealthStatus.DEGRADED) {
            log.warn("System health degraded: {}", result.getIssues());
            
            // Take corrective action
            handleDegradedHealth(result);
        }
        
        // Update metrics
        updateHealthMetrics(result);
    }
}
```

## Monitoring & Observability

### 1. Built-in Metrics

The library provides comprehensive metrics via Micrometer:

```java
// Resilience metrics
jms.circuit.breaker.calls{datacenter="dc1", state="closed"}
jms.circuit.breaker.failure.rate{datacenter="dc1"}
jms.retry.calls{datacenter="dc1", kind="successful_with_retry"}
jms.rate.limiter.available.permissions{datacenter="dc1"}

// Message metrics
jms.message.sent{datacenter="dc1", destination="orders.queue"}
jms.message.send.duration{datacenter="dc1", destination="orders.queue"}
jms.message.failure{datacenter="dc1", error.type="connection.timeout"}

// Health metrics
jms.datacenter.health{datacenter="dc1", status="healthy"}
jms.connection.pool.active{datacenter="dc1"}
jms.connection.pool.idle{datacenter="dc1"}
```

### 2. Custom Metrics

```java
@Component
public class CustomMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter businessMessagesCounter;
    private final Timer messageProcessingTimer;
    
    public CustomMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.businessMessagesCounter = Counter.builder("business.messages.processed")
            .tag("type", "order")
            .register(meterRegistry);
        this.messageProcessingTimer = Timer.builder("business.message.processing.time")
            .register(meterRegistry);
    }
    
    public void recordMessageProcessed(String messageType) {
        businessMessagesCounter.increment(Tags.of("message.type", messageType));
    }
    
    public void recordProcessingTime(Duration duration, String operation) {
        Timer.Sample.start(meterRegistry)
            .stop(Timer.builder("message.processing.time")
                .tag("operation", operation)
                .register(meterRegistry));
    }
}
```

### 3. Health Endpoints

```bash
# Application health (includes JMS library health)
GET /actuator/health

# Detailed JMS health information
GET /actuator/health/jms

# Metrics endpoint
GET /actuator/metrics

# Specific JMS metrics
GET /actuator/metrics/jms.circuit.breaker.calls
```

### 4. Logging Configuration

```yaml
logging:
  level:
    com.prospringjms.lib: INFO
    com.prospringjms.lib.health: DEBUG
    com.prospringjms.lib.security: WARN
    io.github.resilience4j: INFO
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{datacenter}] %logger{36} - %msg%n"
    
  loggers:
    com.prospringjms.lib.security.SecurityManager:
      level: INFO
      additivity: false
      appenders:
        - name: SECURITY_LOG
          type: RollingFile
          fileName: logs/jms-security.log
```

## Security

### 1. Message Encryption

The library provides automatic message encryption using AES-256-GCM:

```java
// Enable encryption in configuration
jms:
  security:
    encryption:
      enabled: true
      algorithm: AES-256-GCM
      key-rotation-interval: 24h

// Messages are automatically encrypted/decrypted
SendResult result = sender.sendToPrimary("secure.queue", sensitiveData);
```

### 2. Audit Logging

```java
// Audit configuration
jms:
  security:
    audit:
      enabled: true
      log-level: INFO
      include-message-content: false  # For privacy
      audit-destinations:
        - "audit.queue"
        - "file:/var/log/jms-audit.log"

// Audit events are automatically logged
2024-10-03 10:30:15 [audit] INFO - MESSAGE_SENT: user=john.doe, datacenter=dc1, destination=orders.queue, messageId=12345
2024-10-03 10:30:16 [audit] INFO - MESSAGE_RECEIVED: user=system, datacenter=dc1, destination=orders.queue, messageId=12345
```

### 3. Access Control

```java
@Component
public class MessageAccessControl {
    
    @PreAuthorize("hasRole('MESSAGE_SENDER')")
    public SendResult sendMessage(String destination, Object message) {
        return jmsLibrary.getSender().sendToPrimary(destination, message);
    }
    
    @PreAuthorize("hasPermission(#destination, 'RECEIVE')")
    @JmsListener(destination = "#{@destinationResolver.resolve('orders')}")
    public void receiveMessage(@Payload Object message, 
                              @Header("destination") String destination) {
        // Process message
    }
}
```

## Resilience Patterns

### 1. Circuit Breaker Pattern

```java
// Automatic circuit breaker protection
SendResult result = resilienceManager.executeWithCircuitBreaker("dc1", () -> {
    return template.convertAndSend("orders.queue", message);
});

// Circuit breaker states: CLOSED -> OPEN -> HALF_OPEN -> CLOSED
// Monitor state transitions
circuitBreaker.getEventPublisher().onStateTransition(event -> 
    log.info("Circuit breaker state change: {} -> {}", 
        event.getStateTransition().getFromState(),
        event.getStateTransition().getToState())
);
```

### 2. Retry with Exponential Backoff

```java
// Configuration
jms:
  resiliency:
    retry:
      enabled: true
      max-attempts: 3
      initial-delay-ms: 1000
      backoff-multiplier: 2.0      # 1s, 2s, 4s

// Automatic retry on transient failures
SendResult result = resilienceManager.executeWithFullResilience("dc1", () -> {
    // This operation will be retried on failure
    return performJmsOperation();
});
```

### 3. Rate Limiting

```java
// Prevent overwhelming downstream systems
jms:
  resiliency:
    rate-limiting:
      enabled: true
      max-requests-per-second: 1000
      window-size-ms: 1000

// Rate limiting is automatically applied
for (int i = 0; i < 10000; i++) {
    // Rate limiter will throttle these calls
    sender.sendToPrimary("orders.queue", createOrder(i));
}
```

### 4. Bulkhead Pattern

```java
// Isolate failures between different operations
jms:
  resiliency:
    bulk-head:
      enabled: true
      max-concurrent-calls: 100
      max-wait-ms: 5000

// Each datacenter has its own bulkhead
CompletableFuture<SendResult> future1 = resilienceManager
    .executeAsyncWithFullResilience("dc1", () -> sendToDatacenter1());
    
CompletableFuture<SendResult> future2 = resilienceManager
    .executeAsyncWithFullResilience("dc2", () -> sendToDatacenter2());
```

## Troubleshooting

### Common Issues

#### 1. Connection Failures

**Problem:** `JmsLibraryException: Failed to connect to datacenter dc1`

**Solution:**
```yaml
# Check datacenter configuration
jms:
  datacenters:
    dc1:
      host: correct-hostname
      port: correct-port
      username: valid-username
      password: valid-password
      
# Increase connection timeout
jms:
  datacenters:
    dc1:
      connection-pool:
        idle-timeout: 60000  # Increase timeout
```

#### 2. Circuit Breaker Issues

**Problem:** Circuit breaker is constantly OPEN

**Solution:**
```yaml
# Adjust circuit breaker settings
jms:
  resiliency:
    circuit-breaker:
      failure-threshold: 10      # Increase threshold
      reset-timeout-ms: 120000   # Increase reset time
```

#### 3. Message Delivery Failures

**Problem:** Messages are not being delivered

**Diagnostic Steps:**
```bash
# Check health endpoints
curl http://localhost:8080/actuator/health/jms

# Check metrics
curl http://localhost:8080/actuator/metrics/jms.message.failure

# Enable debug logging
logging:
  level:
    com.prospringjms.lib: DEBUG
```

#### 4. Performance Issues

**Problem:** Slow message processing

**Optimization:**
```yaml
# Increase connection pool size
jms:
  datacenters:
    dc1:
      connection-pool:
        max-connections: 50
        max-sessions-per-connection: 20
        
# Adjust resilience timeouts
jms:
  resiliency:
    circuit-breaker:
      timeout-ms: 5000  # Reduce timeout
```

### Debug Configuration

```yaml
# Enable comprehensive debugging
logging:
  level:
    com.prospringjms.lib: DEBUG
    com.prospringjms.lib.sender.ResilientJmsSender: TRACE
    com.prospringjms.lib.health.HealthCheckManager: DEBUG
    io.github.resilience4j: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"  # Expose all actuator endpoints
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
```

## Best Practices

### 1. Configuration Management

```yaml
# Use environment-specific profiles
spring:
  profiles:
    active: ${ENVIRONMENT:dev}
    
# Externalize sensitive configuration
jms:
  datacenters:
    dc1:
      username: ${JMS_USERNAME_DC1}
      password: ${JMS_PASSWORD_DC1}
      
# Use configuration validation
jms:
  validation:
    enabled: true
    fail-fast: true
```

### 2. Error Handling

```java
@Component
public class RobustMessageHandler {
    
    @JmsListener(destination = "orders.queue")
    @Retryable(value = {TransientException.class}, maxAttempts = 3)
    public void processOrder(@Payload Order order) {
        try {
            orderService.process(order);
        } catch (TransientException e) {
            // Will be retried by @Retryable
            throw e;
        } catch (PermanentException e) {
            // Send to DLQ for manual review
            dlqSender.send(order, e.getMessage());
        }
    }
    
    @Recover
    public void recoverOrderProcessing(TransientException e, Order order) {
        // Final recovery after all retries failed
        dlqSender.send(order, "Retry exhausted: " + e.getMessage());
    }
}
```

### 3. Message Design

```java
// Use versioned message schemas
public class OrderMessageV1 {
    private String version = "1.0";
    private String orderId;
    private LocalDateTime timestamp;
    // ... other fields
}

// Include correlation IDs for tracing
public class TrackedMessage {
    private String correlationId = UUID.randomUUID().toString();
    private String traceId;
    // ... message content
}

// Use proper message headers
Map<String, Object> headers = Map.of(
    "messageType", "ORDER_CREATED",
    "version", "1.0",
    "correlationId", correlationId,
    "timestamp", System.currentTimeMillis(),
    "source", "order-service"
);
```

### 4. Monitoring Strategy

```java
@Component
public class MessageMonitoring {
    
    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        // Track business metrics
        meterRegistry.counter("business.orders.created",
            "datacenter", event.getDatacenter(),
            "region", event.getRegion())
            .increment();
    }
    
    @EventListener
    public void onCircuitBreakerOpen(CircuitBreakerOpenEvent event) {
        // Send alerts for circuit breaker state changes
        alertService.sendAlert(
            "Circuit breaker OPEN for datacenter: " + event.getDatacenter(),
            AlertSeverity.HIGH
        );
    }
}
```

### 5. Testing Strategies

```java
@SpringBootTest
@ActiveProfiles("test")
class MessageIntegrationTest {
    
    @Autowired
    private JmsLibraryManager jmsLibrary;
    
    @Test
    void shouldSendMessageSuccessfully() {
        // Given
        Order order = createTestOrder();
        
        // When
        SendResult result = jmsLibrary.getSender()
            .sendToPrimary("orders.queue", order);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDatacenter()).isEqualTo("dc1");
    }
    
    @Test
    void shouldFailoverOnDatacenterFailure() {
        // Given
        simulateDatacenterFailure("dc1");
        
        // When
        SendResult result = jmsLibrary.getSender()
            .sendToPrimary("orders.queue", createTestOrder());
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDatacenter()).isEqualTo("dc2");  // Failover
    }
}
```

## Migration Guide

### From Spring JMS

```java
// Before (Standard Spring JMS)
@Autowired
private JmsTemplate jmsTemplate;

public void sendMessage(Object message) {
    jmsTemplate.convertAndSend("orders.queue", message);
}

// After (Pro Spring JMS Library)
@Autowired
private JmsLibraryManager jmsLibrary;

public void sendMessage(Object message) {
    SendResult result = jmsLibrary.getSender()
        .sendToPrimary("orders.queue", message);
    // Now includes automatic resilience, routing, and monitoring
}
```

### Configuration Migration

```yaml
# Before (Standard Spring JMS)
spring:
  jms:
    template:
      default-destination: orders.queue
  artemis:
    host: localhost
    port: 61616

# After (Pro Spring JMS Library)
jms:
  default-datacenter: dc1
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
      queues:
        orders: orders.queue
  # Plus all the advanced features: resilience, security, routing, etc.
```

## API Reference

### Core Classes

#### JmsLibraryManager
```java
public class JmsLibraryManager implements HealthIndicator {
    public ResilientJmsSender getSender()
    public JmsListenerRegistry getListenerRegistry()
    public DatacenterRouter getRouter()
    public HealthCheckManager getHealthManager()
    public Resilience4jManager getResilienceManager()
    public SecurityManager getSecurityManager()
}
```

#### ResilientJmsSender
```java
public class ResilientJmsSender {
    public SendResult sendToPrimary(String destination, Object message)
    public SendResult sendToPrimary(String destination, Object message, Map<String, Object> headers)
    public SendResult sendToDatacenter(String datacenter, String destination, Object message)
    public SendResult sendWithAffinity(SendRequest request)
    public CompletableFuture<SendResult> sendAsync(SendRequest request)
}
```

#### SendRequest (Builder Pattern)
```java
SendRequest request = SendRequest.builder()
    .destination("orders.queue")
    .message(orderObject)
    .headers(headerMap)
    .region("us-east")
    .zone("1a")
    .preferredDatacenters(List.of("dc1", "dc2"))
    .excludedDatacenters(List.of("dc3"))
    .messageType("ORDER_CREATED")
    .build();
```

#### SendResult
```java
public class SendResult {
    public String getDatacenter()
    public String getDestination()
    public boolean isSuccess()
    public long getDurationMs()
    public String getError()
}
```

### Configuration Classes

#### JmsLibraryProperties
```java
@ConfigurationProperties(prefix = "jms")
public class JmsLibraryProperties {
    private String defaultDatacenter
    private Map<String, DataCenter> datacenters
    private RoutingConfig routing
    private ResiliencyConfig resiliency
    private SecurityConfig security
}
```

### Exception Hierarchy

```java
public class JmsLibraryException extends RuntimeException {
    public JmsLibraryException(String message, String datacenter, String operation)
    public JmsLibraryException(String message, String datacenter, String operation, Throwable cause)
    
    public String getDatacenter()
    public String getOperation()
}
```

## Support and Resources

### Documentation
- **GitHub Repository**: https://github.com/company/pro-spring-jms
- **API Documentation**: https://javadoc.company.com/pro-spring-jms
- **Spring Boot Integration Guide**: https://docs.company.com/spring-boot-jms

### Community
- **Stack Overflow**: Tag questions with `pro-spring-jms`
- **GitHub Issues**: Report bugs and feature requests
- **Slack Channel**: #pro-spring-jms

### Enterprise Support
- **Email**: support@company.com
- **Enterprise Portal**: https://support.company.com
- **Phone**: +1-800-JMS-HELP

---

*This user guide covers version 1.0.0 of the Pro Spring JMS Multi-Datacenter Library. For the latest updates and features, please refer to the official documentation.*