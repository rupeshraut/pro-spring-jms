package com.prospringjms.messaging.service;

import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.messaging.VetroMessageProcessor;
import com.prospringjms.messaging.context.SessionAwareProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.JMSException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced JMS integration service with session awareness and retry support.
 * This service properly manages JMS sessions and integrates VETRO processors
 * with transactional message processing capabilities.
 */
@Service
public class SessionAwareVetroJmsIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionAwareVetroJmsIntegrationService.class);
    
    @Autowired
    private JmsListenerRegistry jmsListenerRegistry;
    
    private final Map<String, VetroMessageProcessor> registeredProcessors = new ConcurrentHashMap<>();
    private final Map<String, String> listenerIds = new ConcurrentHashMap<>();
    
    /**
     * Registers a VETRO processor for session-aware message processing.
     * 
     * @param destination JMS destination to listen on
     * @param processor VETRO processor to handle messages
     * @param datacenter Datacenter preference for the listener
     * @param sessionTransacted Whether the session should be transacted
     * @return Registration ID for later reference
     */
    public String registerSessionAwareProcessor(String destination, 
                                              VetroMessageProcessor processor, 
                                              String datacenter, 
                                              boolean sessionTransacted) {
        return registerSessionAwareProcessor(destination, processor, datacenter, sessionTransacted, 1);
    }
    
    /**
     * Registers a VETRO processor with concurrency control.
     * 
     * @param destination JMS destination to listen on
     * @param processor VETRO processor to handle messages
     * @param datacenter Datacenter preference for the listener
     * @param sessionTransacted Whether the session should be transacted
     * @param concurrency Number of concurrent listeners
     * @return Registration ID for later reference
     */
    public String registerSessionAwareProcessor(String destination, 
                                              VetroMessageProcessor processor, 
                                              String datacenter, 
                                              boolean sessionTransacted,
                                              int concurrency) {
        
        String registrationId = destination + "-" + datacenter + "-" + System.currentTimeMillis();
        
        logger.info("Registering session-aware VETRO processor for destination: {}, datacenter: {}, transacted: {}, concurrency: {}", 
            destination, datacenter, sessionTransacted, concurrency);
        
        // Create session-aware message listener
        SessionAwareMessageListener sessionListener = new SessionAwareMessageListener(processor, destination);
        
        try {
            // Register with JMS listener registry
            String listenerId = jmsListenerRegistry.registerListener(
                destination, 
                sessionListener, 
                datacenter, 
                sessionTransacted,
                concurrency
            );
            
            // Store references for management
            registeredProcessors.put(registrationId, processor);
            listenerIds.put(registrationId, listenerId);
            
            logger.info("Successfully registered session-aware processor: {} with listener ID: {}", 
                registrationId, listenerId);
            
            return registrationId;
            
        } catch (Exception e) {
            logger.error("Failed to register session-aware processor for destination: {}", destination, e);
            throw new RuntimeException("Failed to register session-aware processor", e);
        }
    }
    
    /**
     * Registers multiple processors with different configurations.
     */
    public Map<String, String> registerMultipleProcessors(Map<String, ProcessorConfig> configs) {
        Map<String, String> registrationIds = new HashMap<>();
        
        for (Map.Entry<String, ProcessorConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            ProcessorConfig config = entry.getValue();
            
            try {
                String registrationId = registerSessionAwareProcessor(
                    config.getDestination(),
                    config.getProcessor(),
                    config.getDatacenter(),
                    config.isSessionTransacted(),
                    config.getConcurrency()
                );
                
                registrationIds.put(name, registrationId);
                logger.info("Registered processor '{}' with registration ID: {}", name, registrationId);
                
            } catch (Exception e) {
                logger.error("Failed to register processor '{}' for destination: {}", 
                    name, config.getDestination(), e);
            }
        }
        
        return registrationIds;
    }
    
    /**
     * Unregisters a processor and stops its listener.
     */
    public boolean unregisterProcessor(String registrationId) {
        try {
            String listenerId = listenerIds.get(registrationId);
            if (listenerId != null) {
                jmsListenerRegistry.unregisterListener(listenerId);
                listenerIds.remove(registrationId);
            }
            
            VetroMessageProcessor processor = registeredProcessors.remove(registrationId);
            if (processor != null) {
                processor.shutdown(); // Cleanup retry executor
            }
            
            logger.info("Successfully unregistered processor: {}", registrationId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to unregister processor: {}", registrationId, e);
            return false;
        }
    }
    
    /**
     * Starts all registered processors.
     */
    public void startAll() {
        logger.info("Starting all registered session-aware VETRO processors ({} total)", 
            registeredProcessors.size());
        
        try {
            jmsListenerRegistry.startAllListeners();
            logger.info("All session-aware VETRO processors started successfully");
        } catch (Exception e) {
            logger.error("Failed to start all processors", e);
            throw new RuntimeException("Failed to start processors", e);
        }
    }
    
    /**
     * Stops all registered processors.
     */
    public void stopAll() {
        logger.info("Stopping all session-aware VETRO processors");
        
        try {
            jmsListenerRegistry.stopAllListeners();
            
            // Shutdown individual processors
            for (VetroMessageProcessor processor : registeredProcessors.values()) {
                try {
                    processor.shutdown();
                } catch (Exception e) {
                    logger.warn("Error shutting down processor", e);
                }
            }
            
            logger.info("All session-aware VETRO processors stopped");
        } catch (Exception e) {
            logger.error("Failed to stop all processors", e);
        }
    }
    
    /**
     * Gets status information for all registered processors.
     */
    public Map<String, ProcessorStatus> getProcessorStatuses() {
        Map<String, ProcessorStatus> statuses = new HashMap<>();
        
        for (Map.Entry<String, VetroMessageProcessor> entry : registeredProcessors.entrySet()) {
            String registrationId = entry.getKey();
            String listenerId = listenerIds.get(registrationId);
            
            ProcessorStatus status = new ProcessorStatus();
            status.setRegistrationId(registrationId);
            status.setListenerId(listenerId);
            status.setProcessorClass(entry.getValue().getClass().getSimpleName());
            status.setActive(listenerId != null);
            
            statuses.put(registrationId, status);
        }
        
        return statuses;
    }
    
    /**
     * Session-aware message listener that properly handles JMS sessions.
     */
    private static class SessionAwareMessageListener implements MessageListener {
        
        private final VetroMessageProcessor processor;
        private final String destination;
        private final Logger logger = LoggerFactory.getLogger(SessionAwareMessageListener.class);
        
        public SessionAwareMessageListener(VetroMessageProcessor processor, String destination) {
            this.processor = processor;
            this.destination = destination;
        }
        
        @Override
        public void onMessage(Message message) {
            String correlationId = null;
            Session session = null;
            
            try {
                // Extract correlation ID for logging
                correlationId = message.getJMSCorrelationID();
                if (correlationId == null) {
                    correlationId = message.getJMSMessageID();
                }
                
                logger.debug("Received message on destination: {}, correlationId: {}", destination, correlationId);
                
                // Get session from message (implementation specific)
                session = getSessionFromMessage(message);
                
                // Extract message payload
                Object payload = extractPayload(message);
                
                // Process message with session awareness
                processor.processMessage(message, session, destination);
                
                logger.debug("Successfully processed message: {}", correlationId);
                
            } catch (Exception e) {
                logger.error("Error processing message: {} on destination: {}", correlationId, destination, e);
                
                // Let the VETRO processor handle the failure and session management
                // The processor will handle retry logic and session rollback as needed
            }
        }
        
        private Session getSessionFromMessage(Message message) {
            // This is a placeholder - in real implementation, you'd need to
            // access the session through container-specific mechanisms
            // or by using SessionAwareMessageListener from Spring JMS
            try {
                return message.getSession();
            } catch (Exception e) {
                logger.debug("Could not extract session from message", e);
                return null;
            }
        }
        
        private Object extractPayload(Message message) throws JMSException {
            if (message instanceof TextMessage) {
                return ((TextMessage) message).getText();
            }
            // Add other message type handling as needed
            return message;
        }
    }
    
    /**
     * Configuration class for processor registration.
     */
    public static class ProcessorConfig {
        private String destination;
        private VetroMessageProcessor processor;
        private String datacenter = "primary";
        private boolean sessionTransacted = true;
        private int concurrency = 1;
        
        // Getters and setters
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
        public VetroMessageProcessor getProcessor() { return processor; }
        public void setProcessor(VetroMessageProcessor processor) { this.processor = processor; }
        public String getDatacenter() { return datacenter; }
        public void setDatacenter(String datacenter) { this.datacenter = datacenter; }
        public boolean isSessionTransacted() { return sessionTransacted; }
        public void setSessionTransacted(boolean sessionTransacted) { this.sessionTransacted = sessionTransacted; }
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    }
    
    /**
     * Status information for registered processors.
     */
    public static class ProcessorStatus {
        private String registrationId;
        private String listenerId;
        private String processorClass;
        private boolean active;
        
        // Getters and setters
        public String getRegistrationId() { return registrationId; }
        public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
        public String getListenerId() { return listenerId; }
        public void setListenerId(String listenerId) { this.listenerId = listenerId; }
        public String getProcessorClass() { return processorClass; }
        public void setProcessorClass(String processorClass) { this.processorClass = processorClass; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}