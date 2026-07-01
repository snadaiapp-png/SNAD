package com.sanad.platform.security.tenant.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Stage 04A.3.3 §3 — Test-only DataSource for migration-owner verification.
 *
 * <p>Uses the migration_owner account to prove that FORCE RLS applies
 * even to the table owner during ordinary DML.</p>
 */
@TestConfiguration
@ConditionalOnProperty(name = "tenant.migration.database.username")
public class TenantMigrationOwnerDataSourceConfig {

    @Bean(name = "tenantMigrationOwnerDataSource")
    public DataSource tenantMigrationOwnerDataSource(Environment env) {
        String url = env.getProperty("tenant.migration.database.url",
                env.getProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/sanad_tenant_isolation"));
        String username = env.getProperty("tenant.migration.database.username");
        String password = env.getProperty("tenant.migration.database.password");
        String driver = env.getProperty("tenant.migration.database.driver",
                env.getProperty("DATABASE_DRIVER", "org.postgresql.Driver"));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("TenantMigrationOwnerCP");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(1);
        return ds;
    }
}
