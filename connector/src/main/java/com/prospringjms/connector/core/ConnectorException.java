package com.prospringjms.connector.core;

/**
 * Exception thrown by backend connectors for communication or processing errors.
 */
public class ConnectorException extends Exception {
    
    private final ConnectorType connectorType;
    private final String endpoint;
    private final String datacenter;
    private final String operation;
    private final int errorCode;
    
    public ConnectorException(String message, ConnectorType connectorType, String endpoint, String datacenter) {
        super(message);
        this.connectorType = connectorType;
        this.endpoint = endpoint;
        this.datacenter = datacenter;
        this.operation = null;
        this.errorCode = -1;
    }
    
    public ConnectorException(String message, Throwable cause, ConnectorType connectorType, 
                            String endpoint, String datacenter, String operation) {
        super(message, cause);
        this.connectorType = connectorType;
        this.endpoint = endpoint;
        this.datacenter = datacenter;
        this.operation = operation;
        this.errorCode = -1;
    }
    
    public ConnectorException(String message, Throwable cause, ConnectorType connectorType, 
                            String endpoint, String datacenter, String operation, int errorCode) {
        super(message, cause);
        this.connectorType = connectorType;
        this.endpoint = endpoint;
        this.datacenter = datacenter;
        this.operation = operation;
        this.errorCode = errorCode;
    }
    
    public ConnectorType getConnectorType() { return connectorType; }
    public String getEndpoint() { return endpoint; }
    public String getDatacenter() { return datacenter; }
    public String getOperation() { return operation; }
    public int getErrorCode() { return errorCode; }
    
    @Override
    public String toString() {
        return String.format("ConnectorException{type=%s, endpoint='%s', datacenter='%s', operation='%s', errorCode=%d, message='%s'}", 
            connectorType, endpoint, datacenter, operation, errorCode, getMessage());
    }
}