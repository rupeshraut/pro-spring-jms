package com.prospringjms.connector.config;

import com.prospringjms.connector.manager.ConnectorManager;
import com.prospringjms.connector.rest.RestConnector;
import com.prospringjms.connector.jms.JmsConnector;
import com.prospringjms.connector.kafka.KafkaConnector;
import com.prospringjms.connector.graphql.GraphQlConnector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration for backend connector components.
 * 
 * Enables automatic configuration of REST, JMS, Kafka, and GraphQL connectors
 * with sensible defaults and optional customization through properties.
 */
@AutoConfiguration
@EnableConfigurationProperties(ConnectorProperties.class)
@ComponentScan(basePackages = "com.prospringjms.connector")
public class ConnectorAutoConfiguration {
    
    /**
     * Configure WebClient for REST connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebClient restWebClient(ConnectorProperties properties) {
        ConnectorProperties.RestConfig restConfig = properties.getRest();
        
        return WebClient.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(restConfig.getMaxInMemorySize());
            })
            .build();
    }
    
    /**
     * Configure REST connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestConnector restConnector(WebClient restWebClient) {
        return new RestConnector(restWebClient);
    }
    
    /**
     * Configure JMS connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.jms", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JmsConnector jmsConnector() {
        return new JmsConnector();
    }
    
    /**
     * Configure Kafka connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaConnector kafkaConnector(KafkaTemplate<String, Object> kafkaTemplate) {
        return new KafkaConnector(kafkaTemplate);
    }
    
    /**
     * Configure WebClient for GraphQL connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.graphql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebClient graphqlWebClient(ConnectorProperties properties) {
        ConnectorProperties.GraphQlConfig graphQlConfig = properties.getGraphql();
        
        return WebClient.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(graphQlConfig.getMaxInMemorySize());
            })
            .build();
    }
    
    /**
     * Configure GraphQL connector.
     */
    @Bean
    @ConditionalOnProperty(prefix = "prospringjms.connector.graphql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GraphQlConnector graphQlConnector(WebClient graphqlWebClient) {
        return new GraphQlConnector(graphqlWebClient);
    }
    
    /**
     * Configure connector manager.
     */
    @Bean
    public ConnectorManager connectorManager() {
        return new ConnectorManager();
    }
}