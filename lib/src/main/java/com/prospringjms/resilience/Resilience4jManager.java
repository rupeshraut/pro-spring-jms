package com.prospringjms.resilience;

import com.prospringjms.config.JmsLibraryProperties;
import com.prospringjms.exception.JmsLibraryException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Production-grade resilience manager using Resilience4j library.
 * Provides circuit breaker, retry, rate limiting, bulkhead, and time limiting patterns.
 */
@Component
public class Resilience4jManager {
    
    private static final Logger logger = LoggerFactory.getLogger(Resilience4jManager.class);
    
    private final JmsLibraryProperties properties;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final Map<String, TimeLimiter> timeLimiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    
    public Resilience4jManager(JmsLibraryProperties properties) {
        this.properties = properties;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Resilience4j-Manager");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("Resilience4j Manager initialized with enterprise-grade resilience patterns");
    }
    
    /**
     * Gets or creates a circuit breaker for the specified datacenter.
     */
    public CircuitBreaker getCircuitBreaker(String datacenter) {
        return circuitBreakers.computeIfAbsent(datacenter, this::createCircuitBreaker);
    }
    
    /**
     * Gets or creates a retry mechanism for the specified datacenter.
     */
    public Retry getRetry(String datacenter) {
        return retries.computeIfAbsent(datacenter, this::createRetry);
    }
    
    /**
     * Gets or creates a rate limiter for the specified datacenter.
     */
    public RateLimiter getRateLimiter(String datacenter) {
        return rateLimiters.computeIfAbsent(datacenter, this::createRateLimiter);
    }
    
    /**
     * Gets or creates a bulkhead for the specified datacenter.
     */
    public Bulkhead getBulkhead(String datacenter) {
        return bulkheads.computeIfAbsent(datacenter, this::createBulkhead);
    }
    
    /**
     * Gets or creates a time limiter for the specified datacenter.
     */
    public TimeLimiter getTimeLimiter(String datacenter) {
        return timeLimiters.computeIfAbsent(datacenter, this::createTimeLimiter);
    }
    
