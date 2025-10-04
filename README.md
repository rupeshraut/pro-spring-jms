# Pro Spring JMS - Multi-Datacenter Production Library

A production-ready Java Spring Boot library for multi-datacenter JMS messaging with enterprise-grade features including datacenter affinity, intelligent routing, failover, and resiliency patterns.

## 📚 Documentation

- **📖 [Comprehensive User Guide](docs/USER_GUIDE.md)** - Complete documentation covering installation, configuration, usage, monitoring, security, and best practices
- **⚙️ [Configuration Guide](docs/CONFIGURATION_GUIDE.md)** - Comprehensive YAML configuration reference with examples for all features
- **⚡ [Quick Reference](docs/QUICK_REFERENCE.md)** - Developer quick reference with common patterns and troubleshooting
- **🆚 [Feature Comparison](docs/FEATURE_COMPARISON.md)** - Detailed comparison vs Standard Spring JMS showing advantages and migration path
- **🏥 Health & Monitoring** - Built-in actuator endpoints and custom health indicators

## 🌟 Key Features

### 📡 Multi-Datacenter Messaging
- **Datacenter Affinity**: Route messages based on geographic or logical affinity rules
- **Intelligent Routing**: Smart message routing with health-aware load balancing
- **Cross-Region Support**: Manage messaging across multiple geographic regions
- **Priority-Based Routing**: Configure datacenter priorities for optimal performance

### 🔄 High Availability & Resilience
- **Automatic Failover**: Seamless failover with configurable strategies
- **Circuit Breaker Pattern**: Prevent cascading failures with circuit breakers
- **Retry Logic**: Exponential backoff and configurable retry strategies  
- **Health Monitoring**: Continuous health checks with automatic recovery

### 🏢 Enterprise Integration
- **IBM MQ Support**: Full enterprise IBM MQ integration
- **ActiveMQ Artemis**: High-performance open-source messaging
- **Jakarta JMS 3.0**: Modern JMS specification compliance
- **Connection Pooling**: Optimized connection management and pooling

### ⚙️ Production Ready
- **Dynamic Listener Registration**: Register and manage listeners at runtime
- **Transaction Management**: Full JMS transaction support with session management
- **Monitoring & Metrics**: Spring Boot Actuator integration with custom health indicators
- **Configuration Flexibility**: Comprehensive YAML-based configuration

## 🏗️ Architecture

### Core Components

```
JmsLibraryManager
├── ResilientJmsSender      # Smart message sending with failover
├── JmsListenerRegistry     # Dynamic listener management  
├── DatacenterRouter        # Intelligent routing logic
├── JmsLibraryProperties    # Configuration management
└── Health Monitoring       # Continuous health checks
```

### Multi-Datacenter Routing

```
Message Send Request
       ↓
DatacenterRouter
├── Check Affinity Rules
├── Evaluate Health Status  
├── Apply Load Balancing
└── Select Target Datacenter
       ↓
ResilientJmsSender
├── Circuit Breaker Check
├── Send with Retry Logic
└── Update Health Metrics
```

## 🚀 Quick Start

### 1. Add Dependency

```gradle
dependencies {
    implementation project(':lib') // Local library
    // OR when published:
    // implementation 'com.prospringjms:pro-spring-jms-lib:1.0.0'
}
```

### 2. Basic Configuration

```yaml
# application.yml
jms:
  lib:
    primary-datacenter: "us-east-1"
    datacenters:
      us-east-1:
        type: artemis
        host: artemis-east.company.com
        port: 61616
        username: ${JMS_USERNAME}
        password: ${JMS_PASSWORD}
      us-west-1:
        type: ibmmq
        host: mqm-west.company.com
        port: 1414
        queue-manager: QM_WEST
        channel: SYSTEM.DEF.SVRCONN
```

### 3. Use the Library

