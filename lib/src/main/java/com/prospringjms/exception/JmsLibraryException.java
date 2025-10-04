package com.prospringjms.exception;

/**
 * Base exception for JMS library operations.
 */
public class JmsLibraryException extends Exception {
    
    private final String datacenter;
    private final String operation;

    public JmsLibraryException(String message) {
        super(message);
        this.datacenter = null;
        this.operation = null;
    }

    public JmsLibraryException(String message, Throwable cause) {
        super(message, cause);
        this.datacenter = null;
        this.operation = null;
    }

    public JmsLibraryException(String message, String datacenter, String operation) {
        super(message);
        this.datacenter = datacenter;
        this.operation = operation;
    }

    public JmsLibraryException(String message, String datacenter, String operation, Throwable cause) {
        super(message, cause);
        this.datacenter = datacenter;
        this.operation = operation;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public String getOperation() {
        return operation;
    }
}

/**
 * Exception thrown when all datacenters are unavailable.
 */
class AllDatacentersUnavailableException extends JmsLibraryException {
    public AllDatacentersUnavailableException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when circuit breaker is open.
 */
class CircuitBreakerOpenException extends JmsLibraryException {
    public CircuitBreakerOpenException(String datacenter) {
        super("Circuit breaker is open for datacenter: " + datacenter, datacenter, "circuit-breaker");
    }
}

/**
 * Exception thrown when rate limit is exceeded.
 */
class RateLimitExceededException extends JmsLibraryException {
    public RateLimitExceededException(String datacenter) {
        super("Rate limit exceeded for datacenter: " + datacenter, datacenter, "rate-limiting");
    }
}

/**
 * Exception thrown when datacenter is not found.
 */
class DatacenterNotFoundException extends JmsLibraryException {
    public DatacenterNotFoundException(String datacenter) {
        super("Datacenter not found: " + datacenter, datacenter, "lookup");
    }
}