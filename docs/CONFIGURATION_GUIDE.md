# YAML Configuration Guide

Complete configuration reference for the Pro Spring JMS Multi-Datacenter Library.

## 📋 Table of Contents

- [Basic Configuration](#basic-configuration)
- [Datacenter Configuration](#datacenter-configuration)
- [Connection Pools](#connection-pools)
- [Resilience Patterns](#resilience-patterns)
- [Security Configuration](#security-configuration)
- [Monitoring & Health Checks](#monitoring--health-checks)
- [Load Balancing & Failover](#load-balancing--failover)
- [Advanced Configuration](#advanced-configuration)
- [Configuration Examples](#configuration-examples)

## 🚀 Basic Configuration

### Minimal Configuration

```yaml
# application.yml
jms:
  default-datacenter: primary
  datacenters:
    primary:
      type: artemis
      host: localhost
      port: 61616
      username: admin
      password: admin
```

### Application Properties

```yaml
spring:
  application:
    name: my-jms-app
  jms:
    template:
      default-destination: orders.queue
      
info:
  app:
    name: My JMS Application
    description: Multi-datacenter messaging application
    version: 1.0.0
```

## 🏢 Datacenter Configuration

### ActiveMQ Artemis Configuration

```yaml
jms:
  datacenters:
    dc1:
      type: artemis
      host: artemis-dc1.example.com
      port: 61616
      username: ${ARTEMIS_USERNAME:admin}
      password: ${ARTEMIS_PASSWORD:admin}
      client-id: my-app-dc1
      
      # SSL Configuration
      ssl:
        enabled: true
        keystore: classpath:keystore.jks
        keystore-password: ${KEYSTORE_PASSWORD}
        truststore: classpath:truststore.jks
        truststore-password: ${TRUSTSTORE_PASSWORD}
        
      # Queue and Topic Mappings
      queues:
        orders: orders.queue
        notifications: notifications.queue
        events: events.queue
        dlq: dlq.queue
        
      topics:
        broadcast: broadcast.topic
        alerts: alerts.topic
```

### IBM MQ Configuration

```yaml
jms:
  datacenters:
    dc2:
      type: ibmmq
      host: ibmmq-dc2.example.com
      port: 1414
      queue-manager: QM_DC2
      channel: SYSTEM.DEF.SVRCONN
      username: ${IBMMQ_USERNAME:mqadmin}
      password: ${IBMMQ_PASSWORD:mqpassword}
      client-id: my-app-dc2
      
      # IBM MQ Specific Settings
      ibmmq:
        ccsid: 1208
        encoding: 273
        use-connection-pooling: true
        
      # Queue Mappings
      queues:
        orders: DEV.QUEUE.ORDERS
        notifications: DEV.QUEUE.NOTIFICATIONS  
        events: DEV.QUEUE.EVENTS
        dlq: DEV.DEAD.LETTER.QUEUE
        
      topics:
        broadcast: DEV.TOPIC.BROADCAST
        alerts: DEV.TOPIC.ALERTS
```

## 🔗 Connection Pools

### Basic Pool Configuration

```yaml
jms:
  datacenters:
    dc1:
      connection-pool:
        max-connections: 20
        min-connections: 2
        idle-timeout: 300000  # 5 minutes in ms
        max-sessions-per-connection: 10
        validation-query-timeout: 5000
        
        # Pool Health
        test-on-borrow: true
        test-on-return: false
        test-while-idle: true
        validation-interval: 30000
```

### Advanced Pool Configuration

```yaml
jms:
  datacenters:
    production:
      connection-pool:
        max-connections: 50
        min-connections: 5
        initial-size: 10
        
        # Timeouts
        idle-timeout: 600000        # 10 minutes
        max-wait-time: 30000       # 30 seconds
        connection-timeout: 10000   # 10 seconds
        
        # Eviction Policy
        eviction-policy: LIFO
        eviction-run-interval: 300000  # 5 minutes
        min-evictable-idle-time: 900000  # 15 minutes
        
        # Validation
        validation-query: "SELECT 1"
        test-on-create: true
        test-on-borrow: true
        test-on-return: false
        test-while-idle: true
        
        # JMX Monitoring
        jmx-enabled: true
        jmx-name: "jms.pool.${datacenter}"
```

## 🛡️ Resilience Patterns

### Circuit Breaker Configuration

```yaml
jms:
  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50.0        # 50% failure rate
      slow-call-rate-threshold: 50.0      # 50% slow call rate
      slow-call-duration-threshold: 10s   # Calls slower than 10s
      
      # State Transitions
      wait-duration-in-open-state: 60s
      permitted-calls-in-half-open: 5
      minimum-number-of-calls: 10
      sliding-window-size: 100
      sliding-window-type: COUNT_BASED     # or TIME_BASED
      
      # Exceptions
      record-exceptions:
        - javax.jms.JMSException
        - java.net.ConnectException
        - java.net.SocketTimeoutException
      ignore-exceptions:
        - com.prospringjms.exception.ValidationException
```

### Retry Configuration

```yaml
jms:
  resilience:
    retry:
      enabled: true
      max-attempts: 3
      wait-duration: 1s
      
      # Exponential Backoff
      exponential-backoff-multiplier: 2.0
      max-wait-duration: 10s
      
      # Retry Conditions
      retry-exceptions:
        - javax.jms.JMSException
        - java.net.ConnectException
        - com.prospringjms.exception.TransientException
      
      # Predicate (custom retry logic)
      retry-on-result-predicate: "result -> result == null"
```

### Rate Limiter Configuration

```yaml
jms:
  resilience:
    rate-limiter:
      enabled: true
      limit-for-period: 1000      # 1000 calls
      limit-refresh-period: 1s    # per second
      timeout-duration: 5s        # wait up to 5s for permit
      
      # Different limits per datacenter
      per-datacenter-limits:
        dc1: 500
        dc2: 300
        dc3: 200
```

### Bulkhead Configuration

```yaml
jms:
  resilience:
    bulkhead:
      enabled: true
      max-concurrent-calls: 25
      max-wait-duration: 10s
      
      # Thread Pool Bulkhead (alternative)
      type: THREAD_POOL
      core-thread-pool-size: 10
      max-thread-pool-size: 20
      queue-capacity: 100
      keep-alive-duration: 60s
```

### Time Limiter Configuration

```yaml
jms:
  resilience:
    time-limiter:
      enabled: true
      timeout-duration: 30s
      cancel-running-future: true
```

## 🔐 Security Configuration

### Encryption Settings

```yaml
jms:
  security:
    enabled: true
    
    encryption:
      algorithm: AES-256-GCM
      key-derivation: PBKDF2
      key-length: 256
      
      # Key Management
      key-rotation-interval: 24h
      key-store-path: classpath:keys/encryption-keys.jks
      key-store-password: ${ENCRYPTION_KEY_PASSWORD}
      key-alias: jms-encryption-key
      
      # Performance
      buffer-size: 8192
      use-compression: true
```

### Audit Configuration

```yaml
jms:
  security:
    audit:
      enabled: true
      include-message-content: false  # For PII compliance
      include-headers: true
      include-properties: true
      
      # Audit Storage
      storage-type: FILE  # FILE, DATABASE, or ELASTIC
      file-path: ${user.home}/logs/jms-audit.log
      max-file-size: 100MB
      max-history: 30
      
      # Filtering
      exclude-destinations:
        - heartbeat.queue
        - health.check.queue
      
      # Sensitive Data
      mask-fields:
        - password
        - ssn
        - credit-card
```

### Certificate Configuration

```yaml
jms:
  security:
    certificate:
      enabled: true
      
      # Client Certificate
      keystore:
        path: classpath:client-keystore.jks
        password: ${CLIENT_KEYSTORE_PASSWORD}
        type: JKS
        
      # Trusted Certificates  
      truststore:
        path: classpath:truststore.jks
        password: ${TRUSTSTORE_PASSWORD}
        type: JKS
        
      # Certificate Validation
      validate-certificate-chain: true
      check-certificate-revocation: true
      hostname-verification: true
```

## 📊 Monitoring & Health Checks

### Health Check Configuration

```yaml
jms:
  health:
    enabled: true
    interval: 30s
    timeout: 5s
    failure-threshold: 3
    recovery-threshold: 2
    
    # Health Check Types
    checks:
      - type: CONNECTION    # Test connection
      - type: QUEUE_DEPTH  # Monitor queue depth
        threshold: 10000
      - type: RESPONSE_TIME # Monitor response time
        threshold: 1000ms
      - type: CUSTOM       # Custom health check
        class: com.mycompany.CustomHealthCheck
```

### Metrics Configuration

```yaml
jms:
  monitoring:
    enabled: true
    
    # JMX Settings
    jmx:
      enabled: true
      domain: com.prospringjms
      port: 9999
      
    # Metrics Export
    metrics:
      enabled: true
      export-interval: 60s
      include-datacenters: true
      include-queues: true
      include-topics: true
      include-connection-pools: true
      
      # Micrometer Integration
      micrometer:
        enabled: true
        step: 1m
        
    # Prometheus Integration
    prometheus:
      enabled: true
      path: /actuator/prometheus
      
    # Custom Metrics
    custom-metrics:
      - name: business.orders.processed
        type: COUNTER
      - name: business.processing.time
        type: TIMER
```

## ⚖️ Load Balancing & Failover

### Load Balancing Configuration

```yaml
jms:
  load-balancing:
    enabled: true
    strategy: WEIGHTED_ROUND_ROBIN  # ROUND_ROBIN, RANDOM, LEAST_CONNECTIONS
    
    # Datacenter Weights
    weights:
      primary: 70
      secondary: 20  
      backup: 10
      
    # Health-based Adjustment
    health-check-weight-adjustment: true
    unhealthy-weight-reduction: 0.5
    
    # Sticky Sessions (for stateful operations)
    sticky-sessions:
      enabled: true
      session-timeout: 3600s  # 1 hour
      session-key-header: X-Session-ID
```

### Failover Configuration

```yaml
jms:
  failover:
    enabled: true
    
    # Failover Strategy
    strategy: PRIORITY_BASED  # PRIORITY_BASED, ROUND_ROBIN, GEOGRAPHIC
    max-failover-attempts: 3
    failover-delay: 5s
    
    # Cross-Region Failover
    cross-region-enabled: true
    exclude-unhealthy-datacenters: true
    
    # Datacenter Priorities
    priorities:
      primary: 1
      secondary: 2
      dr: 3
      
    # Geographic Preferences
    geographic-routing:
      prefer-local-region: true
      region-mapping:
        us-east: [primary, secondary]
        us-west: [backup]
        europe: [eu-primary]
```

## ⚙️ Advanced Configuration

### Transaction Management

```yaml
jms:
  transaction:
    enabled: true
    timeout: 30s
    propagation: REQUIRED  # REQUIRED, REQUIRES_NEW, SUPPORTS, etc.
    
    # XA Transactions
    xa-enabled: false
    xa-timeout: 300s
    
    # Transaction Manager
    transaction-manager-ref: transactionManager
```

### Message Routing

```yaml
jms:
  routing:
    # Content-Based Routing
    content-based:
      enabled: true
      rules:
        - condition: "messageType == 'ORDER'"
          datacenter: primary
        - condition: "priority == 'HIGH'"
          datacenter: primary
        - condition: "region == 'EU'"
          datacenter: eu-datacenter
          
    # Header-Based Routing  
    header-based:
      enabled: true
      routing-header: X-Datacenter-Preference
      
    # Geographic Routing
    geographic:
      enabled: true
      ip-geolocation-service: maxmind
      fallback-datacenter: primary
```

### Performance Tuning

```yaml
jms:
  performance:
    # Message Batching
    batch-processing:
      enabled: true
      batch-size: 100
      batch-timeout: 5s
      
    # Compression
    compression:
      enabled: true
      algorithm: GZIP
      min-message-size: 1024  # Only compress messages > 1KB
      
    # Caching
    cache:
      destinations: true
      message-producers: true
      message-consumers: false
      cache-size: 1000
      ttl: 3600s
      
    # Async Processing
    async:
      enabled: true
      thread-pool-size: 20
      queue-capacity: 1000
```

## 📝 Configuration Examples

### Development Environment

```yaml
# application-dev.yml
server:
  port: 8080

jms:
  default-datacenter: local
  datacenters:
    local:
      type: artemis
      host: localhost
      port: 61616
      username: admin
      password: admin
      queues:
        orders: dev.orders.queue
        notifications: dev.notifications.queue
        
  # Simplified Resilience for Dev
  resilience:
    circuit-breaker:
      enabled: false
    retry:
      enabled: true
      max-attempts: 2
      
  # Detailed Monitoring in Dev
  monitoring:
    enabled: true
    jmx:
      enabled: true
      
logging:
  level:
    '[com.prospringjms]': DEBUG
    '[org.apache.activemq]': INFO
```

### Production Environment

```yaml
# application-prod.yml
server:
  port: 8080

jms:
  default-datacenter: prod-primary
  
  datacenters:
    prod-primary:
      type: artemis
      host: ${ARTEMIS_PRIMARY_HOST}
      port: 61617  # SSL port
      username: ${ARTEMIS_USERNAME}
      password: ${ARTEMIS_PASSWORD}
      ssl:
        enabled: true
        keystore: file:/opt/app/certs/keystore.jks
        keystore-password: ${KEYSTORE_PASSWORD}
      connection-pool:
        max-connections: 50
        min-connections: 10
        
    prod-secondary:
      type: artemis
      host: ${ARTEMIS_SECONDARY_HOST}
      port: 61617
      username: ${ARTEMIS_USERNAME}
      password: ${ARTEMIS_PASSWORD}
      ssl:
        enabled: true
      connection-pool:
        max-connections: 30
        min-connections: 5
        
    dr-backup:
      type: ibmmq
      host: ${IBMMQ_DR_HOST}
      port: 1414
      queue-manager: ${IBMMQ_QM}
      channel: ${IBMMQ_CHANNEL}
      
  # Production Resilience
  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 30.0
      wait-duration-in-open-state: 120s
    retry:
      enabled: true
      max-attempts: 5
      exponential-backoff-multiplier: 2.0
    rate-limiter:
      enabled: true
      limit-for-period: 10000
    bulkhead:
      enabled: true
      max-concurrent-calls: 100
      
  # Production Security
  security:
    enabled: true
    encryption:
      enabled: true
      algorithm: AES-256-GCM
    audit:
      enabled: true
      include-message-content: false
      
  # Production Monitoring
  monitoring:
    enabled: true
    metrics:
      export-interval: 30s
    prometheus:
      enabled: true
      
  # Production Load Balancing
  load-balancing:
    enabled: true
    strategy: WEIGHTED_ROUND_ROBIN
    weights:
      prod-primary: 80
      prod-secondary: 20
      
  failover:
    enabled: true
    max-failover-attempts: 3
    cross-region-enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jms-health
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: INFO
    '[com.prospringjms]': INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Multi-Region Setup

```yaml
# application-multi-region.yml
jms:
  default-datacenter: us-east-primary
  
  datacenters:
    # US East Region
    us-east-primary:
      type: artemis
      host: artemis-use1.example.com
      port: 61617
      region: us-east
      zone: us-east-1a
      
    us-east-secondary:
      type: artemis  
      host: artemis-use2.example.com
      port: 61617
      region: us-east
      zone: us-east-1b
      
    # US West Region
    us-west-primary:
      type: artemis
      host: artemis-usw1.example.com
      port: 61617
      region: us-west
      zone: us-west-1a
      
    # Europe Region
    eu-primary:
      type: artemis
      host: artemis-eu1.example.com
      port: 61617
      region: europe
      zone: eu-west-1a
      
  # Geographic Routing
  routing:
    geographic:
      enabled: true
      rules:
        - source-region: us-east
          preferred-datacenters: [us-east-primary, us-east-secondary]
          fallback-datacenters: [us-west-primary]
        - source-region: us-west
          preferred-datacenters: [us-west-primary]
          fallback-datacenters: [us-east-primary]
        - source-region: europe
          preferred-datacenters: [eu-primary]
          fallback-datacenters: [us-east-primary]
          
  # Multi-Region Load Balancing
  load-balancing:
    enabled: true
    strategy: GEOGRAPHIC_WEIGHTED
    cross-region-latency-threshold: 100ms
    
  failover:
    enabled: true
    cross-region-enabled: true
    region-failover-delay: 10s
```

## 🔍 Configuration Validation

### Schema Validation

The library provides JSON Schema validation for configuration:

```yaml
jms:
  validation:
    enabled: true
    schema-location: classpath:jms-config-schema.json
    fail-on-validation-error: true
    
  # Environment-specific validation
  environment-checks:
    - name: production-ssl-required
      condition: "env == 'production'"
      require: "datacenters.*.ssl.enabled == true"
    - name: connection-pool-sizing
      condition: "env == 'production'"
      require: "datacenters.*.connection-pool.max-connections >= 10"
```

## 🛠️ Configuration Best Practices

### 1. Environment-Specific Configuration

```yaml
# Use Spring profiles
spring:
  profiles:
    active: ${ENVIRONMENT:dev}

---
# Development Profile
spring:
  config:
    activate:
      on-profile: dev
jms:
  # Development-specific settings
  
---
# Production Profile  
spring:
  config:
    activate:
      on-profile: prod
jms:
  # Production-specific settings
```

### 2. External Configuration

```yaml
# Use external config sources
spring:
  config:
    import:
      - classpath:jms-config.yml
      - optional:file:./config/jms-override.yml
      - vault://secret/jms-config
      - consul:jms/config
```

### 3. Configuration Encryption

```yaml
# Use encrypted values with Spring Cloud Config
jms:
  datacenters:
    secure:
      password: '{cipher}AQA...'  # Encrypted password
      keystore-password: '{cipher}BQB...'
```

### 4. Configuration Monitoring

```yaml
jms:
  config:
    # Monitor configuration changes
    refresh:
      enabled: true
      interval: 300s  # Check every 5 minutes
      
    # Configuration actuator endpoints
    management:
      expose-config: true
      mask-sensitive-properties: true
```

## 📚 Related Documentation

- **[User Guide](USER_GUIDE.md)** - Complete installation and usage guide
- **[Quick Reference](QUICK_REFERENCE.md)** - Developer quick reference  
- **[Feature Comparison](FEATURE_COMPARISON.md)** - Comparison vs Standard Spring JMS
- **[Production Readiness](PRODUCTION_READINESS_FINAL.md)** - Production deployment guide

---

**Pro Spring JMS Multi-Datacenter Library v1.0.0** - [GitHub Repository](https://github.com/rupeshraut/pro-spring-jms)
