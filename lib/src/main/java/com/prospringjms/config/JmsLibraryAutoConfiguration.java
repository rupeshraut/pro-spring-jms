package com.prospringjms.config;

import com.prospringjms.listener.JmsListenerRegistry;
import com.prospringjms.registry.JmsLibraryManager;
import com.prospringjms.resilience.Resilience4jManager;
import com.prospringjms.routing.DatacenterRouter;
import com.prospringjms.sender.ResilientJmsSender;
import com.ibm.mq.jms.MQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for the JMS Library providing multi-datacenter support.
 */
@Configuration
@EnableConfigurationProperties(JmsLibraryProperties.class)
public class JmsLibraryAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsLibraryAutoConfiguration.class);
    
    @Bean
    @Primary
    public JmsLibraryProperties jmsLibraryProperties() {
        return new JmsLibraryProperties();
    }
    
    @Bean
    public Map<String, ConnectionFactory> connectionFactories(JmsLibraryProperties properties) {
        Map<String, ConnectionFactory> factories = new HashMap<>();
        
        if (properties.getDatacenters() != null) {
            properties.getDatacenters().forEach((name, datacenter) -> {
                try {
                    ConnectionFactory factory = createConnectionFactory(datacenter);
                    factories.put(name, factory);
                    logger.info("Created connection factory for datacenter: {} ({})", name, datacenter.getType());
                } catch (Exception e) {
                    logger.error("Failed to create connection factory for datacenter: {}", name, e);
                }
            });
        }
        
        return factories;
    }
    
    @Bean("libJmsTemplates")
    public Map<String, JmsTemplate> libJmsTemplates(Map<String, ConnectionFactory> connectionFactories, 
                                                   MessageConverter messageConverter) {
        Map<String, JmsTemplate> templates = new HashMap<>();
        
        connectionFactories.forEach((name, factory) -> {
            JmsTemplate template = new JmsTemplate(factory);
            template.setMessageConverter(messageConverter);
            template.setReceiveTimeout(5000); // 5 second timeout
            template.setExplicitQosEnabled(true);
            templates.put(name, template);
            logger.debug("Created JMS template for datacenter: {}", name);
        });
        
        return templates;
    }
    
    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
    
    @Bean
    public DatacenterRouter datacenterRouter(JmsLibraryProperties properties) {
        return new DatacenterRouter(properties);
    }
    
    @Bean
    public ResilientJmsSender resilientJmsSender(JmsLibraryProperties properties,
                                                DatacenterRouter router,
                                                @Qualifier("libJmsTemplates") Map<String, JmsTemplate> jmsTemplates,
                                                Resilience4jManager resilienceManager) {
        return new ResilientJmsSender(properties, router, jmsTemplates, resilienceManager);
    }
    
    @Bean
    public JmsListenerRegistry jmsListenerRegistry(JmsLibraryProperties properties,
                                                  Map<String, ConnectionFactory> connectionFactories) {
        return new JmsListenerRegistry(properties, connectionFactories);
    }
    
    @Bean
    public JmsLibraryManager jmsLibraryManager(JmsLibraryProperties properties,
                                              DatacenterRouter router,
                                              ResilientJmsSender sender,
                                              JmsListenerRegistry listenerRegistry,
                                              Map<String, ConnectionFactory> connectionFactories,
                                              @Qualifier("libJmsTemplates") Map<String, JmsTemplate> jmsTemplates,
                                              Resilience4jManager resilienceManager) {
        return new JmsLibraryManager(properties, router, sender, listenerRegistry, 
                                   connectionFactories, jmsTemplates, resilienceManager);
    }
    
    private ConnectionFactory createConnectionFactory(JmsLibraryProperties.DataCenter datacenter) throws Exception {
        switch (datacenter.getType().toLowerCase()) {
            case "artemis":
                return createArtemisConnectionFactory(datacenter);
            case "ibmmq":
                return createIbmMqConnectionFactory(datacenter);
            default:
                throw new IllegalArgumentException("Unsupported datacenter type: " + datacenter.getType());
        }
    }
    
    private ConnectionFactory createArtemisConnectionFactory(JmsLibraryProperties.DataCenter datacenter) {
        String brokerUrl = String.format("tcp://%s:%d", datacenter.getHost(), datacenter.getPort());
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        
        if (datacenter.getUsername() != null) {
            factory.setUser(datacenter.getUsername());
        }
        if (datacenter.getPassword() != null) {
            factory.setPassword(datacenter.getPassword());
        }
        if (datacenter.getClientId() != null) {
            factory.setClientID(datacenter.getClientId());
        }
        
        // Configure connection pool if specified
        if (datacenter.getConnectionPool() != null) {
            JmsLibraryProperties.DataCenter.ConnectionPool poolConfig = datacenter.getConnectionPool();
            factory.setConnectionTTL(poolConfig.getIdleTimeout().longValue());
            factory.setClientFailureCheckPeriod(poolConfig.getConnectionTimeout().longValue());
        }
        
        return factory;
    }
    
    private ConnectionFactory createIbmMqConnectionFactory(JmsLibraryProperties.DataCenter datacenter) throws Exception {
        MQConnectionFactory factory = new MQConnectionFactory();
        
        factory.setHostName(datacenter.getHost());
        factory.setPort(datacenter.getPort());
        factory.setQueueManager(datacenter.getQueueManager());
        factory.setChannel(datacenter.getChannel());
        factory.setTransportType(1); // TCP/IP
        
        if (datacenter.getUsername() != null) {
            factory.setStringProperty("user", datacenter.getUsername());
        }
        if (datacenter.getPassword() != null) {
            factory.setStringProperty("password", datacenter.getPassword());
        }
        if (datacenter.getClientId() != null) {
            factory.setClientID(datacenter.getClientId());
        }
        
        return (ConnectionFactory) factory;
    }
}