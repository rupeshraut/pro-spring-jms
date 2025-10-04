package com.prospringjms.connector.rest;

import com.prospringjms.connector.core.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * REST API connector supporting both synchronous and asynchronous HTTP calls
 * with comprehensive resilience patterns.
 * 
 * Supports various HTTP methods (GET, POST, PUT, DELETE, PATCH) and handles
 * different content types (JSON, XML, form data).
 */
@Component
public class RestConnector extends AbstractResilientConnector<RestRequest, RestResponse> {
    
    private final WebClient webClient;
    
    public RestConnector() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
        
        logger.info("REST connector initialized");
    }
    
    public RestConnector(WebClient webClient) {
        this.webClient = webClient;
        logger.info("REST connector initialized with custom WebClient");
    }
    
    @Override
    protected RestResponse doSendSync(RestRequest request, ConnectorContext context) throws ConnectorException {
        try {
            logger.debug("Sending sync REST request to: {} [{}]", context.getEndpoint(), request.getMethod());
            
            WebClient.RequestBodySpec requestSpec = buildRequest(request, context);
            
            // Execute request and block for response
            ResponseEntity<String> responseEntity = requestSpec
                .retrieve()
                .toEntity(String.class)
                .timeout(context.getTimeout())
                .block();
            
            if (responseEntity == null) {
                throw new ConnectorException("Received null response", null, getType(), 
                    context.getEndpoint(), context.getDatacenter(), "sendSync");
            }
            
            RestResponse response = new RestResponse(
                responseEntity.getStatusCode().value(),
                responseEntity.getHeaders().toSingleValueMap(),
                responseEntity.getBody(),
                responseEntity.getStatusCode().is2xxSuccessful()
            );
            
            logger.debug("Received sync REST response: {} from {}", 
                response.getStatusCode(), context.getEndpoint());
            
            return response;
            
        } catch (WebClientResponseException e) {
            throw new ConnectorException(
                String.format("REST call failed with status %d: %s", e.getStatusCode().value(), e.getMessage()),
                e, getType(), context.getEndpoint(), context.getDatacenter(), "sendSync", e.getStatusCode().value()
            );
        } catch (WebClientException e) {
            throw new ConnectorException("REST call failed", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendSync");
        } catch (Exception e) {
            throw new ConnectorException("Unexpected error during REST call", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendSync");
        }
    }
    
    @Override
    protected CompletableFuture<RestResponse> doSendAsync(RestRequest request, ConnectorContext context) {
        logger.debug("Sending async REST request to: {} [{}]", context.getEndpoint(), request.getMethod());
        
        try {
            WebClient.RequestBodySpec requestSpec = buildRequest(request, context);
            
            Mono<RestResponse> responseMono = requestSpec
                .retrieve()
                .toEntity(String.class)
                .timeout(context.getTimeout())
                .map(responseEntity -> new RestResponse(
                    responseEntity.getStatusCode().value(),
                    responseEntity.getHeaders().toSingleValueMap(),
                    responseEntity.getBody(),
                    responseEntity.getStatusCode().is2xxSuccessful()
                ))
                .doOnSuccess(response -> logger.debug("Received async REST response: {} from {}", 
                    response.getStatusCode(), context.getEndpoint()))
                .doOnError(error -> logger.error("Async REST request failed for: {}", 
                    context.getEndpoint(), error));
            
            return responseMono.toFuture();
            
        } catch (Exception e) {
            logger.error("Failed to initiate async REST request to: {}", context.getEndpoint(), e);
            CompletableFuture<RestResponse> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ConnectorException(
                "Failed to initiate async REST request", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendAsync"));
            return failedFuture;
        }
    }
    
    @Override
    protected CompletableFuture<Void> doSendAsyncNoResponse(RestRequest request, ConnectorContext context) {
        logger.debug("Sending fire-and-forget REST request to: {} [{}]", 
            context.getEndpoint(), request.getMethod());
        
        try {
            WebClient.RequestBodySpec requestSpec = buildRequest(request, context);
            
            Mono<Void> responseMono = requestSpec
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(context.getTimeout())
                .doOnSuccess(v -> logger.debug("Fire-and-forget REST request completed for: {}", 
                    context.getEndpoint()))
                .doOnError(error -> logger.error("Fire-and-forget REST request failed for: {}", 
                    context.getEndpoint(), error));
            
            return responseMono.toFuture();
            
        } catch (Exception e) {
            logger.error("Failed to initiate fire-and-forget REST request to: {}", 
                context.getEndpoint(), e);
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ConnectorException(
                "Failed to initiate fire-and-forget REST request", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendAsyncNoResponse"));
            return failedFuture;
        }
    }
    
    @Override
    protected boolean doHealthCheck(ConnectorContext context) {
        try {
            // Create a simple health check request
            RestRequest healthRequest = RestRequest.builder()
                .method(HttpMethod.GET)
                .build();
            
            WebClient.RequestBodySpec requestSpec = buildRequest(healthRequest, context);
            
            ResponseEntity<String> response = requestSpec
                .retrieve()
                .toEntity(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            return response != null && response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.debug("Health check failed for REST endpoint: {}", context.getEndpoint(), e);
            return false;
        }
    }
    
    /**
     * Build WebClient request from RestRequest and ConnectorContext.
     */
    private WebClient.RequestBodySpec buildRequest(RestRequest request, ConnectorContext context) {
        // Build URL with query parameters
        String baseUrl = context.getEndpoint();
        if (request.getPath() != null && !request.getPath().isEmpty()) {
            baseUrl = baseUrl.endsWith("/") ? baseUrl + request.getPath() : baseUrl + "/" + request.getPath();
        }
        
        String finalUrl = baseUrl;
        if (request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            if (!baseUrl.contains("?")) {
                urlBuilder.append("?");
            } else {
                urlBuilder.append("&");
            }
            
            request.getQueryParams().forEach((key, value) -> 
                urlBuilder.append(key).append("=").append(value).append("&"));
            
            // Remove trailing &
            finalUrl = urlBuilder.toString();
            if (finalUrl.endsWith("&")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
        }
        
        // Create request spec
        WebClient.RequestBodySpec requestSpec = webClient.method(request.getMethod())
            .uri(finalUrl);
        
        // Add headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            for (Map.Entry<String, Object> header : request.getHeaders().entrySet()) {
                requestSpec.header(header.getKey(), String.valueOf(header.getValue()));
            }
        }
        
        // Add request body if present
        if (request.getBody() != null) {
            if (request.getContentType() != null) {
                requestSpec.contentType(request.getContentType());
            }
            requestSpec.bodyValue(request.getBody());
        }
        
        return requestSpec;
    }
    
    @Override
    public ConnectorType getType() {
        return ConnectorType.REST;
    }
    
    @Override
    protected void doClose() {
        // WebClient doesn't require explicit cleanup
        logger.info("REST connector closed");
    }
    
    /**
     * Convenience method for GET requests.
     */
    public RestResponse get(String url, Map<String, Object> headers, ConnectorContext context) 
            throws ConnectorException {
        RestRequest request = RestRequest.builder()
            .method(HttpMethod.GET)
            .headers(headers)
            .build();
        
        return sendSync(request, context.builder().endpoint(url).build());
    }
    
    /**
     * Convenience method for POST requests.
     */
    public RestResponse post(String url, Object body, Map<String, Object> headers, ConnectorContext context) 
            throws ConnectorException {
        RestRequest request = RestRequest.builder()
            .method(HttpMethod.POST)
            .body(body)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers)
            .build();
        
        return sendSync(request, context.builder().endpoint(url).build());
    }
    
    /**
     * Convenience method for PUT requests.
     */
    public RestResponse put(String url, Object body, Map<String, Object> headers, ConnectorContext context) 
            throws ConnectorException {
        RestRequest request = RestRequest.builder()
            .method(HttpMethod.PUT)
            .body(body)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers)
            .build();
        
        return sendSync(request, context.builder().endpoint(url).build());
    }
    
    /**
     * Convenience method for DELETE requests.
     */
    public RestResponse delete(String url, Map<String, Object> headers, ConnectorContext context) 
            throws ConnectorException {
        RestRequest request = RestRequest.builder()
            .method(HttpMethod.DELETE)
            .headers(headers)
            .build();
        
        return sendSync(request, context.builder().endpoint(url).build());
    }
}