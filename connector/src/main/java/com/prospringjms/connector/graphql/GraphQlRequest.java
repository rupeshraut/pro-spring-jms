package com.prospringjms.connector.graphql;

import java.util.Map;
import java.util.HashMap;

/**
 * GraphQL request model containing query, variables, operation type, and headers.
 */
public class GraphQlRequest {
    
    public enum OperationType {
        QUERY, MUTATION, SUBSCRIPTION
    }
    
    private final String query;
    private final Map<String, Object> variables;
    private final String operationName;
    private final OperationType operationType;
    private final Map<String, Object> headers;
    
    private GraphQlRequest(Builder builder) {
        this.query = builder.query;
        this.variables = new HashMap<>(builder.variables);
        this.operationName = builder.operationName;
        this.operationType = builder.operationType;
        this.headers = new HashMap<>(builder.headers);
    }
    
    // Getters
    public String getQuery() { return query; }
    public Map<String, Object> getVariables() { return new HashMap<>(variables); }
    public String getOperationName() { return operationName; }
    public OperationType getOperationType() { return operationType; }
    public Map<String, Object> getHeaders() { return new HashMap<>(headers); }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String query;
        private Map<String, Object> variables = new HashMap<>();
        private String operationName;
        private OperationType operationType = OperationType.QUERY;
        private Map<String, Object> headers = new HashMap<>();
        
        public Builder query(String query) {
            this.query = query;
            return this;
        }
        
        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }
        
        public Builder variables(Map<String, Object> variables) {
            if (variables != null) {
                this.variables.putAll(variables);
            }
            return this;
        }
        
        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }
        
        public Builder operationType(OperationType operationType) {
            this.operationType = operationType;
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
        
        public GraphQlRequest build() {
            if (query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("Query is required");
            }
            return new GraphQlRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("GraphQlRequest{operationType=%s, operationName='%s', " +
                           "variablesCount=%d, headersCount=%d, query='%.50s...'}",
            operationType, operationName, variables.size(), headers.size(), 
            query != null ? query.replaceAll("\\s+", " ") : "null");
    }
}