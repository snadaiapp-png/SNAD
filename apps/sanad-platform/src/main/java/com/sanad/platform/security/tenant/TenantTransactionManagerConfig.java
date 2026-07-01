package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 04A.1 §5 — Register the TenantAwareJpaTransactionManager as the
 * primary transaction manager.
 *
 * <p>This ensures every {@code @Transactional} method uses the tenant-aware
 * manager, which automatically executes
 * {@code SELECT set_config('app.current_tenant_id', ?, true)} on the
 * transaction's connection before any query runs.</p>
 *
 * <p>The default {@link JpaTransactionManager} is replaced — Spring Boot's
 * auto-configuration backs off when a primary {@code PlatformTransactionManager}
 * bean is declared explicitly.</p>
 */
@Configuration
public class TenantTransactionManagerConfig {

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            TenantContextProvider tenantContextProvider) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory, tenantContextProvider);
    }
}
