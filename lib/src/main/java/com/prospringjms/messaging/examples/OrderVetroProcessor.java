package com.prospringjms.messaging.examples;

import com.prospringjms.messaging.VetroMessageProcessor;
import com.prospringjms.messaging.context.SessionAwareProcessingContext;
import com.prospringjms.messaging.context.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced concrete implementation of VETRO message processor for order processing.
 * 
 * This example demonstrates how to extend the abstract VetroMessageProcessor
 * with session awareness and retry context handling for robust order processing.
 */
@Component
public class OrderVetroProcessor extends VetroMessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderVetroProcessor.class);
    
    public OrderVetroProcessor() {
        // Configure retry settings for order processing
        configureRetry(3, 2000, 2.0); // 3 attempts, 2 second base delay, 2x backoff
    }
    
    @Override
    protected ValidationResult validate(Object payload, SessionAwareProcessingContext context) {
        logger.debug("Validating order message for correlation: {} (attempt {}/{})", 
            context.getCorrelationId(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        // Check if this is a retry attempt
        if (context.isRetryAttempt()) {
            RetryContext retryContext = context.getRetryContext();
            logger.info("Retry validation attempt {} for order: {}, previous failure: {}", 
                retryContext.getCurrentAttempt(), context.getCorrelationId(), 
                retryContext.getLastException() != null ? retryContext.getLastException().getMessage() : "unknown");
        }
        
        if (payload == null) {
            return ValidationResult.failure("payload", "Payload cannot be null");
        }
        
        if (payload instanceof OrderMessage) {
            OrderMessage order = (OrderMessage) payload;
            
            // Validate required fields
            Map<String, String> errors = new HashMap<>();
            
            if (order.getCustomerId() == null || order.getCustomerId().trim().isEmpty()) {
                errors.put("customerId", "Customer ID is required");
            }
            
            if (order.getProductId() == null || order.getProductId().trim().isEmpty()) {
                errors.put("productId", "Product ID is required");
            }
            
            if (order.getQuantity() <= 0) {
                errors.put("quantity", "Quantity must be positive");
            }
            
            if (order.getAmount() <= 0) {
                errors.put("amount", "Amount must be positive");
            }
            
            if (!errors.isEmpty()) {
                return new ValidationResult(false, errors);
            }
            
            // Business validation
            if (order.getAmount() > 10000) {
                context.addProperty("highValueOrder", true);
                logger.info("High value order detected: {} for correlation: {}", 
                    order.getAmount(), context.getCorrelationId());
            }
            
            return ValidationResult.success();
        }
        
        return ValidationResult.failure("payload", "Invalid payload type, expected OrderMessage");
    }
    
    @Override
    protected Object enrich(Object payload, SessionAwareProcessingContext context) {
        logger.debug("Enriching order message for correlation: {} (attempt {}/{})", 
            context.getCorrelationId(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        // Log retry context if this is a retry
        if (context.isRetryAttempt()) {
            RetryContext retryContext = context.getRetryContext();
            logger.info("Retry enrichment attempt {} for order: {}", 
                retryContext.getCurrentAttempt(), context.getCorrelationId());
        }
        
        OrderMessage order = (OrderMessage) payload;
        
        // Simulate enrichment with customer data
        CustomerInfo customerInfo = lookupCustomerInfo(order.getCustomerId());
        order.setCustomerInfo(customerInfo);
        
        // Simulate enrichment with product data
        ProductInfo productInfo = lookupProductInfo(order.getProductId());
        order.setProductInfo(productInfo);
        
        // Add pricing information
        PricingInfo pricingInfo = calculatePricing(order);
        order.setPricingInfo(pricingInfo);
        
        // Set enrichment metadata
        order.setEnrichmentTimestamp(System.currentTimeMillis());
        order.setEnrichedBy("OrderVetroProcessor");
        
        logger.debug("Order enrichment completed for correlation: {}", context.getCorrelationId());
        return order;
    }
    
    @Override
    protected Object transform(Object payload, SessionAwareProcessingContext context) {
        logger.debug("Transforming order message for correlation: {} (attempt {}/{})", 
            context.getCorrelationId(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        OrderMessage order = (OrderMessage) payload;
        
        // Create transformed order message for downstream systems
        TransformedOrderMessage transformedOrder = new TransformedOrderMessage();
        transformedOrder.setOrderId(order.getOrderId());
        transformedOrder.setCustomerId(order.getCustomerId());
        transformedOrder.setCustomerName(order.getCustomerInfo().getName());
        transformedOrder.setCustomerTier(order.getCustomerInfo().getTier());
        transformedOrder.setProductId(order.getProductId());
        transformedOrder.setProductName(order.getProductInfo().getName());
        transformedOrder.setQuantity(order.getQuantity());
        transformedOrder.setUnitPrice(order.getPricingInfo().getUnitPrice());
        transformedOrder.setTotalAmount(order.getPricingInfo().getTotalAmount());
        transformedOrder.setTaxAmount(order.getPricingInfo().getTaxAmount());
        transformedOrder.setCurrency(order.getPricingInfo().getCurrency());
        
        // Add transformation metadata
        transformedOrder.setProcessingTimestamp(System.currentTimeMillis());
        transformedOrder.setCorrelationId(context.getCorrelationId());
        transformedOrder.setMessageType("TRANSFORMED_ORDER");
        
        // Apply business rules during transformation
        if (Boolean.TRUE.equals(context.getProperty("highValueOrder"))) {
            transformedOrder.setPriority("HIGH");
            transformedOrder.setRequiresApproval(true);
        } else {
            transformedOrder.setPriority("NORMAL");
            transformedOrder.setRequiresApproval(false);
        }
        
        logger.debug("Order transformation completed for correlation: {}", context.getCorrelationId());
        return transformedOrder;
    }
    
    @Override
    protected RoutingDecision route(Object payload, SessionAwareProcessingContext context) {
        logger.debug("Routing order message for correlation: {} (attempt {}/{})", 
            context.getCorrelationId(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        TransformedOrderMessage transformedOrder = (TransformedOrderMessage) payload;
        
        // Routing logic based on order characteristics
        String destination;
        String datacenter = "primary"; // default datacenter
        Map<String, Object> headers = new HashMap<>();
        boolean expectResponse = false;
        String responseDestination = null;
        String responseDatacenter = null;
        
        // Route based on customer tier and order value
        if ("PREMIUM".equals(transformedOrder.getCustomerTier())) {
            destination = "premium.orders.queue";
            datacenter = "primary";
            headers.put("priority", "HIGH");
        } else if (transformedOrder.isRequiresApproval()) {
            destination = "approval.orders.queue";
            datacenter = "primary";
            expectResponse = true;
            responseDestination = "approval.response.queue";
            responseDatacenter = "primary";
            headers.put("approvalRequired", true);
        } else {
            destination = "standard.orders.queue";
            datacenter = "secondary"; // Load balance to secondary DC
            headers.put("priority", "NORMAL");
        }
        
        // Add routing metadata
        headers.put("routingTimestamp", System.currentTimeMillis());
        headers.put("routedBy", "OrderVetroProcessor");
        headers.put("customerTier", transformedOrder.getCustomerTier());
        
        logger.info("Order routed to destination: {} in datacenter: {} for correlation: {}", 
            destination, datacenter, context.getCorrelationId());
        
        return new RoutingDecision(destination, datacenter, headers, 
            expectResponse, responseDestination, responseDatacenter);
    }
    
    @Override
    protected void handleResponse(Object responsePayload, ProcessingContext originalContext) {
        logger.info("Handling approval response for correlation: {}", originalContext.getCorrelationId());
        
        if (responsePayload instanceof String) {
            String response = (String) responsePayload;
            
            // Parse approval response
            if (response.contains("APPROVED")) {
                logger.info("Order approved for correlation: {}", originalContext.getCorrelationId());
                // Process approved order - could trigger next step in workflow
                processApprovedOrder(originalContext);
            } else if (response.contains("REJECTED")) {
                logger.warn("Order rejected for correlation: {}", originalContext.getCorrelationId());
                // Handle rejection - notify customer, update status
                processRejectedOrder(originalContext);
            } else {
                logger.warn("Unknown approval response for correlation: {}: {}", 
                    originalContext.getCorrelationId(), response);
            }
        }
    }
    
    // Helper methods for enrichment (simulate external service calls)
    
    private CustomerInfo lookupCustomerInfo(String customerId) {
        // Simulate customer lookup
        CustomerInfo info = new CustomerInfo();
        info.setCustomerId(customerId);
        info.setName("Customer " + customerId);
        info.setTier(customerId.startsWith("PREM") ? "PREMIUM" : "STANDARD");
        info.setEmail(customerId.toLowerCase() + "@example.com");
        return info;
    }
    
    private ProductInfo lookupProductInfo(String productId) {
        // Simulate product lookup
        ProductInfo info = new ProductInfo();
        info.setProductId(productId);
        info.setName("Product " + productId);
        info.setCategory("ELECTRONICS");
        info.setBasePrice(99.99);
        return info;
    }
    
    private PricingInfo calculatePricing(OrderMessage order) {
        // Simulate pricing calculation
        PricingInfo pricing = new PricingInfo();
        double unitPrice = order.getProductInfo().getBasePrice();
        double totalAmount = unitPrice * order.getQuantity();
        double taxRate = 0.08; // 8% tax
        double taxAmount = totalAmount * taxRate;
        
        pricing.setUnitPrice(unitPrice);
        pricing.setTotalAmount(totalAmount);
        pricing.setTaxAmount(taxAmount);
        pricing.setCurrency("USD");
        
        return pricing;
    }
    
    private void processApprovedOrder(ProcessingContext context) {
        logger.info("Processing approved order for correlation: {}", context.getCorrelationId());
        // Implementation would trigger fulfillment workflow
    }
    
    private void processRejectedOrder(ProcessingContext context) {
        logger.info("Processing rejected order for correlation: {}", context.getCorrelationId());
        // Implementation would notify customer and update order status
    }
    
    @Override
    protected boolean shouldRetryStep(String step, Exception exception, SessionAwareProcessingContext context) {
        logger.info("Determining retry for step '{}' with exception: {} (attempt {}/{})", 
            step, exception.getMessage(), context.getCurrentRetryAttempt(), context.getMaxRetryAttempts());
        
        // Custom retry logic for order processing
        switch (step) {
            case "validation":
                // Don't retry validation failures - data issues won't fix themselves
                logger.info("Validation failures are not retried for order: {}", context.getCorrelationId());
                return false;
                
            case "enrichment":
                // Retry enrichment failures - external services might recover
                if (exception.getMessage().contains("timeout") || 
                    exception.getMessage().contains("connection")) {
                    logger.info("Retrying enrichment for order: {} due to connectivity issue", 
                        context.getCorrelationId());
                    return true;
                }
                return false;
                
            case "transformation":
            case "routing":
            case "operation":
                // Retry these steps for most exceptions
                logger.info("Retrying {} for order: {}", step, context.getCorrelationId());
                return true;
                
            default:
                return false;
        }
    }
    
    @Override
    protected void handleFinalFailure(String step, Exception exception, SessionAwareProcessingContext context) {
        logger.error("Final failure processing order {} at step '{}' after {} attempts", 
            context.getCorrelationId(), step, context.getCurrentRetryAttempt(), exception);
        
        RetryContext retryContext = context.getRetryContext();
        if (retryContext != null) {
            logger.error("Retry history - Total elapsed time: {}ms, First attempt: {}, Final attempt: {}", 
                retryContext.getTotalElapsedTimeMs(), 
                retryContext.getFirstAttemptTime(), 
                retryContext.getCurrentAttemptTime());
        }
        
        try {
            // Send to dead letter queue for manual processing
            Map<String, Object> headers = new HashMap<>();
            headers.put("failureReason", exception.getMessage());
            headers.put("failedStep", step);
            headers.put("totalAttempts", context.getCurrentRetryAttempt());
            headers.put("originalCorrelationId", context.getCorrelationId());
            headers.put("failureTimestamp", System.currentTimeMillis());
            
            // Send original payload to DLQ
            Object originalPayload = context.getAttribute("payload");
            jmsSender.sendToPrimary("order.processing.dlq", originalPayload, headers);
            
            logger.info("Order {} sent to dead letter queue after final failure", context.getCorrelationId());
            
            // Rollback session to prevent message acknowledgment
            if (context.isSessionTransacted()) {
                context.rollbackSession();
                logger.info("JMS session rolled back for failed order: {}", context.getCorrelationId());
            }
            
        } catch (Exception dlqException) {
            logger.error("Failed to send order {} to dead letter queue", context.getCorrelationId(), dlqException);
            
            // If we can't send to DLQ, ensure session rollback
            if (context.isSessionTransacted()) {
                try {
                    context.rollbackSession();
                } catch (Exception rollbackEx) {
                    logger.error("Failed to rollback session for order: {}", context.getCorrelationId(), rollbackEx);
                }
            }
        }
    }
    
    @Override
    protected void handleResponse(Object responsePayload, SessionAwareProcessingContext originalContext) {
        logger.info("Received response for order: {}", originalContext.getCorrelationId());
        
        // Custom response handling based on payload type
        if (responsePayload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) responsePayload;
            
            Boolean successful = (Boolean) response.get("successful");
            if (Boolean.TRUE.equals(successful)) {
                logger.info("Order {} processed successfully", originalContext.getCorrelationId());
                
                // Commit session for successful processing
                if (originalContext.isSessionTransacted()) {
                    originalContext.commitSession();
                }
            } else {
                String errorCode = (String) response.get("errorCode");
                logger.error("Order {} processing failed with error: {}", 
                    originalContext.getCorrelationId(), errorCode);
                
                // Handle different error scenarios
                handleProcessingError(errorCode, originalContext);
            }
        } else {
            logger.info("Received response of type {} for order: {}", 
                responsePayload.getClass().getSimpleName(), originalContext.getCorrelationId());
        }
    }
    
    private void handleProcessingError(String errorCode, SessionAwareProcessingContext context) {
        // Custom error handling based on error code
        if ("INSUFFICIENT_INVENTORY".equals(errorCode)) {
            logger.info("Order {} has insufficient inventory, sending to backorder queue", context.getCorrelationId());
            // Could send to backorder processing queue
        } else if ("PAYMENT_FAILED".equals(errorCode)) {
            logger.info("Payment failed for order {}, sending to payment retry", context.getCorrelationId());
            // Could send to payment retry mechanism
        } else {
            logger.warn("Unknown error code '{}' for order {}", errorCode, context.getCorrelationId());
        }
    }
    
    // Supporting data classes
    
    public static class OrderMessage {
        private String orderId;
        private String customerId;
        private String productId;
        private int quantity;
        private double amount;
        private CustomerInfo customerInfo;
        private ProductInfo productInfo;
        private PricingInfo pricingInfo;
        private long enrichmentTimestamp;
        private String enrichedBy;
        
        // Constructors
        public OrderMessage() {
            this.orderId = UUID.randomUUID().toString();
        }
        
        // Getters and setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public CustomerInfo getCustomerInfo() { return customerInfo; }
        public void setCustomerInfo(CustomerInfo customerInfo) { this.customerInfo = customerInfo; }
        public ProductInfo getProductInfo() { return productInfo; }
        public void setProductInfo(ProductInfo productInfo) { this.productInfo = productInfo; }
        public PricingInfo getPricingInfo() { return pricingInfo; }
        public void setPricingInfo(PricingInfo pricingInfo) { this.pricingInfo = pricingInfo; }
        public long getEnrichmentTimestamp() { return enrichmentTimestamp; }
        public void setEnrichmentTimestamp(long enrichmentTimestamp) { this.enrichmentTimestamp = enrichmentTimestamp; }
        public String getEnrichedBy() { return enrichedBy; }
        public void setEnrichedBy(String enrichedBy) { this.enrichedBy = enrichedBy; }
    }
    
    public static class TransformedOrderMessage {
        private String orderId;
        private String customerId;
        private String customerName;
        private String customerTier;
        private String productId;
        private String productName;
        private int quantity;
        private double unitPrice;
        private double totalAmount;
        private double taxAmount;
        private String currency;
        private String priority;
        private boolean requiresApproval;
        private long processingTimestamp;
        private String correlationId;
        private String messageType;
        
        // Getters and setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getCustomerTier() { return customerTier; }
        public void setCustomerTier(String customerTier) { this.customerTier = customerTier; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
        public double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
        public double getTaxAmount() { return taxAmount; }
        public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public boolean isRequiresApproval() { return requiresApproval; }
        public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
        public long getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(long processingTimestamp) { this.processingTimestamp = processingTimestamp; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }
    }
    
    public static class CustomerInfo {
        private String customerId;
        private String name;
        private String tier;
        private String email;
        
        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
    
    public static class ProductInfo {
        private String productId;
        private String name;
        private String category;
        private double basePrice;
        
        // Getters and setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getBasePrice() { return basePrice; }
        public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    }
    
    public static class PricingInfo {
        private double unitPrice;
        private double totalAmount;
        private double taxAmount;
        private String currency;
        
        // Getters and setters
        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
        public double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
        public double getTaxAmount() { return taxAmount; }
        public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}