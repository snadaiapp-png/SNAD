package com.sanad.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Enables JDBC savepoint support for nested transactions (Propagation.NESTED).
 *
 * <p>Spring's default {@link JpaTransactionManager} has {@code nestedTransactionAllowed=false}
 * because not all JPA providers support savepoints reliably. Hibernate + PostgreSQL JDBC
 * driver <em>do</em> support savepoints, so we explicitly enable them here.
 *
 * <p>This is required for application methods annotated with
 * {@code @Transactional(propagation = Propagation.NESTED)} — for example, methods that
 * may legitimately fail with a constraint violation (duplicate key, etc.) inside a
 * larger transaction. Without savepoints, PostgreSQL aborts the entire transaction
 * after the constraint violation, causing all subsequent SQL in the same transaction
 * to fail with {@code ERROR: current transaction is aborted}.
 *
 * <p>H2 (used in local dev / pre-PostgreSQL CI) does NOT have this strict abort
 * behavior, so the bug was latent until CI switched to PostgreSQL.
 */
@Configuration
public class TransactionManagerConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager tm = new JpaTransactionManager(emf);
        tm.setNestedTransactionAllowed(true);
        return tm;
    }
}
