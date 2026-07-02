package com.sanad.platform.security.tenant.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Stage 05A.2.3 §2 — Explicit Runtime DataSource for PostgreSQL integration tests.
 *
 * <p>Provides a DataSource that connects as {@code sanad_runtime_app} (NOSUPERUSER,
 * NOBYPASSRLS). This is the DataSource used by:</p>
 * <ul>
 *   <li>JPA EntityManagerFactory</li>
 *   <li>TenantAwareJpaTransactionManager</li>
 *   <li>PostgresIdempotencyReservationStore</li>
 *   <li>OrganizationRepository (via JPA)</li>
 *   <li>AuditService (via JPA)</li>
 *   <li>Idempotent transaction executor</li>
 * </ul>
 *
 * <p>RLS tests inject this via {@code @Qualifier("tenantRuntimeDataSource")} to
 * verify that the runtime role is subject to RLS.</p>
 *
 * <p>Activated when {@code DATABASE_USERNAME} is set (CI).</p>
 */
@TestConfiguration
@ConditionalOnProperty(name = "database.username")
public class TenantRuntimeDataSourceConfig {

    @Bean(name = {"dataSource", "tenantRuntimeDataSource"})
    @Primary
    public DataSource tenantRuntimeDataSource(Environment env) {
        String url = env.getProperty("database.url",
                env.getProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/sanad_tenant_isolation"));
        String username = env.getProperty("database.username",
                env.getProperty("DATABASE_USERNAME", "sanad_runtime_app"));
        String password = env.getProperty("database.password",
                env.getProperty("DATABASE_PASSWORD", "ci-runtime-only"));
        String driver = env.getProperty("database.driver",
                env.getProperty("DATABASE_DRIVER", "org.postgresql.Driver"));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("TenantRuntimeCP");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        return ds;
    }
}
