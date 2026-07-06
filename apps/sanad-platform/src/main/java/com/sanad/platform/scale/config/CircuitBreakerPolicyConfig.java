package com.sanad.platform.scale.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Stage 08 Sprint 1 — ST8-S1-006 Circuit Breaker Policy.
 *
 * Configures circuit breakers for downstream service calls:
 *   - database: open on 5 errors/30s, half-open after 30s
 *   - redis:    open on 10 errors/30s, half-open after 30s
 *   - aiInference: open on 3 timeouts/60s, half-open after 60s
 *   - emailProvider: open on 5 5xx/30s, half-open after 60s
 *   - webhookDelivery: per-endpoint breaker (open on 5 errors/30s, half-open after 30s)
 *
 * Half-open: 10 trial calls; close on success, re-open on failure.
 *
 * Observability: Resilience4j metrics are auto-published to micrometer
 * via resilience4j-micrometer. Alert on breaker state transition.
 *
 * Tenant isolation: per-tenant breaker state is the responsibility of
 * the caller (ST8-S1-004 Noisy-Neighbor Protection). This config
 * defines the global policy.
 */
@Configuration
public class CircuitBreakerPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerPolicyConfig.class);

    public static final String DB_BREAKER = "database";
    public static final String REDIS_BREAKER = "redis";
    public static final String AI_INFERENCE_BREAKER = "aiInference";
    public static final String EMAIL_PROVIDER_BREAKER = "emailProvider";
    public static final String WEBHOOK_DELIVERY_BREAKER = "webhookDelivery";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)              // 50% failures trigger open
                .slowCallRateThreshold(80.0f)             // 80% slow calls trigger open
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .minimumNumberOfCalls(5)
                .slidingWindowSize(30)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();

        CircuitBreakerConfig redisConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(33.3f)              // ~10 of 30 = 33%
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .minimumNumberOfCalls(30)
                .slidingWindowSize(30)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();

        CircuitBreakerConfig aiConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(10.0f)              // 3 of 30 = 10%
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(60))
                .minimumNumberOfCalls(30)
                .slidingWindowSize(60)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();

        CircuitBreakerConfig emailConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(16.7f)              // 5 of 30 = 16.7%
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .minimumNumberOfCalls(30)
                .slidingWindowSize(30)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();

        CircuitBreakerConfig webhookConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(16.7f)
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .minimumNumberOfCalls(30)
                .slidingWindowSize(30)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.addConfiguration(DB_BREAKER, dbConfig);
        registry.addConfiguration(REDIS_BREAKER, redisConfig);
        registry.addConfiguration(AI_INFERENCE_BREAKER, aiConfig);
        registry.addConfiguration(EMAIL_PROVIDER_BREAKER, emailConfig);
        registry.addConfiguration(WEBHOOK_DELIVERY_BREAKER, webhookConfig);

        // Pre-create the breakers
        registry.circuitBreaker(DB_BREAKER, dbConfig);
        registry.circuitBreaker(REDIS_BREAKER, redisConfig);
        registry.circuitBreaker(AI_INFERENCE_BREAKER, aiConfig);
        registry.circuitBreaker(EMAIL_PROVIDER_BREAKER, emailConfig);
        registry.circuitBreaker(WEBHOOK_DELIVERY_BREAKER, webhookConfig);

        log.info("Circuit breaker policy configured: 5 breakers (database, redis, aiInference, emailProvider, webhookDelivery)");
        return registry;
    }
}
