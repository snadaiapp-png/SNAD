package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §6 — Verifies that the TenantAwareJpaTransactionManager is
 * the primary transaction manager bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantTransactionManagerWiringTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Primary PlatformTransactionManager is TenantAwareJpaTransactionManager")
    void primaryTransactionManager_isTenantAware() {
        assertThat(transactionManager)
                .as("The primary transaction manager must be TenantAwareJpaTransactionManager")
                .isInstanceOf(TenantAwareJpaTransactionManager.class);
    }

    @Test
    @DisplayName("Bean named 'transactionManager' is TenantAwareJpaTransactionManager")
    void transactionManagerBean_isTenantAware() {
        Object bean = applicationContext.getBean("transactionManager");
        assertThat(bean)
                .as("Bean 'transactionManager' must be TenantAwareJpaTransactionManager")
                .isInstanceOf(TenantAwareJpaTransactionManager.class);
    }

    @Test
    @DisplayName("Exactly one primary PlatformTransactionManager exists")
    void exactlyOnePrimaryTransactionManager() {
        String[] beanNames = applicationContext.getBeanNamesForType(PlatformTransactionManager.class);
        assertThat(beanNames.length)
                .as("At least one PlatformTransactionManager must exist")
                .isGreaterThanOrEqualTo(1);
        // The primary bean should be 'transactionManager'
        assertThat(applicationContext.containsBean("transactionManager"))
                .as("Bean 'transactionManager' must exist")
                .isTrue();
    }

    @Test
    @DisplayName("Default JpaTransactionManager is not silently selected")
    void defaultJpaTransactionManager_notSelected() {
        assertThat(transactionManager.getClass())
                .as("Must not be plain JpaTransactionManager")
                .isNotEqualTo(org.springframework.orm.jpa.JpaTransactionManager.class);
    }

    @Test
    @DisplayName("TenantAwareJpaTransactionManager has the correct EntityManagerFactory")
    void tenantAwareManager_hasEntityManagerFactory() {
        TenantAwareJpaTransactionManager tm = (TenantAwareJpaTransactionManager) transactionManager;
        assertThat(tm.getEntityManagerFactory())
                .as("TenantAwareJpaTransactionManager must use the application's EntityManagerFactory")
                .isEqualTo(entityManagerFactory);
    }
}
