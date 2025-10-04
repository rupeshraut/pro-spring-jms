package com.prospringjms.connector.rest;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.HashMap;

/**
 * REST request model containing HTTP method, path, headers, query parameters, and body.
 */
public class RestRequest {
    
    private final HttpMethod method;
    private final String path;
    private final Map<String, Object> headers;
    private final Map<String, Object> queryParams;
    private final Object body;
    private final MediaType contentType;
    
    private RestRequest(Builder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.headers = new HashMap<>(builder.headers);
        this.queryParams = new HashMap<>(builder.queryParams);
        this.body = builder.body;
        this.contentType = builder.contentType;
    }
    
    // Getters
    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public Map<String, Object> getHeaders() { return new HashMap<>(headers); }
    public Map<String, Object> getQueryParams() { return new HashMap<>(queryParams); }
    public Object getBody() { return body; }
    public MediaType getContentType() { return contentType; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private HttpMethod method = HttpMethod.GET;
        private String path;
        private Map<String, Object> headers = new HashMap<>();
        private Map<String, Object> queryParams = new HashMap<>();
        private Object body;
        private MediaType contentType;
        
        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Builder headers(Map<String, Object> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }
        
        public Builder queryParam(String key, Object value) {
            this.queryParams.put(key, value);
            return this;
        }
        
        public Builder queryParams(Map<String, Object> queryParams) {
            if (queryParams != null) {
                this.queryParams.putAll(queryParams);
            }
            return this;
        }
        
        public Builder body(Object body) {
            this.body = body;
            return this;
        }
        
        public Builder contentType(MediaType contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public RestRequest build() {
            return new RestRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("RestRequest{method=%s, path='%s', headers=%s, queryParams=%s, hasBody=%s, contentType=%s}",
            method, path, headers, queryParams, body != null, contentType);
    }
}