# Pro Spring JMS Library - Quick Reference

## � Library Module Structure

The Pro Spring JMS Library is available as a standalone module:

```text
pro-spring-jms/
├── lib/                    # 🎯 Standalone Library Module
│   ├── build.gradle       # Library-specific build configuration
│   └── src/main/java/com/example/jms/
│       ├── config/        # Auto-configuration
│       ├── health/        # Health monitoring
│       ├── listener/      # Dynamic listeners
│       ├── registry/      # Central manager
│       ├── resilience/    # Resilience4j integration
│       ├── routing/       # Multi-datacenter routing
│       ├── security/      # Enterprise security
│       └── sender/        # Resilient messaging
└── src/                   # Demo application (optional)
```

### Library Artifacts

- `lib-1.0.0.jar` - Main library
- `lib-1.0.0-sources.jar` - Source code
- `lib-1.0.0-javadoc.jar` - API documentation

## �🚀 Quick Start

```yaml
# application.yml
jms:
  default-datacenter: dc1
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
```

```java
@Autowired
private JmsLibraryManager jmsLibrary;

// Send message
SendResult result = jmsLibrary.getSender()
    .sendToPrimary("orders.queue", message);

// Listen for messages  
@JmsListener(destination = "orders.queue")
public void processOrder(Order order) {
    // Process order
}
```

## 📋 Common Operations

### Send Messages

```java
// Basic send to primary datacenter
SendResult result = sender.sendToPrimary("queue.name", message);

// Send with headers
Map<String, Object> headers = Map.of("priority", "high");
SendResult result = sender.sendToPrimary("queue.name", message, headers);

// Send to specific datacenter
SendResult result = sender.sendToDatacenter("dc2", "queue.name", message);

// Send with routing preferences
SendRequest request = SendRequest.builder()
    .destination("orders.queue")
    .message(order)
    .region("us-east")
    .preferredDatacenters(List.of("dc1", "dc2"))
    .build();
SendResult result = sender.sendWithAffinity(request);
```

### Listen for Messages

```java
// Basic listener
@JmsListener(destination = "orders.queue")
public void handleOrder(@Payload Order order) {
    // Process order
}

// Listener with headers
@JmsListener(destination = "notifications.topic")  
public void handleNotification(
    @Payload Notification notification,
    @Header("region") String region) {
    // Process notification
}

// Dynamic listener registration
jmsListenerRegistry.registerListener(
    "dynamic.queue", 
    message -> processMessage(message),
    "dc1"
);
```

### Health Monitoring

```bash
# Check overall health
curl http://localhost:8080/actuator/health

# Check JMS health specifically  
curl http://localhost:8080/actuator/health/jms

# View metrics
curl http://localhost:8080/actuator/metrics/jms.message.sent
```

## ⚙️ Configuration Patterns

### Multi-Datacenter Setup

```yaml
jms:
  datacenters:
    dc1:
      type: artemis
      host: artemis1.company.com
      port: 61616
    dc2:
      type: ibmmq
      host: mq2.company.com
      port: 1414
      queue-manager: QM2
  routing:
    strategy: primary-backup
    failover:
      enabled: true
```

### Resilience Configuration

```yaml
jms:
  resiliency:
    circuit-breaker:
      enabled: true
      failure-threshold: 5
      reset-timeout-ms: 60000
    retry:
      enabled: true
      max-attempts: 3
      initial-delay-ms: 1000
      backoff-multiplier: 2.0
```

### Security Configuration

```yaml
jms:
  security:
    encryption:
      enabled: true
      algorithm: AES-256-GCM
    audit:
      enabled: true
      log-level: INFO
```

## 🔧 Troubleshooting

### Common Issues

```yaml
# Connection timeout issues
jms:
  datacenters:
    dc1:
      connection-pool:
        idle-timeout: 60000

# Circuit breaker too sensitive
jms:
  resiliency:
    circuit-breaker:
      failure-threshold: 10
      reset-timeout-ms: 120000

# Enable debug logging
logging:
  level:
    com.prospringjms.lib: DEBUG
```

### Health Check Commands

```bash
# Test datacenter connectivity
curl -s http://localhost:8080/actuator/health | jq '.components.jms'

# Monitor circuit breaker state
curl -s http://localhost:8080/actuator/metrics/jms.circuit.breaker.state

# Check message throughput
curl -s http://localhost:8080/actuator/metrics/jms.message.sent
```

## 📊 Monitoring & Metrics

### Key Metrics to Monitor

