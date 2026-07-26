package com.sanad.platform.crm.idempotency;

import com.sanad.platform.crm.idempotency.infrastructure.JdbcIdempotencyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** CRM API contract idempotency-service wiring. */
@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnMissingBean(IdempotencyService.class)
    public IdempotencyService idempotencyService(NamedParameterJdbcTemplate jdbc) {
        return new JdbcIdempotencyService(jdbc);
    }
}
