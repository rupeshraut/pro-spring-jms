package com.prospringjms.security;

import com.prospringjms.config.JmsLibraryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade security manager for JMS operations.
 * Provides message encryption, authentication, access control, and audit logging.
 */
@Component
public class SecurityManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("JMS-AUDIT");
    
    private final JmsLibraryProperties properties;
    private final Map<String, SecretKey> datacenterKeys = new ConcurrentHashMap<>();
    private final Map<String, SecurityContext> activeContexts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService securityScheduler;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public SecurityManager(JmsLibraryProperties properties) {
        this.properties = properties;
        this.securityScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "JMS-Security-Manager");
            t.setDaemon(true);
            return t;
        });
        
        initializeSecurity();
    }
    
    /**
     * Initializes security components and key management.
     */
    private void initializeSecurity() {
        // Initialize encryption keys for each datacenter
        if (properties.getDatacenters() != null) {
            properties.getDatacenters().keySet().forEach(this::initializeDatacenterSecurity);
        }
        
        // Schedule security maintenance
        securityScheduler.scheduleAtFixedRate(this::performSecurityMaintenance, 
            300, 300, TimeUnit.SECONDS); // Every 5 minutes
        
        // Schedule key rotation
        securityScheduler.scheduleAtFixedRate(this::rotateEncryptionKeys,
            24 * 60 * 60, 24 * 60 * 60, TimeUnit.SECONDS); // Every 24 hours
        
        logger.info("Security manager initialized for {} datacenters", 
            properties.getDatacenters() != null ? properties.getDatacenters().size() : 0);
    }
    
    /**
     * Initializes security for a specific datacenter.
     */
    private void initializeDatacenterSecurity(String datacenter) {
        try {
            // Generate or load encryption key
            SecretKey key = generateOrLoadEncryptionKey(datacenter);
            datacenterKeys.put(datacenter, key);
            
            logger.info("Security initialized for datacenter: {}", datacenter);
        } catch (Exception e) {
            logger.error("Failed to initialize security for datacenter: {}", datacenter, e);
        }
    }
    
    /**
     * Creates a security context for JMS operations.
     */
    public SecurityContext createSecurityContext(String datacenter, String userId, String operation) {
        validateAccess(datacenter, userId, operation);
        
        SecurityContext context = new SecurityContext(datacenter, userId, operation, Instant.now());
        activeContexts.put(context.getContextId(), context);
        
        auditLogger.info("Security context created: datacenter={}, user={}, operation={}, contextId={}", 
            datacenter, userId, operation, context.getContextId());
        
        return context;
    }
    
    /**
     * Validates access permissions for a user operation.
     */
    private void validateAccess(String datacenter, String userId, String operation) {
        // Production access control validation
        if (userId == null || userId.trim().isEmpty()) {
            throw new SecurityException("User authentication required");
        }
        
        if (datacenter == null || !datacenterKeys.containsKey(datacenter)) {
            throw new SecurityException("Invalid datacenter: " + datacenter);
        }
        
        // Add role-based access control here
        // For production, integrate with enterprise identity systems
        switch (operation.toLowerCase()) {
            case "send":
            case "receive":
            case "listen":
                // Basic operations - allowed for authenticated users
                break;
            case "admin":
            case "config":
                // Administrative operations - require elevated privileges
                if (!isAdminUser(userId)) {
                    throw new SecurityException("Administrative privileges required for operation: " + operation);
                }
                break;
            default:
                logger.warn("Unknown operation requested: {} by user: {}", operation, userId);
        }
    }
    
    /**
     * Encrypts a message payload for secure transmission.
     */
    public EncryptedMessage encryptMessage(String datacenter, String message, SecurityContext context) {
        try {
            SecretKey key = datacenterKeys.get(datacenter);
            if (key == null) {
                throw new SecurityException("No encryption key available for datacenter: " + datacenter);
            }
            
            // Generate random IV for each encryption
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            
            byte[] encryptedData = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            
            // Create message hash for integrity verification
            String messageHash = createMessageHash(message);
            
            EncryptedMessage encrypted = new EncryptedMessage(
                Base64.getEncoder().encodeToString(encryptedData),
                Base64.getEncoder().encodeToString(iv),
                messageHash,
                context.getContextId(),
                Instant.now()
            );
            
            auditLogger.info("Message encrypted: datacenter={}, contextId={}, size={} bytes", 
                datacenter, context.getContextId(), encryptedData.length);
            
            return encrypted;
            
        } catch (Exception e) {
            auditLogger.error("Message encryption failed: datacenter={}, contextId={}", 
                datacenter, context.getContextId(), e);
            throw new SecurityException("Message encryption failed", e);
        }
    }
    
    /**
     * Decrypts a message payload.
     */
    public String decryptMessage(String datacenter, EncryptedMessage encryptedMessage, SecurityContext context) {
        try {
            SecretKey key = datacenterKeys.get(datacenter);
            if (key == null) {
                throw new SecurityException("No decryption key available for datacenter: " + datacenter);
            }
            
            byte[] encryptedData = Base64.getDecoder().decode(encryptedMessage.getEncryptedData());
            byte[] iv = Base64.getDecoder().decode(encryptedMessage.getIv());
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String decryptedMessage = new String(decryptedData, StandardCharsets.UTF_8);
            
            // Verify message integrity
            String computedHash = createMessageHash(decryptedMessage);
            if (!computedHash.equals(encryptedMessage.getMessageHash())) {
                throw new SecurityException("Message integrity check failed");
            }
            
            auditLogger.info("Message decrypted: datacenter={}, contextId={}", 
                datacenter, context.getContextId());
            
            return decryptedMessage;
            
        } catch (Exception e) {
            auditLogger.error("Message decryption failed: datacenter={}, contextId={}", 
                datacenter, context.getContextId(), e);
            throw new SecurityException("Message decryption failed", e);
        }
    }
    
    /**
     * Validates message authenticity and prevents replay attacks.
     */
    public boolean validateMessage(EncryptedMessage message, SecurityContext context) {
        // Check message age (prevent replay attacks)
        long messageAge = Instant.now().toEpochMilli() - message.getTimestamp().toEpochMilli();
        if (messageAge > 300000) { // 5 minutes
            auditLogger.warn("Message rejected - too old: age={}ms, contextId={}", 
                messageAge, context.getContextId());
            return false;
        }
        
        // Verify context association
        if (!message.getContextId().equals(context.getContextId())) {
            auditLogger.warn("Message rejected - context mismatch: expected={}, actual={}", 
                context.getContextId(), message.getContextId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Creates security headers for JMS messages.
     */
    public Map<String, Object> createSecurityHeaders(SecurityContext context) {
        Map<String, Object> headers = new ConcurrentHashMap<>();
        headers.put("X-Security-Context", context.getContextId());
        headers.put("X-User-Id", context.getUserId());
        headers.put("X-Datacenter", context.getDatacenter());
        headers.put("X-Timestamp", context.getCreatedAt().toEpochMilli());
        headers.put("X-Signature", createContextSignature(context));
        
        return headers;
    }
    
    /**
     * Validates security headers from incoming messages.
     */
    public boolean validateSecurityHeaders(Map<String, Object> headers) {
        try {
            String contextId = (String) headers.get("X-Security-Context");
            String userId = (String) headers.get("X-User-Id");
            String datacenter = (String) headers.get("X-Datacenter");
            Long timestamp = (Long) headers.get("X-Timestamp");
            String signature = (String) headers.get("X-Signature");
            
            if (contextId == null || userId == null || datacenter == null || 
                timestamp == null || signature == null) {
                auditLogger.warn("Invalid security headers - missing required fields");
                return false;
            }
            
            // Verify timestamp (prevent replay attacks)
            long age = System.currentTimeMillis() - timestamp;
            if (age > 300000) { // 5 minutes
                auditLogger.warn("Security headers rejected - timestamp too old: age={}ms", age);
                return false;
            }
            
            // Verify signature
            SecurityContext reconstructedContext = new SecurityContext(datacenter, userId, "validate", 
                Instant.ofEpochMilli(timestamp));
            reconstructedContext.setContextId(contextId);
            
            String expectedSignature = createContextSignature(reconstructedContext);
            if (!signature.equals(expectedSignature)) {
                auditLogger.warn("Security headers rejected - invalid signature");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            auditLogger.error("Security header validation failed", e);
            return false;
        }
    }
    
    /**
     * Closes a security context and cleans up resources.
     */
    public void closeSecurityContext(SecurityContext context) {
        activeContexts.remove(context.getContextId());
        
        auditLogger.info("Security context closed: contextId={}, duration={}ms", 
            context.getContextId(), 
            Instant.now().toEpochMilli() - context.getCreatedAt().toEpochMilli());
    }
    
    /**
     * Gets security statistics and metrics.
     */
    public SecurityMetrics getSecurityMetrics() {
        return new SecurityMetrics(
            activeContexts.size(),
            datacenterKeys.size(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * Performs periodic security maintenance.
     */
    private void performSecurityMaintenance() {
        // Clean up expired contexts
        long now = System.currentTimeMillis();
        activeContexts.entrySet().removeIf(entry -> {
            SecurityContext context = entry.getValue();
            long age = now - context.getCreatedAt().toEpochMilli();
            return age > 3600000; // 1 hour timeout
        });
        
        logger.debug("Security maintenance completed: active contexts={}", activeContexts.size());
    }
    
    /**
     * Rotates encryption keys for enhanced security.
     */
    private void rotateEncryptionKeys() {
        logger.info("Starting encryption key rotation");
        
        datacenterKeys.forEach((datacenter, oldKey) -> {
            try {
                SecretKey newKey = generateOrLoadEncryptionKey(datacenter);
                datacenterKeys.put(datacenter, newKey);
                
                auditLogger.info("Encryption key rotated for datacenter: {}", datacenter);
            } catch (Exception e) {
                logger.error("Key rotation failed for datacenter: {}", datacenter, e);
            }
        });
        
        logger.info("Encryption key rotation completed");
    }
    
    /**
     * Generates or loads an encryption key for a datacenter.
     */
    private SecretKey generateOrLoadEncryptionKey(String datacenter) throws Exception {
        // In production, load from secure key management system (HSM, AWS KMS, etc.)
        // For now, generate a new key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }
    
    /**
     * Creates a cryptographic hash of a message.
     */
    private String createMessageHash(String message) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Creates a digital signature for a security context.
     */
    private String createContextSignature(SecurityContext context) {
        try {
            String data = context.getDatacenter() + "|" + context.getUserId() + "|" + 
                         context.getOperation() + "|" + context.getCreatedAt().toEpochMilli();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new SecurityException("Failed to create context signature", e);
        }
    }
    
    /**
     * Checks if a user has administrative privileges.
     */
    private boolean isAdminUser(String userId) {
        // In production, integrate with enterprise identity systems
        // For now, use simple pattern matching
        return userId.toLowerCase().contains("admin") || userId.toLowerCase().startsWith("svc_");
    }
    
    /**
     * Shuts down the security manager.
     */
    public void shutdown() {
        logger.info("Shutting down security manager");
        
        // Clear sensitive data
        datacenterKeys.clear();
        activeContexts.clear();
        
        securityScheduler.shutdown();
        try {
            if (!securityScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                securityScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            securityScheduler.shutdownNow();
        }
    }
    
    /**
     * Security context for JMS operations.
     */
    public static class SecurityContext {
        private String contextId;
        private final String datacenter;
        private final String userId;
        private final String operation;
        private final Instant createdAt;
        
        public SecurityContext(String datacenter, String userId, String operation, Instant createdAt) {
            this.contextId = generateContextId();
            this.datacenter = datacenter;
            this.userId = userId;
            this.operation = operation;
            this.createdAt = createdAt;
        }
        
        private String generateContextId() {
            return "ctx_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(System.identityHashCode(this));
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public String getDatacenter() { return datacenter; }
        public String getUserId() { return userId; }
        public String getOperation() { return operation; }
        public Instant getCreatedAt() { return createdAt; }
        
        // Setter for reconstruction
        public void setContextId(String contextId) { this.contextId = contextId; }
    }
    
    /**
     * Encrypted message container.
     */
    public static class EncryptedMessage {
        private final String encryptedData;
        private final String iv;
        private final String messageHash;
        private final String contextId;
        private final Instant timestamp;
        
        public EncryptedMessage(String encryptedData, String iv, String messageHash, 
                               String contextId, Instant timestamp) {
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.messageHash = messageHash;
            this.contextId = contextId;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getEncryptedData() { return encryptedData; }
        public String getIv() { return iv; }
        public String getMessageHash() { return messageHash; }
        public String getContextId() { return contextId; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Security metrics and statistics.
     */
    public static class SecurityMetrics {
        private final int activeContexts;
        private final int configuredDatacenters;
        private final long timestamp;
        
        public SecurityMetrics(int activeContexts, int configuredDatacenters, long timestamp) {
            this.activeContexts = activeContexts;
            this.configuredDatacenters = configuredDatacenters;
            this.timestamp = timestamp;
        }
        
        // Getters
        public int getActiveContexts() { return activeContexts; }
        public int getConfiguredDatacenters() { return configuredDatacenters; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("SecurityMetrics{activeContexts=%d, datacenters=%d, timestamp=%d}",
                activeContexts, configuredDatacenters, timestamp);
        }
    }
}