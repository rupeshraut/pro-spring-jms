package com.prospringjms.connector.graphql;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * GraphQL response model containing data, errors, extensions, and success indicator.
 */
public class GraphQlResponse {
    
    private final Map<String, Object> data;
    private final List<Object> errors;
    private final Map<String, Object> extensions;
    private final boolean success;
    private final String message;
    private final long timestamp;
    
    public GraphQlResponse(Object data, List<Object> errors, 
                          Map<String, Object> extensions, boolean success, 
                          String message, long timestamp) {
        this.data = data instanceof Map ? new HashMap<>((Map<String, Object>) data) : new HashMap<>();
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.extensions = extensions != null ? new HashMap<>(extensions) : new HashMap<>();
        this.success = success;
        this.message = message;
        this.timestamp = timestamp;
    }
    
    // Getters
    public Map<String, Object> getData() { return new HashMap<>(data); }
    public List<Object> getErrors() { return new ArrayList<>(errors); }
    public Map<String, Object> getExtensions() { return new HashMap<>(extensions); }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public Object getDataField(String fieldName) {
        return data.get(fieldName);
    }
    
    public Object getExtension(String extensionName) {
        return extensions.get(extensionName);
    }
    
    @Override
    public String toString() {
        return String.format("GraphQlResponse{success=%s, hasData=%s, errorsCount=%d, " +
                           "extensionsCount=%d, message='%s'}",
            success, hasData(), errors.size(), extensions.size(), message);
    }
    
    /**
     * GraphQL error representation.
     */
    public static class GraphQlError {
        private final String message;
        private final List<Location> locations;
        private final List<String> path;
        private final Map<String, Object> extensions;
        
        public GraphQlError(String message, List<Location> locations, 
                           List<String> path, Map<String, Object> extensions) {
            this.message = message;
            this.locations = locations != null ? new ArrayList<>(locations) : new ArrayList<>();
            this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
            this.extensions = extensions != null ? new HashMap<>(extensions) : new HashMap<>();
        }
        
        public String getMessage() { return message; }
        public List<Location> getLocations() { return new ArrayList<>(locations); }
        public List<String> getPath() { return new ArrayList<>(path); }
        public Map<String, Object> getExtensions() { return new HashMap<>(extensions); }
        
        @Override
        public String toString() {
            return String.format("GraphQlError{message='%s', locationsCount=%d, pathLength=%d}",
                message, locations.size(), path.size());
        }
        
        /**
         * Location in GraphQL document where error occurred.
         */
        public static class Location {
            private final int line;
            private final int column;
            
            public Location(int line, int column) {
                this.line = line;
                this.column = column;
            }
            
            public int getLine() { return line; }
            public int getColumn() { return column; }
            
            @Override
            public String toString() {
                return String.format("Location{line=%d, column=%d}", line, column);
            }
        }
    }
}