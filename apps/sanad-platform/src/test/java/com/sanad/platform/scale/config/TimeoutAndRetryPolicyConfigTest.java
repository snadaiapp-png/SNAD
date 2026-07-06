package com.sanad.platform.scale.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutAndRetryPolicyConfigTest {

    @Test
    void registryContainsIdempotentRetry() {
        TimeoutAndRetryPolicyConfig config = new TimeoutAndRetryPolicyConfig();
        RetryRegistry registry = config.retryRegistry();

        assertThat(registry.find(TimeoutAndRetryPolicyConfig.IDEMPOTENT_RETRY)).isPresent();
    }

    @Test
    void idempotentRetryHasThreeMaxAttempts() {
        TimeoutAndRetryPolicyConfig config = new TimeoutAndRetryPolicyConfig();
        RetryRegistry registry = config.retryRegistry();
        Retry retry = registry.retry(TimeoutAndRetryPolicyConfig.IDEMPOTENT_RETRY);

        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void idempotentRetryFailsAfterMaxAttempts() {
        TimeoutAndRetryPolicyConfig config = new TimeoutAndRetryPolicyConfig();
        RetryRegistry registry = config.retryRegistry();
        Retry retry = registry.retry(TimeoutAndRetryPolicyConfig.IDEMPOTENT_RETRY);

        // The retry should be configured to fail after max attempts
        assertThat(retry.getRetryConfig().isFailAfterMaxAttempts()).isTrue();
    }
}
