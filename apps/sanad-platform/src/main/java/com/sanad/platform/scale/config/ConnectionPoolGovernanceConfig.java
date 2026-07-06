package com.sanad.platform.scale.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Stage 08 Sprint 1 — ST8-S1-005 Connection Pool Governance.
 *
 * Enforces HikariCP pool governance:
 *   - maxPoolSize: 200 (production)
 *   - minIdle: 20
 *   - idleTimeout: 10 minutes
 *   - maxLifetime: 30 minutes
 *   - connectionTimeout: 5 seconds (acquire)
 *   - leakDetectionThreshold: 60 seconds
 *
 * Per-tenant sub-pool isolation is provided by tenant-aware routing
 * (separate concern, ST8-S1-004 Noisy-Neighbor Protection).
 *
 * Observability: Hikari exposes metrics via micrometer; the
 * {@code hikaricp.connections.active} gauge is the primary utilization
 * signal. Alert at > 80% utilization.
 *
 * Note: This config reuses the existing Spring Boot DataSourceProperties
 * bean (auto-configured from spring.datasource.* properties). It does NOT
 * declare its own DataSourceProperties bean to avoid duplicate bean
 * conflicts.
 */
@Configuration
public class ConnectionPoolGovernanceConfig {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolGovernanceConfig.class);

    @Bean
    @Primary
    public DataSource governedDataSource(DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("SanadHikariCP-Governed");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());

        // Pool governance — ST8-S1-005
        config.setMaximumPoolSize(200);
        config.setMinimumIdle(20);
        config.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
        config.setLeakDetectionThreshold(Duration.ofSeconds(60).toMillis());

        // Validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(Duration.ofSeconds(2).toMillis());

        log.info("HikariCP pool governance configured: maxPoolSize=200 minIdle=20 "
                + "idleTimeout=600s maxLifetime=1800s connectionTimeout=5s leakDetection=60s");

        return new HikariDataSource(config);
    }
}