```bash
# Message metrics
jms.message.sent{datacenter, destination}
jms.message.send.duration{datacenter, destination}
jms.message.failure{datacenter, error.type}

# Resilience metrics  
jms.circuit.breaker.state{datacenter}
jms.circuit.breaker.failure.rate{datacenter}
jms.retry.calls{datacenter, kind}
jms.rate.limiter.available.permissions{datacenter}

# Health metrics
jms.datacenter.health{datacenter, status}
jms.connection.pool.active{datacenter}
```

### Alerting Rules

```yaml
# Circuit breaker open alert
- alert: JMSCircuitBreakerOpen
  expr: jms_circuit_breaker_state{state="open"} == 1
  for: 1m
  
# High failure rate alert  
- alert: JMSHighFailureRate
  expr: jms_circuit_breaker_failure_rate > 0.1
  for: 5m
  
# Datacenter unhealthy alert
- alert: JMSDatacenterUnhealthy
  expr: jms_datacenter_health{status="unhealthy"} == 1
  for: 30s
```

## 🛡️ Security Best Practices

### Message Encryption

```java
// Encryption is automatic when enabled
jms:
  security:
    encryption:
      enabled: true

// Messages are automatically encrypted/decrypted
SendResult result = sender.sendToPrimary("secure.queue", sensitiveData);
```

### Access Control

```java
@PreAuthorize("hasRole('MESSAGE_SENDER')")
public SendResult sendMessage(String destination, Object message) {
    return jmsLibrary.getSender().sendToPrimary(destination, message);
}

@PreAuthorize("hasPermission(#destination, 'RECEIVE')")  
@JmsListener(destination = "secure.queue")
public void receiveSecureMessage(@Payload String message) {
    // Process secure message
}
```

## 🧪 Testing Patterns

### Integration Testing

```java
@SpringBootTest
@ActiveProfiles("test")
class JmsIntegrationTest {
    
    @Autowired
    private JmsLibraryManager jmsLibrary;
    
    @Test
    void shouldSendMessageSuccessfully() {
        SendResult result = jmsLibrary.getSender()
            .sendToPrimary("test.queue", "test message");
            
        assertThat(result.isSuccess()).isTrue();
    }
    
    @Test
    void shouldFailoverOnDatacenterFailure() {
        // Simulate failure and test failover
        simulateDatacenterFailure("dc1");
        
        SendResult result = jmsLibrary.getSender()
            .sendToPrimary("test.queue", "test message");
            
        assertThat(result.getDatacenter()).isEqualTo("dc2");
    }
}
```

### Test Configuration

```yaml
# application-test.yml
jms:
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
  security:
    encryption:
      enabled: false  # Disable for testing
  resiliency:
    circuit-breaker:
      failure-threshold: 100  # More lenient for tests
```

## 📚 Migration from Standard Spring JMS

### Before (Spring JMS)

```java
@Autowired
private JmsTemplate jmsTemplate;

public void sendMessage(Object message) {
    jmsTemplate.convertAndSend("orders.queue", message);
}

@JmsListener(destination = "orders.queue")  
public void receiveMessage(Object message) {
    // Process message
}
```

### After (Pro Spring JMS Library)

```java
@Autowired
private JmsLibraryManager jmsLibrary;

public void sendMessage(Object message) {
    SendResult result = jmsLibrary.getSender()
        .sendToPrimary("orders.queue", message);
    // Now includes: resilience, routing, monitoring, security
}

@JmsListener(destination = "orders.queue")
public void receiveMessage(Object message) {
    // Same listener pattern, enhanced with library features
}
```

### Configuration Migration

```yaml
# Before
spring.jms.template.default-destination: orders.queue
spring.artemis.host: localhost
spring.artemis.port: 61616

# After  
jms:
  default-datacenter: dc1
  datacenters:
    dc1:
      type: artemis
      host: localhost
      port: 61616
      queues:
        orders: orders.queue
```

## 🔗 Useful Links

- **Full User Guide**: [USER_GUIDE.md](USER_GUIDE.md)  
- **Resilience4j Integration**: [RESILIENCE4J_INTEGRATION.md](RESILIENCE4J_INTEGRATION.md)
- **GitHub Repository**: <https://github.com/company/pro-spring-jms>
- **Spring Boot Actuator**: <https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html>

## 📞 Support

- **Issues**: GitHub Issues
- **Questions**: Stack Overflow with `pro-spring-jms` tag
- **Enterprise Support**: <support@company.com>

---

Pro Spring JMS Multi-Datacenter Library v1.0.0
