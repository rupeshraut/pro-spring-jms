package com.prospringjms.connector.rest;

import java.util.Map;
import java.util.HashMap;

/**
 * REST response model containing status code, headers, body, and success indicator.
 */
public class RestResponse {
    
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final boolean success;
    private final long responseTime;
    
    public RestResponse(int statusCode, Map<String, String> headers, String body, boolean success) {
        this.statusCode = statusCode;
        this.headers = new HashMap<>(headers != null ? headers : new HashMap<>());
        this.body = body;
        this.success = success;
        this.responseTime = System.currentTimeMillis();
    }
    
    // Getters
    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return new HashMap<>(headers); }
    public String getBody() { return body; }
    public boolean isSuccess() { return success; }
    public long getResponseTime() { return responseTime; }
    
    public String getHeader(String key) {
        return headers.get(key);
    }
    
    public boolean hasBody() {
        return body != null && !body.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("RestResponse{statusCode=%d, success=%s, hasBody=%s, headers=%s}",
            statusCode, success, hasBody(), headers.keySet());
    }
}