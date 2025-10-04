package com.prospringjms.connector.core;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Abstract base class for all backend connectors providing common resilience patterns
 * using Resilience4j: Circuit Breaker, Retry, Bulkhead, Rate Limiter, and Time Limiter.
 * 
 * Implements the template method pattern for consistent connector behavior.
 */
public abstract class AbstractResilientConnector<TRequest, TResponse> implements BackendConnector<TRequest, TResponse> {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);
    
    // Resilience4j components
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Bulkhead bulkhead;
    private RateLimiter rateLimiter;
    private TimeLimiter timeLimiter;
    
    protected AbstractResilientConnector() {
        initializeResilienceComponents();
    }
    
    /**
     * Initialize resilience components with default configurations.
     * Subclasses can override to customize behavior.
     */
    protected void initializeResilienceComponents() {
        // Circuit Breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(100)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .minimumNumberOfCalls(10)
            .slidingWindowSize(20)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        
        this.circuitBreaker = CircuitBreaker.of(getType().name() + "-circuit-breaker", cbConfig);
        
        // Retry
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(this::shouldRetry)
            .build();
            
        this.retry = Retry.of(getType().name() + "-retry", retryConfig);
        
        // Bulkhead for concurrent call isolation
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(25)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build();
            
        this.bulkhead = Bulkhead.of(getType().name() + "-bulkhead", bulkheadConfig);
        
        // Rate Limiter
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(100)
            .timeoutDuration(Duration.ofSeconds(2))
            .build();
            
        this.rateLimiter = RateLimiter.of(getType().name() + "-rate-limiter", rateLimiterConfig);
        
        // Time Limiter
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(30))
            .build();
            
        this.timeLimiter = TimeLimiter.of(getType().name() + "-time-limiter", timeLimiterConfig);
        
        // Register event listeners for monitoring
        registerEventListeners();
    }
    
    /**
     * Configure resilience components based on context.
     */
    protected void configureResilience(ConnectorContext context) {
        if (context.getRetryConfig() != null) {
            configureRetry(context.getRetryConfig());
        }
        if (context.getCircuitBreakerConfig() != null) {
            configureCircuitBreaker(context.getCircuitBreakerConfig());
        }
    }
    
    private void configureRetry(ConnectorContext.RetryConfig config) {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(config.getMaxAttempts())
            .waitDuration(config.getWaitDuration())
            .retryOnException(this::shouldRetry)
            .build();
            
        this.retry = Retry.of(getType().name() + "-retry", retryConfig);
    }
    
    private void configureCircuitBreaker(ConnectorContext.CircuitBreakerConfig config) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .slidingWindowSize(config.getSlidingWindowSize())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .build();
        
        this.circuitBreaker = CircuitBreaker.of(getType().name() + "-circuit-breaker", cbConfig);
    }
    
    @Override
    public final TResponse sendSync(TRequest request, ConnectorContext context) throws ConnectorException {
        configureResilience(context);
        
        Supplier<TResponse> decoratedSupplier = decorateSupplier(() -> {
            try {
                return doSendSync(request, context);
            } catch (ConnectorException e) {
                throw new CompletionException(e);
            }
        });
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectorException) {
                throw (ConnectorException) e.getCause();
            }
            throw new ConnectorException("Failed to send sync request", e, getType(), 
                context.getEndpoint(), context.getDatacenter(), "sendSync");
        }
    }
    
    @Override
    public final CompletableFuture<TResponse> sendAsync(TRequest request, ConnectorContext context) {
        configureResilience(context);
        
        Supplier<CompletableFuture<TResponse>> decoratedSupplier = decorateAsyncSupplier(() -> 
            doSendAsync(request, context)
        );
        
        return decoratedSupplier.get()
            .exceptionally(throwable -> {
                logger.error("Async request failed for endpoint: {} in datacenter: {}", 
                    context.getEndpoint(), context.getDatacenter(), throwable);
                throw new CompletionException(new ConnectorException(
                    "Failed to send async request", throwable, getType(), 
                    context.getEndpoint(), context.getDatacenter(), "sendAsync"));
            });
    }
    
    @Override
    public final CompletableFuture<Void> sendAsyncNoResponse(TRequest request, ConnectorContext context) {
        configureResilience(context);
        
        Supplier<CompletableFuture<Void>> decoratedSupplier = decorateAsyncSupplier(() -> 
            doSendAsyncNoResponse(request, context)
        );
        
        return decoratedSupplier.get()
            .exceptionally(throwable -> {
                logger.error("Async fire-and-forget request failed for endpoint: {} in datacenter: {}", 
                    context.getEndpoint(), context.getDatacenter(), throwable);
                throw new CompletionException(new ConnectorException(
                    "Failed to send async no-response request", throwable, getType(), 
                    context.getEndpoint(), context.getDatacenter(), "sendAsyncNoResponse"));
            });
    }
    
    @Override
    public final boolean isHealthy(ConnectorContext context) {
        try {
            return circuitBreaker.getState() == CircuitBreaker.State.CLOSED && doHealthCheck(context);
        } catch (Exception e) {
            logger.warn("Health check failed for {}: {}", getType(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Decorate supplier with all resilience patterns for sync calls.
     */
    private Supplier<TResponse> decorateSupplier(Supplier<TResponse> supplier) {
        Supplier<TResponse> decorated = supplier;
        decorated = Bulkhead.decorateSupplier(bulkhead, decorated);
        decorated = RateLimiter.decorateSupplier(rateLimiter, decorated);
        decorated = Retry.decorateSupplier(retry, decorated);
        decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        return decorated;
    }
    
    /**
     * Decorate supplier with all resilience patterns for async calls.
     */
    private <T> Supplier<CompletableFuture<T>> decorateAsyncSupplier(Supplier<CompletableFuture<T>> supplier) {
        Supplier<CompletableFuture<T>> decorated = supplier;
        decorated = Bulkhead.decorateSupplier(bulkhead, decorated);
        decorated = RateLimiter.decorateSupplier(rateLimiter, decorated);
        decorated = Retry.decorateSupplier(retry, decorated);
        decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        return decorated;
    }
    
    /**
     * Register event listeners for monitoring and logging.
     */
    private void registerEventListeners() {
        // Circuit Breaker events
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                logger.info("{} Circuit breaker state transition: {} -> {}", 
                    getType(), event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState()))
            .onFailureRateExceeded(event -> 
                logger.warn("{} Circuit breaker failure rate exceeded: {}%", 
                    getType(), event.getFailureRate()))
            .onSlowCallRateExceeded(event -> 
                logger.warn("{} Circuit breaker slow call rate exceeded: {}%", 
                    getType(), event.getSlowCallRate()));
        
        // Retry events
        retry.getEventPublisher()
            .onRetry(event -> 
                logger.info("{} Retry attempt {} for: {}", 
                    getType(), event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
            .onSuccess(event -> 
                logger.debug("{} Retry succeeded after {} attempts", 
                    getType(), event.getNumberOfRetryAttempts()));
        
        // Bulkhead events
        bulkhead.getEventPublisher()
            .onCallRejected(event -> 
                logger.warn("{} Bulkhead call rejected", getType()))
            .onCallFinished(event -> 
                logger.debug("{} Bulkhead call finished", getType()));
        
        // Rate Limiter events
        rateLimiter.getEventPublisher()
            .onFailure(event -> 
                logger.warn("{} Rate limiter call rejected", getType()));
    }
    
    /**
     * Determines whether an exception should trigger a retry.
     * Subclasses can override for custom retry logic.
     */
    protected boolean shouldRetry(Throwable throwable) {
        // Default: retry on ConnectorException but not on validation errors
        if (throwable instanceof ConnectorException) {
            ConnectorException ce = (ConnectorException) throwable;
            return !ce.getMessage().toLowerCase().contains("validation") && 
                   !ce.getMessage().toLowerCase().contains("authentication");
        }
        return true; // Retry on other exceptions
    }
    
    // Abstract methods to be implemented by concrete connectors
    
    /**
     * Perform the actual synchronous request.
     */
    protected abstract TResponse doSendSync(TRequest request, ConnectorContext context) throws ConnectorException;
    
    /**
     * Perform the actual asynchronous request.
     */
    protected abstract CompletableFuture<TResponse> doSendAsync(TRequest request, ConnectorContext context);
    
    /**
     * Perform the actual asynchronous fire-and-forget request.
     */
    protected abstract CompletableFuture<Void> doSendAsyncNoResponse(TRequest request, ConnectorContext context);
    
    /**
     * Perform health check on the backend service.
     */
    protected abstract boolean doHealthCheck(ConnectorContext context);
    
    /**
     * Get metrics and status information.
     */
    public ConnectorMetrics getMetrics() {
        return new ConnectorMetrics(
            getType(),
            circuitBreaker.getState(),
            circuitBreaker.getMetrics(),
            retry.getMetrics(),
            bulkhead.getMetrics(),
            rateLimiter.getMetrics()
        );
    }
    
    @Override
    public void close() {
        logger.info("Shutting down {} connector", getType());
        try {
            executorService.shutdown();
            doClose();
        } catch (Exception e) {
            logger.error("Error during connector shutdown", e);
        }
    }
    
    /**
     * Perform connector-specific cleanup.
     */
    protected abstract void doClose();
    
    /**
     * Metrics container for connector monitoring.
     */
    public static class ConnectorMetrics {
        private final ConnectorType type;
        private final CircuitBreaker.State circuitBreakerState;
        private final CircuitBreaker.Metrics circuitBreakerMetrics;
        private final Retry.Metrics retryMetrics;
        private final Bulkhead.Metrics bulkheadMetrics;
        private final RateLimiter.Metrics rateLimiterMetrics;
        
        public ConnectorMetrics(ConnectorType type, CircuitBreaker.State circuitBreakerState,
                              CircuitBreaker.Metrics circuitBreakerMetrics, Retry.Metrics retryMetrics,
                              Bulkhead.Metrics bulkheadMetrics, RateLimiter.Metrics rateLimiterMetrics) {
            this.type = type;
            this.circuitBreakerState = circuitBreakerState;
            this.circuitBreakerMetrics = circuitBreakerMetrics;
            this.retryMetrics = retryMetrics;
            this.bulkheadMetrics = bulkheadMetrics;
            this.rateLimiterMetrics = rateLimiterMetrics;
        }
        
        // Getters
        public ConnectorType getType() { return type; }
        public CircuitBreaker.State getCircuitBreakerState() { return circuitBreakerState; }
        public CircuitBreaker.Metrics getCircuitBreakerMetrics() { return circuitBreakerMetrics; }
        public Retry.Metrics getRetryMetrics() { return retryMetrics; }
        public Bulkhead.Metrics getBulkheadMetrics() { return bulkheadMetrics; }
        public RateLimiter.Metrics getRateLimiterMetrics() { return rateLimiterMetrics; }
    }
}