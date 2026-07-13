package com.sanad.platform.crm.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * CRM API Contract — Idempotency Service wiring.
 * <p>
 * In production (when a {@link NamedParameterJdbcTemplate} is available
 * and the {@code crm_idempotency_records} table exists), the
 * {@link JdbcIdempotencyService} bean is registered. In unit/contract
 * tests where no DB is available, the {@link IdempotencyService.InMemoryIdempotencyService}
 * fallback is registered.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnMissingBean(IdempotencyService.class)
    public IdempotencyService idempotencyService(NamedParameterJdbcTemplate jdbc) {
        return new JdbcIdempotencyService(jdbc);
    }
}