```java
@RestController
public class MessagingController {
    
    @Autowired
    private JmsLibraryManager libraryManager;
    
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody MessageRequest request) {
        ResilientJmsSender sender = libraryManager.getSender();
        
        // Send to primary datacenter with automatic failover
        SendResult result = sender.sendToPrimary(request.getDestination(), request.getMessage());
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/register-listener")  
    public ResponseEntity<?> registerListener(@RequestBody ListenerRequest request) {
        JmsListenerRegistry registry = libraryManager.getListenerRegistry();
        
        // Register dynamic listener with datacenter affinity
        String listenerId = registry.registerListener(
            request.getDestination(),
            request.getDatacenter(),
            message -> processMessage(message)
        );
        
        return ResponseEntity.ok(Map.of("listenerId", listenerId));
    }
}
```

## 📋 Library Usage Examples

### Smart Message Sending

```java
// Send with affinity rules
SendRequest request = new SendRequest.Builder()
    .destination("orders.queue")
    .message(orderData)
    .region("us-east")
    .preferredDatacenters(List.of("us-east-1", "us-east-2"))
    .messageType("high-priority")
    .build();

SendResult result = sender.sendWithAffinity(request);
```

### Dynamic Listener Registration

```java
// Register session-aware listener with transactions
ListenerRegistration registration = new ListenerRegistration.Builder()
    .destination("payments.queue")
    .datacenter("us-east-1")
    .sessionTransacted(true)
    .concurrency("3-10")
    .sessionAwareMessageListener((message, session) -> {
        try {
            processPayment(message);
            session.commit();
        } catch (Exception e) {
            logger.error("Payment failed", e);
            session.rollback();
        }
    })
    .build();

String listenerId = registry.registerListener(registration);
```

### Broadcast Messaging

```java
// Send to all available datacenters
List<SendResult> results = sender.broadcast("notification.queue", notificationMessage);
results.forEach(result -> 
    logger.info("Sent to {}: {}", result.getDatacenter(), result.isSuccess())
);
```

### Health Monitoring

```java
// Get comprehensive health status
JmsLibraryHealth health = libraryManager.getHealthStatus();
logger.info("Overall healthy: {}", health.isOverallHealthy());
logger.info("Running listeners: {}", health.getTotalRunningListeners());

// Check specific datacenter health  
health.getDatacenterHealth().forEach((dc, dcHealth) ->
    logger.info("Datacenter {}: {}", dc, dcHealth.isHealthy())
);
```

## 🔧 Advanced Configuration

### Production Multi-Datacenter Setup

```yaml
jms:
  lib:
    primary-datacenter: "us-east-1-artemis"
    
    # Datacenter definitions with full configuration
    datacenters:
      us-east-1-artemis:
        type: artemis
        host: artemis-us-east-1.company.com
        port: 61616
        username: ${JMS_USERNAME}
        password: ${JMS_PASSWORD}
        client-id: ${spring.application.name}-us-east-1
        priority: 100
        connection-pool:
          max-connections: 20
          idle-timeout: 300000
          max-sessions-per-connection: 10
        queues:
          orders: "orders.queue"
          payments: "payments.queue"
          notifications: "notifications.queue"
        affinity:
          region: "us-east"
          zone: "us-east-1a"
          preferred-datacenters: ["us-east-1-artemis", "us-east-2-artemis"]
          
      eu-central-1-ibmmq:
        type: ibmmq  
        host: mqm-eu-central-1.company.com
        port: 1414
        queue-manager: QM_EU_CENTRAL_1
        channel: SYSTEM.DEF.SVRCONN
        username: ${JMS_USERNAME}
        password: ${JMS_PASSWORD}
        priority: 200
        connection-pool:
          max-connections: 15
          idle-timeout: 300000
        queues:
          orders: "ORDERS.QUEUE"
          payments: "PAYMENTS.QUEUE"
        affinity:
          region: "eu-central"
          zone: "eu-central-1a"
          
    # Failover configuration
    failover:
      enabled: true
      strategy: "priority"
      max-retries: 3
      retry-delay-ms: 1000
      
    # Load balancing
    load-balancing:
      strategy: "weighted-round-robin"
      
    # Resiliency patterns
    resiliency:
      circuit-breaker:
        enabled: true
        failure-threshold: 5
        reset-timeout-ms: 60000
      retry:
        enabled: true
        max-attempts: 3
        exponential-backoff: true
        
    # Health monitoring
    health-check:
      enabled: true
      interval-ms: 30000
      timeout-ms: 5000
```

