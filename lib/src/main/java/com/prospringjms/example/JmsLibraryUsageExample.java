package com.prospringjms.example;

import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.registry.JmsLibraryManager;
import com.prospringjms.sender.ResilientJmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating how to use the JMS Library for multi-datacenter messaging.
 */
@Component
@org.springframework.context.annotation.Profile("!test")
public class JmsLibraryUsageExample implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsLibraryUsageExample.class);
    
    private final JmsLibraryManager libraryManager;
    
    public JmsLibraryUsageExample(JmsLibraryManager libraryManager) {
        this.libraryManager = libraryManager;
    }
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting JMS Library usage example");
        
        // Example 1: Get library components
        demonstrateLibraryAccess();
        
        // Example 2: Send messages with various strategies
        demonstrateMessageSending();
        
        // Example 3: Register dynamic listeners
        demonstrateListenerRegistration();
        
        // Example 4: Monitor health and perform failover
        demonstrateHealthMonitoring();
        
        logger.info("JMS Library usage example completed");
    }
    
    private void demonstrateLibraryAccess() {
        logger.info("=== Library Access Example ===");
        
        // Get main components
        ResilientJmsSender sender = libraryManager.getSender();
        JmsListenerRegistry listenerRegistry = libraryManager.getListenerRegistry();
        
        // Get available datacenters
        List<String> datacenters = libraryManager.getAvailableDatacenters();
        logger.info("Available datacenters: {}", datacenters);
        
        // Get primary datacenter
        String primary = libraryManager.getPrimaryDatacenter();
        logger.info("Primary datacenter: {}", primary);
    }
    
    private void demonstrateMessageSending() throws Exception {
        logger.info("=== Message Sending Examples ===");
        
        ResilientJmsSender sender = libraryManager.getSender();
        
        // Example 1: Send to primary datacenter
        try {
            ResilientJmsSender.SendResult result = sender.sendToPrimary("test.queue", "Hello from primary!");
            logger.info("Primary send result: {}", result);
        } catch (Exception e) {
            logger.error("Failed to send to primary", e);
        }
        
        // Example 2: Send to specific datacenter
        try {
            List<String> datacenters = libraryManager.getAvailableDatacenters();
            if (!datacenters.isEmpty()) {
                ResilientJmsSender.SendResult result = sender.sendToDatacenter(
                    datacenters.get(0), "test.queue", "Hello from specific datacenter!");
                logger.info("Specific datacenter send result: {}", result);
            }
        } catch (Exception e) {
            logger.error("Failed to send to specific datacenter", e);
        }
        
        // Example 3: Send with headers
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("messageType", "test");
            headers.put("priority", 5);
            headers.put("correlationId", "12345");
            
            ResilientJmsSender.SendResult result = sender.sendToPrimary("test.queue", 
                "Hello with headers!", headers);
            logger.info("Send with headers result: {}", result);
        } catch (Exception e) {
            logger.error("Failed to send with headers", e);
        }
        
        // Example 4: Send with affinity (routing rules)
        try {
            ResilientJmsSender.SendRequest request = new ResilientJmsSender.SendRequest.Builder()
                .destination("test.queue")
                .message("Hello with affinity!")
                .region("us-east")
                .messageType("high-priority")
                .build();
            
            ResilientJmsSender.SendResult result = sender.sendWithAffinity(request);
            logger.info("Send with affinity result: {}", result);
        } catch (Exception e) {
            logger.error("Failed to send with affinity", e);
        }
        
        // Example 5: Broadcast to all datacenters
        try {
            List<ResilientJmsSender.SendResult> results = sender.broadcast("test.queue", 
                "Hello to all datacenters!");
            logger.info("Broadcast results: {}", results);
        } catch (Exception e) {
            logger.error("Failed to broadcast", e);
        }
    }
    
    private void demonstrateListenerRegistration() throws Exception {
        logger.info("=== Listener Registration Examples ===");
        
        JmsListenerRegistry registry = libraryManager.getListenerRegistry();
        
        // Example 1: Simple message listener
        String listenerId1 = registry.registerListener("test.queue", 
            libraryManager.getPrimaryDatacenter(), 
            message -> {
                try {
                    if (message instanceof TextMessage) {
                        String text = ((TextMessage) message).getText();
                        logger.info("Received message: {}", text);
                    }
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                }
            });
        logger.info("Registered simple listener: {}", listenerId1);
        
        // Example 2: Session-aware listener with transaction support
        String listenerId2 = registry.registerSessionAwareListener("test.transacted.queue",
            libraryManager.getPrimaryDatacenter(),
            (message, session) -> {
                try {
                    if (message instanceof TextMessage) {
                        String text = ((TextMessage) message).getText();
                        logger.info("Processing transacted message: {}", text);
                        
                        // Simulate business logic
                        if (text.contains("error")) {
                            logger.warn("Rolling back transaction for message: {}", text);
                            session.rollback();
                        } else {
                            logger.info("Committing transaction for message: {}", text);
                            session.commit();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing transacted message", e);
                    try {
                        session.rollback();
                    } catch (Exception rollbackError) {
                        logger.error("Error rolling back transaction", rollbackError);
                    }
                }
            });
        logger.info("Registered session-aware listener: {}", listenerId2);
        
        // Example 3: Complex listener registration with custom configuration
        JmsListenerRegistry.ListenerRegistration registration = 
            new JmsListenerRegistry.ListenerRegistration.Builder()
                .destination("test.complex.queue")
                .region("us-west")  // Will be routed to appropriate datacenter
                .sessionTransacted(true)
                .concurrency("3-10")  // Min 3, max 10 concurrent consumers
                .autoStart(false)     // Don't start immediately
                .messageListener(message -> {
                    logger.info("Complex listener received: {}", message);
                })
                .build();
        
        String listenerId3 = registry.registerListener(registration);
        logger.info("Registered complex listener: {}", listenerId3);
        
        // Start the complex listener manually
        registry.startListener(listenerId3);
        logger.info("Started complex listener: {}", listenerId3);
        
        // Get status of all listeners
        Map<String, JmsListenerRegistry.ListenerStatus> allListeners = registry.getAllListeners();
        allListeners.forEach((id, status) -> 
            logger.info("Listener status: {}", status));
    }
    
    private void demonstrateHealthMonitoring() {
        logger.info("=== Health Monitoring Examples ===");
        
        // Get overall health status
        JmsLibraryManager.JmsLibraryHealth health = libraryManager.getHealthStatus();
        logger.info("Overall library health: {}", health);
        
        // Get Spring Boot Actuator health
        org.springframework.boot.actuate.health.Health actuatorHealth = libraryManager.health();
        logger.info("Actuator health status: {}", actuatorHealth.getStatus());
        logger.info("Actuator health details: {}", actuatorHealth.getDetails());
        
        // Example: Simulate failover scenario
        List<String> datacenters = libraryManager.getAvailableDatacenters();
        if (datacenters.size() >= 2) {
            try {
                String fromDc = datacenters.get(0);
                String toDc = datacenters.get(1);
                
                logger.info("Simulating failover from {} to {}", fromDc, toDc);
                libraryManager.performFailover(fromDc, toDc);
                
                // Check health after failover
                JmsLibraryManager.JmsLibraryHealth healthAfterFailover = libraryManager.getHealthStatus();
                logger.info("Health after failover: {}", healthAfterFailover);
                
            } catch (Exception e) {
                logger.error("Failover simulation failed", e);
            }
        }
    }
}