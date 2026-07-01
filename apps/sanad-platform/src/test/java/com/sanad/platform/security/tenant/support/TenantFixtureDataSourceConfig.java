package com.sanad.platform.security.tenant.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Stage 04A.3.1 §3 — Test configuration that creates a SEPARATE DataSource
 * for fixture data creation. This DataSource uses the migration_owner account
 * which is NOT subject to RLS (it owns the tables).
 *
 * <p>Activated only when TENANT_FIXTURE_DATABASE_USERNAME is set (CI).</p>
 */
@TestConfiguration
@ConditionalOnProperty(name = "tenant.fixture.database.username")
public class TenantFixtureDataSourceConfig {

    @Bean(name = "tenantFixtureDataSource")
    public DataSource tenantFixtureDataSource(Environment env) {
        String url = env.getProperty("tenant.fixture.database.url",
                env.getProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/sanad_tenant_isolation"));
        String username = env.getProperty("tenant.fixture.database.username");
        String password = env.getProperty("tenant.fixture.database.password");
        String driver = env.getProperty("tenant.fixture.database.driver",
                env.getProperty("DATABASE_DRIVER", "org.postgresql.Driver"));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("TenantFixtureCP");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(1);
        return ds;
    }

    @Bean
    public TenantFixtureDataSource tenantFixtureDataSourceProvider(
            @org.springframework.beans.factory.annotation.Qualifier("tenantFixtureDataSource") DataSource fixtureDs) {
        return () -> fixtureDs;
    }
}
