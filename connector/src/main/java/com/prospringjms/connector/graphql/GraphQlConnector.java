package com.prospringjms.connector.graphql;

import com.prospringjms.connector.core.*;
import com.prospringjms.connector.graphql.GraphQlRequest;
import com.prospringjms.connector.graphql.GraphQlResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;

/**
 * GraphQL connector supporting queries, mutations, and subscriptions.
 * Uses WebClient for HTTP-based GraphQL operations.
 */
@Component
public class GraphQlConnector extends AbstractResilientConnector<GraphQlRequest, GraphQlResponse> {
    
    private final WebClient webClient;
    
    public GraphQlConnector() {
        super();
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
        logger.info("GraphQL connector initialized with default WebClient");
    }
    
    public GraphQlConnector(WebClient webClient) {
        super();
        this.webClient = webClient != null ? webClient : WebClient.builder().build();
        logger.info("GraphQL connector initialized with WebClient");
    }
    
    @Override
    protected GraphQlResponse doSendSync(GraphQlRequest request, ConnectorContext context) 
            throws ConnectorException {
        try {
            logger.debug("Sending sync GraphQL request to: {}", context.getEndpoint());
            
            // Build GraphQL request body
            Map<String, Object> requestBody = buildGraphQlRequestBody(request);
            
            // Execute request
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(context.getEndpoint())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(context.getTimeout())
                .block();
            
            if (response == null) {
                throw new ConnectorException("Received null response", null, getType(), 
                    context.getEndpoint(), context.getDatacenter(), "sendSync");
            }
            
            return mapToGraphQlResponse(response);
            
        } catch (Exception e) {
            throw new ConnectorException("GraphQL sync request failed", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendSync");
        }
    }
    
    @Override
    protected CompletableFuture<GraphQlResponse> doSendAsync(GraphQlRequest request, ConnectorContext context) {
        logger.debug("Sending async GraphQL request to: {}", context.getEndpoint());
        
        try {
            // Build GraphQL request body
            Map<String, Object> requestBody = buildGraphQlRequestBody(request);
            
            // Execute async request
            Mono<GraphQlResponse> responseMono = webClient.post()
                .uri(context.getEndpoint())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(context.getTimeout())
                .map(this::mapToGraphQlResponse);
            
            return responseMono.toFuture();
            
        } catch (Exception e) {
            CompletableFuture<GraphQlResponse> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ConnectorException(
                "GraphQL async request setup failed", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendAsync"));
            return failedFuture;
        }
    }
    
    @Override
    protected CompletableFuture<Void> doSendAsyncNoResponse(GraphQlRequest request, ConnectorContext context) {
        logger.debug("Sending fire-and-forget GraphQL request to: {}", context.getEndpoint());
        
        try {
            // Build GraphQL request body
            Map<String, Object> requestBody = buildGraphQlRequestBody(request);
            
            // Execute fire-and-forget request and return CompletableFuture
            return webClient.post()
                .uri(context.getEndpoint())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(context.getTimeout())
                .then()
                .toFuture()
                .exceptionally(throwable -> {
                    logger.error("Fire-and-forget GraphQL request failed: {}", throwable.getMessage());
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("GraphQL fire-and-forget request failed", e);
            return CompletableFuture.failedFuture(new ConnectorException("GraphQL fire-and-forget request failed", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendAsyncNoResponse"));
        }
    }
    
    @Override
    protected boolean doHealthCheck(ConnectorContext context) {
        try {
            logger.debug("Performing GraphQL health check for: {}", context.getEndpoint());
            
            // Simple introspection query for health check
            Map<String, Object> healthRequest = Map.of(
                "query", "__schema { types { name } }"
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(context.getEndpoint())
                .bodyValue(healthRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            return response != null && (response.get("errors") == null || 
                ((List<?>) response.getOrDefault("errors", Collections.emptyList())).isEmpty());
            
        } catch (Exception e) {
            logger.debug("GraphQL health check failed for endpoint: {}", context.getEndpoint(), e);
            return false;
        }
    }
    
    @Override
    public ConnectorType getType() {
        return ConnectorType.GRAPHQL;
    }
    
    @Override
    protected void doClose() {
        logger.info("Simple GraphQL connector closed");
    }
    
    /**
     * Build GraphQL request body from request object.
     */
    private Map<String, Object> buildGraphQlRequestBody(GraphQlRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", request.getQuery());
        
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            body.put("variables", request.getVariables());
        }
        
        if (request.getOperationName() != null) {
            body.put("operationName", request.getOperationName());
        }
        
        return body;
    }
    
    /**
     * Map raw response to GraphQlResponse object.
     */
    @SuppressWarnings("unchecked")
    private GraphQlResponse mapToGraphQlResponse(Map<String, Object> response) {
        Object data = response.get("data");
        List<Object> errors = (List<Object>) response.get("errors");
        Map<String, Object> extensions = (Map<String, Object>) response.get("extensions");
        
        boolean success = errors == null || errors.isEmpty();
        
        return new GraphQlResponse(
            data,
            errors != null ? errors : Collections.emptyList(),
            extensions != null ? extensions : Collections.emptyMap(),
            success,
            "GraphQL request completed",
            System.currentTimeMillis()
        );
    }
}