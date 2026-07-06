package com.sanad.platform.scale.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerPolicyConfigTest {

    @Test
    void registryContainsAllFiveBreakers() {
        CircuitBreakerPolicyConfig config = new CircuitBreakerPolicyConfig();
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry();

        assertThat(registry.find(CircuitBreakerPolicyConfig.DB_BREAKER)).isPresent();
        assertThat(registry.find(CircuitBreakerPolicyConfig.REDIS_BREAKER)).isPresent();
        assertThat(registry.find(CircuitBreakerPolicyConfig.AI_INFERENCE_BREAKER)).isPresent();
        assertThat(registry.find(CircuitBreakerPolicyConfig.EMAIL_PROVIDER_BREAKER)).isPresent();
        assertThat(registry.find(CircuitBreakerPolicyConfig.WEBHOOK_DELIVERY_BREAKER)).isPresent();
    }

    @Test
    void databaseBreakerOpensAfterFiveFailures() {
        CircuitBreakerPolicyConfig config = new CircuitBreakerPolicyConfig();
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry();
        CircuitBreaker db = registry.circuitBreaker(CircuitBreakerPolicyConfig.DB_BREAKER);

        // Initial state is closed
        assertThat(db.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Record 5 failures (minimumNumberOfCalls=5, failureRateThreshold=50%)
        for (int i = 0; i < 5; i++) {
            db.onError(0, new RuntimeException("test failure"));
        }

        // After 5 failures (100% failure rate > 50% threshold), breaker should open
        assertThat(db.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void aiInferenceBreakerHasSlowerWaitDuration() {
        CircuitBreakerPolicyConfig config = new CircuitBreakerPolicyConfig();
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry();
        CircuitBreaker ai = registry.circuitBreaker(CircuitBreakerPolicyConfig.AI_INFERENCE_BREAKER);

        // AI breaker should wait 60s in open state (vs 30s for DB)
        CircuitBreakerConfig aiConfig = ai.getCircuitBreakerConfig();
        assertThat(aiConfig.getWaitDurationInOpenState().getSeconds()).isEqualTo(60);
    }

    @Test
    void allBreakersPermitTenHalfOpenCalls() {
        CircuitBreakerPolicyConfig config = new CircuitBreakerPolicyConfig();
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry();

        for (String name : new String[]{
                CircuitBreakerPolicyConfig.DB_BREAKER,
                CircuitBreakerPolicyConfig.REDIS_BREAKER,
                CircuitBreakerPolicyConfig.AI_INFERENCE_BREAKER,
                CircuitBreakerPolicyConfig.EMAIL_PROVIDER_BREAKER,
                CircuitBreakerPolicyConfig.WEBHOOK_DELIVERY_BREAKER}) {
            CircuitBreaker breaker = registry.circuitBreaker(name);
            assertThat(breaker.getCircuitBreakerConfig()
                    .getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(10);
        }
    }
}
