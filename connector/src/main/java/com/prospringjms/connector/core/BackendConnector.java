package com.prospringjms.connector.core;

import java.util.concurrent.CompletableFuture;

/**
 * Core interface for all backend connectors.
 * Supports both synchronous and asynchronous communication patterns
 * with built-in resilience using Resilience4j patterns.
 * 
 * Based on VETRO pattern principles where backend connectors handle
 * the "Operate" step - actual invocation of backend services.
 */
public interface BackendConnector<TRequest, TResponse> {

    /**
     * Synchronous request-response communication.
     * 
     * @param request the request payload
     * @param context connection context with routing and configuration
     * @return response from backend service
     * @throws ConnectorException on communication or processing errors
     */
    TResponse sendSync(TRequest request, ConnectorContext context) throws ConnectorException;

    /**
     * Asynchronous request-response communication.
     * 
     * @param request the request payload
     * @param context connection context with routing and configuration
     * @return CompletableFuture containing the response
     */
    CompletableFuture<TResponse> sendAsync(TRequest request, ConnectorContext context);

    /**
     * Fire-and-forget asynchronous communication (no response expected).
     * 
     * @param request the request payload
     * @param context connection context with routing and configuration
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> sendAsyncNoResponse(TRequest request, ConnectorContext context);

    /**
     * Health check for the backend service.
     * 
     * @param context connection context
     * @return true if backend is healthy, false otherwise
     */
    boolean isHealthy(ConnectorContext context);

    /**
     * Get connector type identifier.
     * 
     * @return connector type (REST, JMS, KAFKA, GRAPHQL)
     */
    ConnectorType getType();

    /**
     * Close and cleanup resources.
     */
    void close();
}