package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 04A.3 §5 — Register the TenantAwareJpaTransactionManager as the
 * primary transaction manager.
 */
@Configuration
public class TenantTransactionManagerConfig {

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            TenantContextProvider tenantContextProvider,
            TenantRlsProperties rlsProperties) {
        return new TenantAwareJpaTransactionManager(
                entityManagerFactory, tenantContextProvider, rlsProperties);
    }
}