### Environment-Specific Configuration

```yaml
# application-dev.yml - Development with embedded broker
jms:
  lib:
    primary-datacenter: "dev-embedded"
    datacenters:
      dev-embedded:
        type: artemis
        host: localhost
        port: 61616
        
# application-staging.yml - Staging environment
jms:
  lib:
    primary-datacenter: "staging-artemis"
    datacenters:
      staging-artemis:
        type: artemis
        host: artemis-staging.company.com
        port: 61616
        
# application-prod.yml - Production with multiple DCs
jms:
  lib:
    primary-datacenter: "prod-primary"
    datacenters:
      prod-primary: { ... }
      prod-secondary: { ... }
      prod-dr: { ... }
```

## 📊 Monitoring & Operations

### Spring Boot Actuator Integration

```java
// Custom health indicator automatically registered
@Component
public class JmsLibraryHealthIndicator implements HealthIndicator {
    
    @Override  
    public Health health() {
        JmsLibraryHealth libHealth = libraryManager.getHealthStatus();
        
        return libHealth.isOverallHealthy() ? 
            Health.up()
                .withDetail("datacenters", libHealth.getDatacenterHealth())
                .withDetail("listeners", libHealth.getTotalRunningListeners())
                .build() :
            Health.down()
                .withDetail("issues", getHealthIssues(libHealth))
                .build();
    }
}
```

### Metrics and Observability

```yaml
# Enable metrics and monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,jms-library
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
        
logging:
  level:
    '[com.prospringjms.lib]': INFO
    '[org.springframework.jms]': DEBUG
```

### Operations Dashboard

Access operational endpoints:

- `/actuator/health` - Overall application health
- `/actuator/health/jms-library` - JMS library specific health  
- `/actuator/metrics/jms.library.*` - Library metrics
- `/management/jms/listeners` - Active listeners status
- `/management/jms/datacenters` - Datacenter health status

## 🏃‍♂️ Running the Showcase

### Development Mode

```bash
# Start with embedded Artemis (no external dependencies)
./gradlew bootRun --args="--spring.profiles.active=dev"

# Application starts on http://localhost:8080
# Embedded broker on tcp://localhost:61616
```

### Production Mode

```bash  
# Configure external brokers and run
export JMS_USERNAME=your-jms-user
export JMS_PASSWORD=your-jms-password
./gradlew bootRun --args="--spring.profiles.active=prod"
```

### Test the Library

```bash
# Send message to primary datacenter
curl -X POST http://localhost:8080/api/jms/send \
  -H "Content-Type: application/json" \
  -d '{"destination": "test.queue", "message": "Hello World"}'

# Check library health  
curl http://localhost:8080/actuator/health/jms-library

# View active listeners
curl http://localhost:8080/management/jms/listeners

# Trigger failover test
curl -X POST http://localhost:8080/api/jms/failover/us-east-1/us-west-1
```

## 🏗️ Building and Testing

```bash
# Build the complete project
./gradlew build

# Run comprehensive tests
./gradlew test

# Build library JAR
./gradlew :lib:jar

# Run integration tests
./gradlew integrationTest

# Generate coverage reports
./gradlew jacocoTestReport
```

## 📦 Production Deployment

### Docker Deployment

```dockerfile
FROM openjdk:17-jre-slim

COPY build/libs/pro-spring-jms-*.jar app.jar

# Configure JVM for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pro-spring-jms
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pro-spring-jms
  template:
    metadata:
      labels:
        app: pro-spring-jms
    spec:
      containers:
      - name: app
        image: pro-spring-jms:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JMS_USERNAME
          valueFrom:
            secretKeyRef:
              name: jms-credentials
              key: username
        - name: JMS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: jms-credentials  
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
```

