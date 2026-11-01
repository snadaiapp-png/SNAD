package com.sanad.platform.crm.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * CRM API Contract — Idempotency Service wiring.
 * <p>
 * In production (when a {@link NamedParameterJdbcTemplate} is available
 * and the {@code crm_idempotency_records} table exists), the
 * {@link JdbcIdempotencyService} bean is registered. In unit/contract
 * tests where no DB is available, the {@link IdempotencyService.InMemoryIdempotencyService}
 * fallback is registered.
 * <p>
 * The transaction manager is required: {@code crm_idempotency_records} is a
 * fail-closed FORCE RLS table (V20261029_1) and the service runs at controller
 * level outside any existing transaction, so each operation opens its own
 * transaction-local tenant scope.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnMissingBean(IdempotencyService.class)
    public IdempotencyService idempotencyService(NamedParameterJdbcTemplate jdbc,
                                                 PlatformTransactionManager transactionManager) {
        return new JdbcIdempotencyService(jdbc, transactionManager);
    }
}
