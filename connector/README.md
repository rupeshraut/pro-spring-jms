# Backend Connector Module

A comprehensive backend connectivity library for Spring Boot applications supporting REST, JMS, Kafka, and GraphQL with built-in resilience patterns using Resilience4j.

## Overview

This connector module extends the pro-spring-jms library with enhanced backend connectivity capabilities. It provides a unified interface for communicating with various backend services while incorporating enterprise-grade resilience patterns.

## Features

### Supported Protocols
- **REST API**: HTTP/HTTPS connectivity with WebClient
- **JMS Messaging**: Integration with existing ResilientJmsSender
- **Apache Kafka**: Producer operations with Spring Kafka
- **GraphQL**: Query, mutation, and subscription support

### Resilience Patterns
- **Circuit Breaker**: Prevent cascade failures
- **Retry**: Automatic retry with exponential backoff
- **Bulkhead**: Isolate critical resources
- **Rate Limiter**: Control request rates
- **Time Limiter**: Timeout protection

### Communication Modes
- **Synchronous**: Blocking operations with immediate response
- **Asynchronous**: Non-blocking operations with CompletableFuture
- **Fire-and-Forget**: One-way messaging without response

## Architecture

```
ConnectorManager
├── RestConnector (WebClient)
├── JmsConnector (ResilientJmsSender)
├── KafkaConnector (KafkaTemplate)
└── GraphQlConnector (HttpGraphQlClient)
```

Each connector extends `AbstractResilientConnector` which provides:
- Resilience4j pattern composition
- Metrics and monitoring
- Health checking
- Event handling

## VETRO Integration

The connector module is designed to integrate seamlessly with the VETRO (Validate, Enrich, Transform, Route, Operate) pattern processors:

### Enrich Step
```yaml
enrich:
  enrichments:
    - name: "customer-data"
      connector-type: "REST"
      endpoint: "https://api.example.com/customers/${message.customerId}"
      method: "GET"
      timeout: "PT3S"
      target-path: "enriched.customer"
```

### Route Step
```yaml
route:
  routing-rules:
    - condition: "transformed.priority == 'HIGH'"
      destinations:
        - connector-type: "JMS"
          destination: "queue.orders.express"
          delivery-mode: "PERSISTENT"
```

### Operate Step
```yaml
operate:
  operations:
    - name: "update-inventory"
      connector-type: "REST"
      endpoint: "https://inventory.example.com/api/reserve"
      method: "POST"
      success-action: "CONTINUE"
      failure-action: "ROLLBACK"
```

## Configuration

### Application Properties
```yaml
prospringjms:
  connector:
    circuit-breaker:
      failure-rate-threshold: 50.0
      slow-call-duration-threshold: PT2S
      wait-duration-in-open-state: PT30S
      
    retry:
      max-attempts: 3
      wait-duration: PT1S
      
    rest:
      enabled: true
      max-in-memory-size: 1048576
      
    jms:
      enabled: true
      correlation-id-header: "JMSCorrelationID"
      
    kafka:
      enabled: true
      acks: "all"
      
    graphql:
      enabled: true
      default-endpoint: "http://localhost:8080/graphql"
```

## Usage Examples

### REST Connector
```java
@Autowired
private ConnectorManager connectorManager;

public ResponseEntity<Object> callRestApi(Map<String, Object> payload) {
    RestRequest request = new RestRequest();
    request.setUrl("https://api.example.com/endpoint");
    request.setMethod("POST");
    request.setBody(payload);
    
    ConnectorContext context = ConnectorContext.builder()
        .connectorType(ConnectorType.REST)
        .request(request)
        .timeout(5000)
        .build();
        
    Object response = connectorManager.sendSync(context);
    return ResponseEntity.ok(response);
}
```

### JMS Connector
```java
public void sendJmsMessage(Map<String, Object> payload) {
    JmsRequest request = new JmsRequest();
    request.setDestination("demo.queue");
    request.setPayload(payload);
    request.setCorrelationId("demo-" + System.currentTimeMillis());
    
    ConnectorContext context = ConnectorContext.builder()
        .connectorType(ConnectorType.JMS)
        .request(request)
        .timeout(5000)
        .build();
        
    connectorManager.sendSync(context);
}
```

### Kafka Connector
```java
public void publishToKafka(Map<String, Object> payload) {
    KafkaRequest request = new KafkaRequest();
    request.setTopic("demo.topic");
    request.setKey("demo-key-" + System.currentTimeMillis());
    request.setValue(payload);
    
    ConnectorContext context = ConnectorContext.builder()
        .connectorType(ConnectorType.KAFKA)
        .request(request)
        .build();
        
    connectorManager.sendAsyncNoResponse(context);
}
```

### GraphQL Connector
```java
public Object executeGraphQLQuery(String query, Map<String, Object> variables) {
    GraphQlRequest request = new GraphQlRequest();
    request.setEndpoint("https://api.example.com/graphql");
    request.setQuery(query);
    request.setVariables(variables);
    
    ConnectorContext context = ConnectorContext.builder()
        .connectorType(ConnectorType.GRAPHQL)
        .request(request)
        .timeout(10000)
        .build();
        
    return connectorManager.sendSync(context);
}
```

## Health Monitoring

### Health Check Endpoint
```bash
GET /actuator/health/connectors
```

Response:
```json
{
  "status": "UP",
  "components": {
    "rest": { "status": "UP" },
    "jms": { "status": "UP" },
    "kafka": { "status": "UP" },
    "graphql": { "status": "UP" }
  }
}
```

### Metrics
```bash
GET /actuator/metrics/connector.operation.duration
GET /actuator/metrics/connector.circuit.breaker.state
```

## Demo Controller

The module includes a demo controller (`ConnectorDemoController`) that showcases all connector types:

- `POST /api/connectors/rest/sync` - Synchronous REST call
- `POST /api/connectors/rest/async` - Asynchronous REST call  
- `POST /api/connectors/jms/send` - JMS messaging
- `POST /api/connectors/kafka/publish` - Kafka publishing
- `POST /api/connectors/graphql/query` - GraphQL queries
- `GET /api/connectors/health` - Health status
- `GET /api/connectors/metrics` - Connector metrics

## Integration Steps

1. **Add Module Dependency**
   ```gradle
   implementation project(':connector')
   ```

2. **Enable Auto-Configuration**
   The module uses Spring Boot auto-configuration. No manual configuration required.

3. **Configure Properties**
   Add connector configuration to `application.yml`

4. **Inject ConnectorManager**
   ```java
   @Autowired
   private ConnectorManager connectorManager;
   ```

5. **Use in VETRO Processors**
   Update VETRO configuration to use connector types for backend operations.

## Future Enhancements

- **Additional Protocols**: MQTT, gRPC, WebSocket support
- **Advanced Routing**: Content-based routing and load balancing
- **Security**: OAuth2, JWT token management
- **Caching**: Response caching with TTL
- **Monitoring**: Enhanced metrics and distributed tracing
- **Testing**: Connector-specific test containers and mocks

## Dependencies

- Spring Boot 3.2.12
- Resilience4j 2.1.0
- Spring WebFlux (REST)
- Spring JMS (JMS)
- Spring Kafka (Kafka)  
- Spring GraphQL (GraphQL)
- Pro-Spring-JMS Library (VETRO integration)

## Contributing

When contributing to the connector module:

1. Follow existing patterns for new connector types
2. Extend `AbstractResilientConnector` for resilience patterns
3. Implement health checking in `isHealthy()` method
4. Add comprehensive tests with TestContainers
5. Update configuration properties and documentation
6. Include usage examples in demo controller