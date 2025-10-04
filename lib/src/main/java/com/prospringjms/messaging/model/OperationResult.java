package com.prospringjms.messaging.model;

/**
 * Result of operation execution in VETRO pipeline.
 * Contains operation status and result information.
 */
public class OperationResult {
    
    private final boolean successful;
    private final String messageId;
    private final String datacenter;
    private final String resultMessage;
    private final Exception exception;
    
    public OperationResult(boolean successful, String messageId, String datacenter, String resultMessage) {
        this.successful = successful;
        this.messageId = messageId;
        this.datacenter = datacenter;
        this.resultMessage = resultMessage;
        this.exception = null;
    }
    
    public OperationResult(boolean successful, String messageId, String datacenter, String resultMessage, Exception exception) {
        this.successful = successful;
        this.messageId = messageId;
        this.datacenter = datacenter;
        this.resultMessage = resultMessage;
        this.exception = exception;
    }
    
    public static OperationResult success(String messageId, String datacenter) {
        return new OperationResult(true, messageId, datacenter, "Operation completed successfully");
    }
    
    public static OperationResult success(String messageId, String datacenter, String message) {
        return new OperationResult(true, messageId, datacenter, message);
    }
    
    public static OperationResult failure(String errorMessage) {
        return new OperationResult(false, null, null, errorMessage);
    }
    
    public static OperationResult failure(String errorMessage, Exception exception) {
        return new OperationResult(false, null, null, errorMessage, exception);
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getDatacenter() {
        return datacenter;
    }
    
    public String getResultMessage() {
        return resultMessage;
    }
    
    public String getErrorMessage() {
        return successful ? null : resultMessage;
    }
    
    public Exception getException() {
        return exception;
    }
    
    public boolean hasException() {
        return exception != null;
    }
    
    @Override
    public String toString() {
        return String.format("OperationResult{successful=%s, messageId='%s', datacenter='%s', message='%s'}", 
            successful, messageId, datacenter, resultMessage);
    }
}