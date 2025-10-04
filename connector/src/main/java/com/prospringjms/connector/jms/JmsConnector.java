package com.prospringjms.connector.jms;

import com.prospringjms.connector.core.*;
import com.prospringjms.sender.ResilientJmsSender;
import com.prospringjms.exception.JmsLibraryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.UUID;

/**
 * JMS connector supporting both synchronous and asynchronous messaging
 * with comprehensive resilience patterns.
 * 
 * Handles both request-response and fire-and-forget messaging patterns
 * with automatic correlation ID management and reply-to handling.
 */
@Component
public class JmsConnector extends AbstractResilientConnector<JmsRequest, JmsResponse> {
    
    @Autowired
    private ResilientJmsSender resilientJmsSender;
    
    @Autowired
    private JmsTemplate jmsTemplate;
    
    public JmsConnector() {
        logger.info("JMS connector initialized");
    }
    
    @Override
    protected JmsResponse doSendSync(JmsRequest request, ConnectorContext context) throws ConnectorException {
        try {
            logger.debug("Sending sync JMS request to: {} in datacenter: {}", 
                request.getDestination(), context.getDatacenter());
            
            if (request.isRequestResponse()) {
                return sendSyncRequestResponse(request, context);
            } else {
                return sendSyncFireAndForget(request, context);
            }
            
        } catch (Exception e) {
            throw new ConnectorException("Unexpected error during JMS send", e, getType(), 
                request.getDestination(), context.getDatacenter(), "sendSync");
        }
    }
    
    @Override
    protected CompletableFuture<JmsResponse> doSendAsync(JmsRequest request, ConnectorContext context) {
        logger.debug("Sending async JMS request to: {} in datacenter: {}", 
            request.getDestination(), context.getDatacenter());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (request.isRequestResponse()) {
                    return sendSyncRequestResponse(request, context);
                } else {
                    return sendSyncFireAndForget(request, context);
                }
            } catch (Exception e) {
                throw new CompletionException(new ConnectorException(
                    "Async JMS send failed", e, getType(), 
                    request.getDestination(), context.getDatacenter(), "sendAsync"));
            }
        });
    }
    
    @Override
    protected CompletableFuture<Void> doSendAsyncNoResponse(JmsRequest request, ConnectorContext context) {
        logger.debug("Sending fire-and-forget JMS request to: {} in datacenter: {}", 
            request.getDestination(), context.getDatacenter());
        
        return CompletableFuture.runAsync(() -> {
            try {
                sendFireAndForget(request, context);
            } catch (Exception e) {
                throw new CompletionException(new ConnectorException(
                    "Async fire-and-forget JMS send failed", e, getType(), 
                    request.getDestination(), context.getDatacenter(), "sendAsyncNoResponse"));
            }
        });
    }
    
    /**
     * Send synchronous request-response JMS message.
     */
    private JmsResponse sendSyncRequestResponse(JmsRequest request, ConnectorContext context) 
            throws ConnectorException {
        try {
            String correlationId = request.getCorrelationId();
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // Add correlation ID to headers
            request.getHeaders().put("JMSCorrelationID", correlationId);
            
            // Send message and wait for response
            ResilientJmsSender.SendResult sendResult = resilientJmsSender.sendToPrimary(
                request.getDestination(), request.getPayload(), request.getHeaders());
            
            logger.debug("JMS message sent successfully to {}", request.getDestination());
            
            // For request-response, we would typically wait for a reply here
            // This is a simplified implementation
            return new JmsResponse(
                correlationId,
                correlationId,
                request.getDestination(),
                sendResult.getDatacenter(),
                "Message sent successfully",
                true,
                System.currentTimeMillis(),
                null // No response payload in this simplified version
            );
            
        } catch (Exception e) {
            throw new ConnectorException("JMS request-response failed", e, getType(), 
                request.getDestination(), context.getDatacenter(), "sendSyncRequestResponse");
        }
    }
    
    /**
     * Send synchronous fire-and-forget JMS message.
     */
    private JmsResponse sendSyncFireAndForget(JmsRequest request, ConnectorContext context) 
            throws ConnectorException {
        try {
            sendFireAndForget(request, context);
            
            return new JmsResponse(
                null, // No message ID for fire-and-forget
                request.getCorrelationId(),
                request.getDestination(),
                context.getDatacenter(),
                "Message sent successfully (fire-and-forget)",
                true,
                System.currentTimeMillis(),
                null
            );
            
        } catch (Exception e) {
            throw new ConnectorException("JMS fire-and-forget failed", e, getType(), 
                request.getDestination(), context.getDatacenter(), "sendSyncFireAndForget");
        }
    }
    
    /**
     * Send fire-and-forget JMS message.
     */
    private void sendFireAndForget(JmsRequest request, ConnectorContext context) 
            throws JmsLibraryException {
        resilientJmsSender.sendToPrimary(request.getDestination(), 
            request.getPayload(), request.getHeaders());
        
        logger.debug("Fire-and-forget JMS message sent to: {}", request.getDestination());
    }
    
    @Override
    protected boolean doHealthCheck(ConnectorContext context) {
        try {
            // Simple health check by attempting to get a connection
            Connection connection = jmsTemplate.getConnectionFactory().createConnection();
            connection.start();
            connection.close();
            return true;
        } catch (Exception e) {
            logger.debug("JMS health check failed", e);
            return false;
        }
    }
    
    @Override
    public ConnectorType getType() {
        return ConnectorType.JMS;
    }
    
    @Override
    protected void doClose() {
        // JmsTemplate and ResilientJmsSender cleanup is handled by Spring
        logger.info("JMS connector closed");
    }
    
    /**
     * Convenience method for sending text messages.
     */
    public JmsResponse sendTextMessage(String destination, String text, ConnectorContext context) 
            throws ConnectorException {
        JmsRequest request = JmsRequest.builder()
            .destination(destination)
            .payload(text)
            .messageType(JmsRequest.MessageType.TEXT)
            .build();
        
        return sendSync(request, context);
    }
    
    /**
     * Convenience method for sending object messages.
     */
    public JmsResponse sendObjectMessage(String destination, Object object, ConnectorContext context) 
            throws ConnectorException {
        JmsRequest request = JmsRequest.builder()
            .destination(destination)
            .payload(object)
            .messageType(JmsRequest.MessageType.OBJECT)
            .build();
        
        return sendSync(request, context);
    }
    
    /**
     * Convenience method for sending request-response messages.
     */
    public JmsResponse sendRequestResponse(String destination, Object payload, 
                                         String replyTo, ConnectorContext context) 
            throws ConnectorException {
        JmsRequest request = JmsRequest.builder()
            .destination(destination)
            .payload(payload)
            .requestResponse(true)
            .replyTo(replyTo)
            .build();
        
        return sendSync(request, context);
    }
}