    /**
     * Executes an operation with complete resilience protection (circuit breaker + retry + rate limiter + bulkhead).
     */
    public <T> T executeWithFullResilience(String datacenter, Supplier<T> operation) throws JmsLibraryException {
        CircuitBreaker circuitBreaker = getCircuitBreaker(datacenter);
        Retry retry = getRetry(datacenter);
        RateLimiter rateLimiter = getRateLimiter(datacenter);
        Bulkhead bulkhead = getBulkhead(datacenter);
        
        // Compose all resilience patterns
        Supplier<T> decoratedSupplier = Bulkhead.decorateSupplier(bulkhead,
            RateLimiter.decorateSupplier(rateLimiter,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, operation))));
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.error("Resilience-protected operation failed for datacenter: {}", datacenter, e);
            throw e instanceof JmsLibraryException ? 
                (JmsLibraryException) e : 
                new JmsLibraryException("Resilience operation failed", datacenter, "resilience", e);
        }
    }
    
    /**
     * Executes an async operation with complete resilience protection.
     */
    public <T> CompletableFuture<T> executeAsyncWithFullResilience(String datacenter, Supplier<CompletableFuture<T>> operation) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(datacenter);
        Retry retry = getRetry(datacenter);
        
        // Compose async resilience patterns
        Supplier<CompletableFuture<T>> decoratedSupplier = 
            CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, operation));
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.error("Async resilience operation failed for datacenter: {}", datacenter, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Executes an operation with circuit breaker protection only.
     */
    public <T> T executeWithCircuitBreaker(String datacenter, Supplier<T> operation) throws JmsLibraryException {
        CircuitBreaker circuitBreaker = getCircuitBreaker(datacenter);
        
        try {
            return circuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            throw e instanceof JmsLibraryException ? 
                (JmsLibraryException) e : 
                new JmsLibraryException("Circuit breaker operation failed", datacenter, "circuit-breaker", e);
        }
    }
    
    /**
     * Gets resilience metrics for all datacenters.
     */
    public ResilienceMetrics getResilienceMetrics() {
        ResilienceMetrics metrics = new ResilienceMetrics();
        
        circuitBreakers.forEach((datacenter, cb) -> {
            CircuitBreaker.Metrics cbMetrics = cb.getMetrics();
            metrics.addCircuitBreakerMetrics(datacenter, new CircuitBreakerMetrics(
                cb.getState().toString(),
                cbMetrics.getNumberOfSuccessfulCalls(),
                cbMetrics.getNumberOfFailedCalls(),
                cbMetrics.getFailureRate(),
                cbMetrics.getSlowCallRate()
            ));
        });
        
        retries.forEach((datacenter, retry) -> {
            Retry.Metrics retryMetrics = retry.getMetrics();
            metrics.addRetryMetrics(datacenter, new RetryMetrics(
                retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt(),
                retryMetrics.getNumberOfFailedCallsWithRetryAttempt()
            ));
        });
        
        rateLimiters.forEach((datacenter, rateLimiter) -> {
            RateLimiter.Metrics rlMetrics = rateLimiter.getMetrics();
            metrics.addRateLimiterMetrics(datacenter, new RateLimiterMetrics(
                rlMetrics.getAvailablePermissions(),
                rlMetrics.getNumberOfWaitingThreads()
            ));
        });
        
        return metrics;
    }
    
    /**
     * Creates a circuit breaker with configuration from properties.
     */
    private CircuitBreaker createCircuitBreaker(String datacenter) {
        JmsLibraryProperties.ResiliencyConfig.CircuitBreaker cbConfig = 
            properties.getResiliency() != null ? properties.getResiliency().getCircuitBreaker() : null;
        
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(cbConfig != null && cbConfig.getFailureThreshold() != null ? 
                cbConfig.getFailureThreshold() * 10 : 50) // Convert to percentage
            .waitDurationInOpenState(Duration.ofMillis(cbConfig != null && cbConfig.getResetTimeoutMs() != null ? 
                cbConfig.getResetTimeoutMs() : 60000))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofMillis(cbConfig != null && cbConfig.getTimeoutMs() != null ? 
                cbConfig.getTimeoutMs() : 10000))
            .build();
        
        CircuitBreaker circuitBreaker = CircuitBreaker.of(datacenter + "-circuit-breaker", config);
        
        // Register event listeners
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                logger.info("Circuit breaker state transition for {}: {} -> {}", 
                    datacenter, event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
            .onCallNotPermitted(event -> 
                logger.warn("Circuit breaker call not permitted for {}", datacenter))
            .onError(event -> 
                logger.debug("Circuit breaker recorded error for {}: {}", datacenter, event.getThrowable().getMessage()))
            .onSuccess(event -> 
                logger.debug("Circuit breaker recorded success for {} (duration: {}ms)", 
                    datacenter, event.getElapsedDuration().toMillis()));
        
        return circuitBreaker;
    }
    
    /**
     * Creates a retry mechanism with configuration from properties.
     */
    private Retry createRetry(String datacenter) {
        JmsLibraryProperties.ResiliencyConfig.Retry retryConfig = 
            properties.getResiliency() != null ? properties.getResiliency().getRetry() : null;
        
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(retryConfig != null && retryConfig.getMaxAttempts() != null ? 
                retryConfig.getMaxAttempts() : 3)
            .waitDuration(Duration.ofMillis(retryConfig != null && retryConfig.getInitialDelayMs() != null ? 
                retryConfig.getInitialDelayMs() : 1000))
            .retryOnException(throwable -> {
                if (retryConfig != null && retryConfig.getRetryableExceptions() != null) {
                    return retryConfig.getRetryableExceptions().stream()
                        .anyMatch(exceptionName -> throwable.getClass().getName().contains(exceptionName));
                }
                return throwable instanceof JmsLibraryException || 
                       throwable.getClass().getName().contains("JMSException");
            })
            .build();
        
        Retry retry = Retry.of(datacenter + "-retry", config);
        
        // Register event listeners
        retry.getEventPublisher()
            .onRetry(event -> 
                logger.warn("Retry attempt {} for {} after exception: {}", 
                    event.getNumberOfRetryAttempts(), datacenter, event.getLastThrowable().getMessage()))
            .onSuccess(event -> 
                logger.debug("Retry successful for {} after {} attempts", 
                    datacenter, event.getNumberOfRetryAttempts()))
            .onError(event -> 
                logger.error("Retry failed for {} after {} attempts: {}", 
                    datacenter, event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        
        return retry;
    }
    
    /**
     * Creates a rate limiter with configuration from properties.
     */
    private RateLimiter createRateLimiter(String datacenter) {
        JmsLibraryProperties.ResiliencyConfig.RateLimiting rlConfig = 
            properties.getResiliency() != null ? properties.getResiliency().getRateLimiting() : null;
        
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMillis(rlConfig != null && rlConfig.getWindowSizeMs() != null ? 
                rlConfig.getWindowSizeMs() : 1000))
            .limitForPeriod(rlConfig != null && rlConfig.getMaxRequestsPerSecond() != null ? 
                rlConfig.getMaxRequestsPerSecond() : 1000)
            .timeoutDuration(Duration.ofSeconds(5))
            .build();
        
        RateLimiter rateLimiter = RateLimiter.of(datacenter + "-rate-limiter", config);
        
        // Register event listeners
        rateLimiter.getEventPublisher()
            .onSuccess(event -> 
                logger.debug("Rate limiter permitted call for {}", datacenter))
            .onFailure(event -> 
                logger.warn("Rate limiter rejected call for {}", datacenter));
        
        return rateLimiter;
    }
    
    /**
     * Creates a bulkhead with configuration from properties.
     */
    private Bulkhead createBulkhead(String datacenter) {
        JmsLibraryProperties.ResiliencyConfig.BulkHead bulkheadConfig = 
            properties.getResiliency() != null ? properties.getResiliency().getBulkHead() : null;
        
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(bulkheadConfig != null && bulkheadConfig.getMaxConcurrentCalls() != null ? 
                bulkheadConfig.getMaxConcurrentCalls() : 100)
            .maxWaitDuration(Duration.ofMillis(bulkheadConfig != null && bulkheadConfig.getMaxWaitMs() != null ? 
                bulkheadConfig.getMaxWaitMs() : 5000))
            .build();
        
        Bulkhead bulkhead = Bulkhead.of(datacenter + "-bulkhead", config);
        
        // Register event listeners
        bulkhead.getEventPublisher()
            .onCallPermitted(event -> 
                logger.debug("Bulkhead permitted call for {}", datacenter))
            .onCallRejected(event -> 
                logger.warn("Bulkhead rejected call for {}", datacenter))
            .onCallFinished(event -> 
                logger.debug("Bulkhead call finished for {}", datacenter));
        
        return bulkhead;
    }
    
    /**
     * Creates a time limiter with default configuration.
     */
    private TimeLimiter createTimeLimiter(String datacenter) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(30))
            .cancelRunningFuture(true)
            .build();
        
        return TimeLimiter.of(datacenter + "-time-limiter", config);
    }
    
    /**
     * Shuts down the resilience manager.
     */
    public void shutdown() {
        logger.info("Shutting down Resilience4j Manager");
        scheduler.shutdown();
    }
    
    // Metrics classes
    public static class ResilienceMetrics {
        private final Map<String, CircuitBreakerMetrics> circuitBreakerMetrics = new ConcurrentHashMap<>();
        private final Map<String, RetryMetrics> retryMetrics = new ConcurrentHashMap<>();
        private final Map<String, RateLimiterMetrics> rateLimiterMetrics = new ConcurrentHashMap<>();
        
        public void addCircuitBreakerMetrics(String datacenter, CircuitBreakerMetrics metrics) {
            circuitBreakerMetrics.put(datacenter, metrics);
        }
        
        public void addRetryMetrics(String datacenter, RetryMetrics metrics) {
            retryMetrics.put(datacenter, metrics);
        }
        
        public void addRateLimiterMetrics(String datacenter, RateLimiterMetrics metrics) {
            rateLimiterMetrics.put(datacenter, metrics);
        }
        
        public Map<String, CircuitBreakerMetrics> getCircuitBreakerMetrics() { return circuitBreakerMetrics; }
        public Map<String, RetryMetrics> getRetryMetrics() { return retryMetrics; }
        public Map<String, RateLimiterMetrics> getRateLimiterMetrics() { return rateLimiterMetrics; }
    }
    
    public static class CircuitBreakerMetrics {
        private final String state;
        private final long successfulCalls;
        private final long failedCalls;
        private final float failureRate;
        private final float slowCallRate;
        
        public CircuitBreakerMetrics(String state, long successfulCalls, long failedCalls, 
                                   float failureRate, float slowCallRate) {
            this.state = state;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.failureRate = failureRate;
            this.slowCallRate = slowCallRate;
        }
        
        // Getters
        public String getState() { return state; }
        public long getSuccessfulCalls() { return successfulCalls; }
        public long getFailedCalls() { return failedCalls; }
        public float getFailureRate() { return failureRate; }
        public float getSlowCallRate() { return slowCallRate; }
    }
    
    public static class RetryMetrics {
        private final long successfulCallsWithoutRetry;
        private final long successfulCallsWithRetry;
        private final long failedCallsWithoutRetry;
        private final long failedCallsWithRetry;
        
        public RetryMetrics(long successfulCallsWithoutRetry, long successfulCallsWithRetry,
                           long failedCallsWithoutRetry, long failedCallsWithRetry) {
            this.successfulCallsWithoutRetry = successfulCallsWithoutRetry;
            this.successfulCallsWithRetry = successfulCallsWithRetry;
            this.failedCallsWithoutRetry = failedCallsWithoutRetry;
            this.failedCallsWithRetry = failedCallsWithRetry;
        }
        
        // Getters
        public long getSuccessfulCallsWithoutRetry() { return successfulCallsWithoutRetry; }
        public long getSuccessfulCallsWithRetry() { return successfulCallsWithRetry; }
        public long getFailedCallsWithoutRetry() { return failedCallsWithoutRetry; }
        public long getFailedCallsWithRetry() { return failedCallsWithRetry; }
    }
    
    public static class RateLimiterMetrics {
        private final int availablePermissions;
        private final int waitingThreads;
        
        public RateLimiterMetrics(int availablePermissions, int waitingThreads) {
            this.availablePermissions = availablePermissions;
            this.waitingThreads = waitingThreads;
        }
        
        // Getters
        public int getAvailablePermissions() { return availablePermissions; }
        public int getWaitingThreads() { return waitingThreads; }
    }
}