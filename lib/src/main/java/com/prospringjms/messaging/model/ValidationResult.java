package com.prospringjms.messaging.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of message validation in VETRO pipeline.
 * Contains validation status and any error messages.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final List<String> errorMessages;
    private final String field;
    private final String errorMessage;
    
    private ValidationResult(boolean valid, String field, String errorMessage) {
        this.valid = valid;
        this.field = field;
        this.errorMessage = errorMessage;
        this.errorMessages = new ArrayList<>();
        if (errorMessage != null) {
            this.errorMessages.add(errorMessage);
        }
    }
    
    private ValidationResult(boolean valid, List<String> errorMessages) {
        this.valid = valid;
        this.errorMessages = new ArrayList<>(errorMessages);
        this.field = null;
        this.errorMessage = errorMessages.isEmpty() ? null : errorMessages.get(0);
    }
    
    public static ValidationResult success() {
        return new ValidationResult(true, (String) null, null);
    }
    
    public static ValidationResult failure(String field, String errorMessage) {
        return new ValidationResult(false, field, errorMessage);
    }
    
    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, null, errorMessage);
    }
    
    public static ValidationResult failure(List<String> errorMessages) {
        return new ValidationResult(false, errorMessages);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrorMessages() {
        return new ArrayList<>(errorMessages);
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getField() {
        return field;
    }
    
    public boolean hasErrors() {
        return !errorMessages.isEmpty();
    }
    
    public int getErrorCount() {
        return errorMessages.size();
    }
    
    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        } else {
            return String.format("ValidationResult{valid=false, field='%s', errors=%s}", field, errorMessages);
        }
    }
}