## 🔍 Troubleshooting

### Common Issues

| Issue | Symptoms | Solution |
|-------|----------|----------|
| Connection Timeouts | `ConnectionException` during startup | Check network connectivity and firewall rules |
| Authentication Failures | `SecurityException` in logs | Verify JMS credentials and permissions |
| Bean Conflicts | `BeanDefinitionOverrideException` | Use profile exclusions or bean qualifiers |
| Memory Issues | `OutOfMemoryError` | Tune connection pool settings |

### Debug Configuration

```yaml
# Enable comprehensive debugging
logging:
  level:
    '[com.prospringjms.lib]': DEBUG
    '[org.springframework.jms]': DEBUG
    '[org.apache.activemq]': DEBUG
    '[com.ibm.mq]': DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

## 🤝 Contributing

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)  
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Guidelines

- Follow Spring Boot best practices
- Add comprehensive tests for new features
- Update documentation for API changes
- Use conventional commit messages
- Ensure backward compatibility

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## � Documentation

- **📖 [Comprehensive User Guide](docs/USER_GUIDE.md)** - Complete documentation covering installation, configuration, usage, monitoring, security, and best practices
- **⚡ [Quick Reference](docs/QUICK_REFERENCE.md)** - Developer quick reference with common patterns and troubleshooting
- **🔧 Configuration** - See examples in this README and detailed options in the User Guide
- **🏥 Health & Monitoring** - Built-in actuator endpoints and custom health indicators

## 🙋‍♂️ Support

- **Issues**: [GitHub Issues](https://github.com/your-org/pro-spring-jms/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/pro-spring-jms/discussions)
- **Enterprise Support**: Contact enterprise-support@company.com

---

**Built with ❤️ using Spring Boot, Jakarta JMS, and enterprise messaging best practices.**

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │    │  JMS Templates   │    │  Datacenters    │
│                 │    │                  │    │                 │
│ /api/jms/send   │───▶│  DC1: Artemis    │───▶│  DC1: Artemis   │
│ /api/jms/send/  │    │  DC2: Artemis    │    │  DC2: Artemis   │
│      {dc}       │    │  DC3: IBM MQ     │    │  DC3: IBM MQ    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  JMS Listeners   │
                    │                  │
                    │ - Session Mgmt   │
                    │ - Commit/Rollback│
                    │ - Error Handling │
                    └──────────────────┘
```

## Configuration

### YAML Configuration Structure

```yaml
jms:
  default-datacenter: dc1
  datacenters:
    dc1:
      type: artemis              # or "ibmmq"
      host: localhost
      port: 61616
      username: admin
      password: admin
      client-id: pro-spring-jms-dc1
      connection-pool:
        max-connections: 10
        idle-timeout: 30000
        max-sessions-per-connection: 10
      queues:
        orders: orders.queue
        notifications: notifications.queue
        dlq: dlq.queue
      topics:
        events: events.topic
```

### Supported JMS Providers

1. **ActiveMQ Artemis**
   - Type: `artemis`
   - Connection: TCP-based
   - Features: Full JMS 2.0 support, clustering, persistence

2. **IBM MQ**
   - Type: `ibmmq`
   - Connection: Client connection with channel/queue manager
   - Features: Enterprise-grade reliability, transactional support

## Quick Start

### Prerequisites

- Java 17 or higher
- Gradle 8.5+
- ActiveMQ Artemis (for local testing)
- IBM MQ (optional, for IBM MQ testing)

### Running the Application

1. **Clone and build**:
   ```bash
   ./gradlew build
   ```

2. **Start ActiveMQ Artemis** (for local testing):
   ```bash
   # Download and start Artemis broker
   artemis create --user admin --password admin --require-login mybroker
   cd mybroker/bin
   ./artemis run
   ```

3. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```

4. **Test the endpoints**:
   ```bash
   # Send message to default datacenter
   curl -X POST http://localhost:8080/api/jms/send?destination=orders.queue \
        -H "Content-Type: text/plain" \
        -d "Test order message"

   # Send message to specific datacenter
   curl -X POST http://localhost:8080/api/jms/send/dc1?destination=orders.queue \
        -H "Content-Type: text/plain" \
        -d "Test order for DC1"

   # Get available datacenters
   curl http://localhost:8080/api/jms/datacenters

   # Send test messages (includes rollback scenarios)
   curl -X POST http://localhost:8080/api/jms/test/dc1
   ```

## JMS Listener Features

### Transaction Management

The JMS listeners demonstrate session transaction management:

```java
@JmsListener(destination = "orders.queue")
public void handleOrderMessage(String message, Session session) {
    try {
        // Process message
        processOrder(message);
        
        // Commit on success
        session.commit();
        
    } catch (Exception e) {
        // Rollback on error
        session.rollback();
    }
}
```

### Rollback Scenarios

- Messages containing "INVALID" trigger rollback
- Messages containing "FAIL" in notifications trigger rollback
- Messages containing "ERROR" in events trigger rollback

### Multiple Listeners

- **Order Queue**: Processes orders with transaction management
- **Notification Queue**: Handles notifications
- **Dead Letter Queue**: Processes failed messages
- **Event Topic**: Handles broadcast events

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/jms/send` | POST | Send message to default datacenter |
| `/api/jms/send/{dc}` | POST | Send message to specific datacenter |
| `/api/jms/send/{dc}/with-headers` | POST | Send message with custom headers |
| `/api/jms/datacenters` | GET | List available datacenters |
| `/api/jms/test/{dc}` | POST | Send test messages with rollback scenarios |
| `/api/jms/health` | GET | Health check |

## Monitoring

Spring Boot Actuator endpoints are available:

- `/actuator/health` - Application health
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

## Development

### Project Structure

```
src/
├── main/java/com/example/jms/
│   ├── ProSpringJmsApplication.java    # Main application
│   ├── config/
│   │   ├── JmsConfig.java              # JMS configuration
│   │   ├── JmsProperties.java          # Configuration properties
│   │   └── DatacenterListenerConfig.java # Listener factories
│   ├── controller/
│   │   └── JmsTestController.java      # REST endpoints
│   ├── service/
│   │   └── JmsMessageSender.java       # Message sending service
│   ├── listener/
│   │   └── JmsMessageListener.java     # JMS listeners
│   └── model/
│       └── JmsMessage.java             # Message model
└── main/resources/
    ├── application.yml                 # Main configuration
    ├── application-dev.yml             # Development profile
    └── application-prod.yml            # Production profile
```

### Adding New Datacenters

1. Add datacenter configuration in `application.yml`
2. Create listener container factory bean in `DatacenterListenerConfig`
3. Update listeners to use the new container factory

### Custom Message Processing

Extend `JmsMessageListener` or create new listeners:

```java
@JmsListener(
    destination = "custom.queue",
    containerFactory = "dc1ListenerContainerFactory"
)
public void handleCustomMessage(String message, Session session) {
    // Custom processing logic
}
```

## Production Deployment

### Environment Variables

For production, use environment variables for sensitive configuration:

```yaml
jms:
  datacenters:
    primary:
      username: ${MQ_USERNAME:defaultuser}
      password: ${MQ_PASSWORD:defaultpass}
```

### Docker Support

Build Docker image:

```bash
./gradlew bootBuildImage
```

### Kubernetes Deployment

The application is ready for Kubernetes deployment with:
- Health checks configured
- Graceful shutdown support
- External configuration via ConfigMaps/Secrets

## Troubleshooting

### Common Issues

1. **Connection refused**: Ensure JMS brokers are running
2. **Authentication failed**: Check username/password in configuration
3. **Queue not found**: Verify queue names match broker configuration
4. **Transaction rollback**: Check logs for processing exceptions

### Logging

Enable debug logging for JMS:

```yaml
logging:
  level:
    '[com.prospringjms]': DEBUG
    org.springframework.jms: DEBUG
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.