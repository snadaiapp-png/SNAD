package com.sanad.platform.scale.config;

import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Stage 08 Sprint 1 — ST8-S1-007 Timeout and Retry Policy.
 *
 * Configures retry policies for idempotent downstream calls:
 *   - maxAttempts: 3 (initial + 2 retries)
 *   - exponential backoff: 1s, 2s, 4s
 *   - retryOnExceptions: transient failures only (caller must mark
 *     operations idempotent via annotation)
 *
 * Non-idempotent operations MUST NOT use retry. The caller is
 * responsible for ensuring idempotency before applying @Retry.
 *
 * Timeouts are enforced at multiple layers:
 *   - API gateway: 30s (server.tomcat.connection-timeout)
 *   - Internal service: 10s (per-call @Timeout or RestTemplate setReadTimeout)
 *   - DB query: 5s (Hikari validationTimeout + statement_timeout)
 *   - AI inference: 60s (circuit breaker slowCallDurationThreshold)
 *   - Email send: 10s (circuit breaker slowCallDurationThreshold)
 *   - Webhook delivery: 5s (circuit breaker slowCallDurationThreshold)
 *
 * Observability: Resilience4j retry metrics auto-published to micrometer.
 */
@Configuration
public class TimeoutAndRetryPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeoutAndRetryPolicyConfig.class);

    public static final String IDEMPOTENT_RETRY = "idempotent";

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig idempotentConfig = RetryConfig.custom()
                .maxAttempts(3)                                              // initial + 2 retries
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                        Duration.ofSeconds(1), 2.0))                         // 1s, 2s, 4s
                .retryOnException(throwable -> {
                    // Retry only on transient failures
                    String name = throwable.getClass().getSimpleName();
                    return name.contains("Timeout")
                            || name.contains("IOException")
                            || name.contains("Transient")
                            || name.contains("ResourceAccessException");
                })
                .failAfterMaxAttempts(true)
                .build();

        RetryRegistry registry = RetryRegistry.ofDefaults();
        registry.addConfiguration(IDEMPOTENT_RETRY, idempotentConfig);
        registry.retry(IDEMPOTENT_RETRY, idempotentConfig);

        log.info("Timeout and retry policy configured: idempotent retry (maxAttempts=3, backoff=1s/2s/4s)");
        return registry;
    }
}
