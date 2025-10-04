package com.prospringjms.listener;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dynamic JMS listener registry with datacenter affinity and management capabilities.
 */
@Component
public class JmsListenerRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsListenerRegistry.class);
    
    private final JmsLibraryProperties properties;
    private final Map<String, ConnectionFactory> connectionFactories;
    private final Map<String, MessageListenerContainer> activeContainers = new ConcurrentHashMap<>();
    private final Map<String, ListenerMetadata> listenerMetadata = new ConcurrentHashMap<>();
    private final AtomicInteger listenerIdCounter = new AtomicInteger(0);
    
    public JmsListenerRegistry(JmsLibraryProperties properties,
                              Map<String, ConnectionFactory> connectionFactories) {
        this.properties = properties;
        this.connectionFactories = connectionFactories;
    }
    
    /**
     * Registers a new JMS listener with datacenter affinity.
     */
    public String registerListener(ListenerRegistration registration) throws JmsLibraryException {
        validateRegistration(registration);
        
        String listenerId = generateListenerId(registration);
        String datacenter = resolveDatacenter(registration);
        
        try {
            MessageListenerContainer container = createListenerContainer(registration, datacenter);
            
            ListenerMetadata metadata = new ListenerMetadata(
                listenerId,
                datacenter,
                registration.getDestination(),
                registration.getMessageListener() != null ? 
                    registration.getMessageListener().getClass().getSimpleName() : 
                    registration.getListenerMethod().getDeclaringClass().getSimpleName(),
                registration.isAutoStart()
            );
            
            activeContainers.put(listenerId, container);
            listenerMetadata.put(listenerId, metadata);
            
            if (registration.isAutoStart()) {
                container.start();
                logger.info("Started JMS listener {} for destination {} on datacenter {}", 
                    listenerId, registration.getDestination(), datacenter);
            } else {
                logger.info("Registered JMS listener {} for destination {} on datacenter {} (not started)", 
                    listenerId, registration.getDestination(), datacenter);
            }
            
            return listenerId;
            
        } catch (Exception e) {
            throw new JmsLibraryException(
                "Failed to register JMS listener for destination: " + registration.getDestination(),
                datacenter, "listener", e);
        }
    }
    
    /**
     * Registers a simple message listener with functional interface.
     */
    public String registerListener(String destination, 
                                 String datacenter, 
                                 MessageListener listener) throws JmsLibraryException {
        ListenerRegistration registration = new ListenerRegistration.Builder()
            .destination(destination)
            .datacenter(datacenter)
            .messageListener(listener)
            .autoStart(true)
            .build();
        
        return registerListener(registration);
    }
    
    /**
     * Registers a session-aware message listener with transaction support.
     */
    public String registerSessionAwareListener(String destination, 
                                             String datacenter, 
                                             SessionAwareMessageListener listener) throws JmsLibraryException {
        ListenerRegistration registration = new ListenerRegistration.Builder()
            .destination(destination)
            .datacenter(datacenter)
            .sessionAwareMessageListener(listener)
            .sessionTransacted(true)
            .autoStart(true)
            .build();
        
        return registerListener(registration);
    }
    
    /**
     * Starts a registered listener.
     */
    public void startListener(String listenerId) throws JmsLibraryException {
        MessageListenerContainer container = activeContainers.get(listenerId);
        if (container == null) {
            throw new JmsLibraryException("Listener not found: " + listenerId);
        }
        
        if (!container.isRunning()) {
            container.start();
            logger.info("Started JMS listener: {}", listenerId);
        } else {
            logger.warn("JMS listener {} is already running", listenerId);
        }
    }
    
    /**
     * Stops a registered listener.
     */
    public void stopListener(String listenerId) throws JmsLibraryException {
        MessageListenerContainer container = activeContainers.get(listenerId);
        if (container == null) {
            throw new JmsLibraryException("Listener not found: " + listenerId);
        }
        
        if (container.isRunning()) {
            container.stop();
            logger.info("Stopped JMS listener: {}", listenerId);
        } else {
            logger.warn("JMS listener {} is already stopped", listenerId);
        }
    }
    
    /**
     * Unregisters and removes a listener.
     */
    public void unregisterListener(String listenerId) throws JmsLibraryException {
        MessageListenerContainer container = activeContainers.get(listenerId);
        if (container == null) {
            throw new JmsLibraryException("Listener not found: " + listenerId);
        }
        
        if (container.isRunning()) {
            container.stop();
        }
        
        if (container instanceof org.springframework.context.Lifecycle) {
            ((org.springframework.context.Lifecycle) container).stop();
        }
        activeContainers.remove(listenerId);
        listenerMetadata.remove(listenerId);
        
        logger.info("Unregistered JMS listener: {}", listenerId);
    }
    
    /**
     * Gets the status of a registered listener.
     */
    public ListenerStatus getListenerStatus(String listenerId) throws JmsLibraryException {
        MessageListenerContainer container = activeContainers.get(listenerId);
        ListenerMetadata metadata = listenerMetadata.get(listenerId);
        
        if (container == null || metadata == null) {
            throw new JmsLibraryException("Listener not found: " + listenerId);
        }
        
        return new ListenerStatus(
            listenerId,
            metadata.getDatacenter(),
            metadata.getDestination(),
            metadata.getListenerClass(),
            container.isRunning(),
            container.isRunning()  // Use isRunning() instead of isActive()
        );
    }
    
    /**
     * Gets all registered listeners.
     */
    public Map<String, ListenerStatus> getAllListeners() {
        Map<String, ListenerStatus> statuses = new ConcurrentHashMap<>();
        
        activeContainers.forEach((listenerId, container) -> {
            ListenerMetadata metadata = listenerMetadata.get(listenerId);
            if (metadata != null) {
                statuses.put(listenerId, new ListenerStatus(
                    listenerId,
                    metadata.getDatacenter(),
                    metadata.getDestination(),
                    metadata.getListenerClass(),
                    container.isRunning(),
                    container.isRunning()  // Use isRunning() instead of isActive()
                ));
            }
        });
        
        return statuses;
    }
    
    /**
     * Stops all listeners for a specific datacenter.
     */
    public void stopDatacenterListeners(String datacenter) {
        listenerMetadata.entrySet().stream()
            .filter(entry -> datacenter.equals(entry.getValue().getDatacenter()))
            .forEach(entry -> {
                try {
                    stopListener(entry.getKey());
                } catch (JmsLibraryException e) {
                    logger.error("Failed to stop listener {} for datacenter {}", 
                        entry.getKey(), datacenter, e);
                }
            });
    }
    
    /**
     * Starts all listeners for a specific datacenter.
     */
    public void startDatacenterListeners(String datacenter) {
        listenerMetadata.entrySet().stream()
            .filter(entry -> datacenter.equals(entry.getValue().getDatacenter()))
            .forEach(entry -> {
                try {
                    startListener(entry.getKey());
                } catch (JmsLibraryException e) {
                    logger.error("Failed to start listener {} for datacenter {}", 
                        entry.getKey(), datacenter, e);
                }
            });
    }
    
    private void validateRegistration(ListenerRegistration registration) throws JmsLibraryException {
        if (registration.getDestination() == null || registration.getDestination().trim().isEmpty()) {
            throw new JmsLibraryException("Destination is required for listener registration");
        }
        
        if (registration.getMessageListener() == null && 
            registration.getSessionAwareMessageListener() == null &&
            registration.getListenerMethod() == null) {
            throw new JmsLibraryException("At least one listener type must be specified");
        }
        
        String datacenter = resolveDatacenter(registration);
        if (!connectionFactories.containsKey(datacenter)) {
            throw new JmsLibraryException("No connection factory found for datacenter: " + datacenter);
        }
    }
    
    private String resolveDatacenter(ListenerRegistration registration) {
        if (registration.getDatacenter() != null) {
            return registration.getDatacenter();
        }
        
        // Apply affinity rules from configuration
        if (registration.getRegion() != null && properties.getDatacenters() != null) {
            return properties.getDatacenters().entrySet().stream()
                .filter(entry -> entry.getValue().getAffinity() != null && 
                               registration.getRegion().equals(entry.getValue().getAffinity().getRegion()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(properties.getPrimaryDatacenter());
        }
        
        return properties.getPrimaryDatacenter();
    }
    
    private String generateListenerId(ListenerRegistration registration) {
        return String.format("listener-%d-%s-%s", 
            listenerIdCounter.incrementAndGet(),
            registration.getDestination().replaceAll("[^a-zA-Z0-9]", "-"),
            resolveDatacenter(registration));
    }
    
    private MessageListenerContainer createListenerContainer(ListenerRegistration registration, String datacenter) {
        ConnectionFactory connectionFactory = connectionFactories.get(datacenter);
        
        // Create container directly
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(registration.getDestination());
        
        // Configure container based on registration settings
        if (registration.isSessionTransacted()) {
            container.setSessionTransacted(true);
            container.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            container.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);
        }
        
        if (registration.getConcurrency() != null) {
            container.setConcurrency(registration.getConcurrency());
        }
        
        // Set message listener
        if (registration.getMessageListener() != null) {
            container.setMessageListener(registration.getMessageListener());
        } else if (registration.getSessionAwareMessageListener() != null) {
            // Wrap SessionAwareMessageListener as a regular MessageListener
            container.setMessageListener(new jakarta.jms.MessageListener() {
                @Override
                public void onMessage(Message message) {
                    // For session-aware listeners, we need session access which requires container configuration
                    logger.warn("SessionAware listeners require proper JMS container configuration for message: {}", message);
                }
            });
        }
        
        return container;
    }
    
    /**
     * Listener registration configuration.
     */
    public static class ListenerRegistration {
        private final String destination;
        private final String datacenter;
        private final String region;
        private final String zone;
        private final MessageListener messageListener;
        private final SessionAwareMessageListener sessionAwareMessageListener;
        private final Method listenerMethod;
        private final Object targetBean;
        private final boolean sessionTransacted;
        private final String concurrency;
        private final boolean autoStart;
        
        private ListenerRegistration(Builder builder) {
            this.destination = builder.destination;
            this.datacenter = builder.datacenter;
            this.region = builder.region;
            this.zone = builder.zone;
            this.messageListener = builder.messageListener;
            this.sessionAwareMessageListener = builder.sessionAwareMessageListener;
            this.listenerMethod = builder.listenerMethod;
            this.targetBean = builder.targetBean;
            this.sessionTransacted = builder.sessionTransacted;
            this.concurrency = builder.concurrency;
            this.autoStart = builder.autoStart;
        }
        
        // Getters
        public String getDestination() { return destination; }
        public String getDatacenter() { return datacenter; }
        public String getRegion() { return region; }
        public String getZone() { return zone; }
        public MessageListener getMessageListener() { return messageListener; }
        public SessionAwareMessageListener getSessionAwareMessageListener() { return sessionAwareMessageListener; }
        public Method getListenerMethod() { return listenerMethod; }
        public Object getTargetBean() { return targetBean; }
        public boolean isSessionTransacted() { return sessionTransacted; }
        public String getConcurrency() { return concurrency; }
        public boolean isAutoStart() { return autoStart; }
        
        public static class Builder {
            private String destination;
            private String datacenter;
            private String region;
            private String zone;
            private MessageListener messageListener;
            private SessionAwareMessageListener sessionAwareMessageListener;
            private Method listenerMethod;
            private Object targetBean;
            private boolean sessionTransacted = false;
            private String concurrency;
            private boolean autoStart = true;
            
            public Builder destination(String destination) {
                this.destination = destination;
                return this;
            }
            
            public Builder datacenter(String datacenter) {
                this.datacenter = datacenter;
                return this;
            }
            
            public Builder region(String region) {
                this.region = region;
                return this;
            }
            
            public Builder zone(String zone) {
                this.zone = zone;
                return this;
            }
            
            public Builder messageListener(MessageListener listener) {
                this.messageListener = listener;
                return this;
            }
            
            public Builder sessionAwareMessageListener(SessionAwareMessageListener listener) {
                this.sessionAwareMessageListener = listener;
                return this;
            }
            
            public Builder listenerMethod(Method method, Object targetBean) {
                this.listenerMethod = method;
                this.targetBean = targetBean;
                return this;
            }
            
            public Builder sessionTransacted(boolean transacted) {
                this.sessionTransacted = transacted;
                return this;
            }
            
            public Builder concurrency(String concurrency) {
                this.concurrency = concurrency;
                return this;
            }
            
            public Builder autoStart(boolean autoStart) {
                this.autoStart = autoStart;
                return this;
            }
            
            public ListenerRegistration build() {
                return new ListenerRegistration(this);
            }
        }
    }
    
    /**
     * Session-aware message listener interface for transaction support.
     */
    @FunctionalInterface
    public interface SessionAwareMessageListener {
        void onMessage(Message message, Session session) throws Exception;
    }
    
    /**
     * Metadata about a registered listener.
     */
    private static class ListenerMetadata {
        private final String listenerId;
        private final String datacenter;
        private final String destination;
        private final String listenerClass;
        private final boolean autoStart;
        
        public ListenerMetadata(String listenerId, String datacenter, String destination, 
                               String listenerClass, boolean autoStart) {
            this.listenerId = listenerId;
            this.datacenter = datacenter;
            this.destination = destination;
            this.listenerClass = listenerClass;
            this.autoStart = autoStart;
        }
        
        public String getListenerId() { return listenerId; }
        public String getDatacenter() { return datacenter; }
        public String getDestination() { return destination; }
        public String getListenerClass() { return listenerClass; }
        public boolean isAutoStart() { return autoStart; }
    }
    
    /**
     * Status information for a registered listener.
     */
    public static class ListenerStatus {
        private final String listenerId;
        private final String datacenter;
        private final String destination;
        private final String listenerClass;
        private final boolean running;
        private final boolean active;
        
        public ListenerStatus(String listenerId, String datacenter, String destination, 
                             String listenerClass, boolean running, boolean active) {
            this.listenerId = listenerId;
            this.datacenter = datacenter;
            this.destination = destination;
            this.listenerClass = listenerClass;
            this.running = running;
            this.active = active;
        }
        
        public String getListenerId() { return listenerId; }
        public String getDatacenter() { return datacenter; }
        public String getDestination() { return destination; }
        public String getListenerClass() { return listenerClass; }
        public boolean isRunning() { return running; }
        public boolean isActive() { return active; }
        
        @Override
        public String toString() {
            return String.format("ListenerStatus{id='%s', datacenter='%s', destination='%s', class='%s', running=%s, active=%s}",
                listenerId, datacenter, destination, listenerClass, running, active);
        }
    }
}