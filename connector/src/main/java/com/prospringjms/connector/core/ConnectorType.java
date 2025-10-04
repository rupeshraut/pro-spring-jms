package com.prospringjms.connector.core;

/**
 * Supported connector types for backend communication.
 */
public enum ConnectorType {
    REST("REST API"),
    JMS("Java Message Service"),
    KAFKA("Apache Kafka"),
    GRAPHQL("GraphQL API");

    private final String description;

    ConnectorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}