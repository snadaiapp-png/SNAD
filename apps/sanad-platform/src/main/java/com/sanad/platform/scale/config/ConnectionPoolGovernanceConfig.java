package com.sanad.platform.scale.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Stage 08 Sprint 1 — ST8-S1-005 Connection Pool Governance.
 *
 * Enforces HikariCP pool governance with environment-variable-controlled
 * sizing so that the production deployment (Render free tier, 512MB RAM)
 * is not OOM-killed by an oversized connection pool.
 *
 * Pool sizing is read from the same DATABASE_POOL_* environment variables
 * used by application-prod.yml, ensuring that the @Primary DataSource bean
 * and the YAML-configured pool stay in sync:
 *   - DATABASE_POOL_MAX (default 5, was 200 — too large for free tier)
 *   - DATABASE_POOL_MIN (default 1, was 20 — too many idle connections)
 *   - DATABASE_POOL_TIMEOUT (default 30000ms)
 *
 * Leak detection is retained at 60s as a non-memory-impacting governance
 * control. Connection validation (SELECT 1) is retained for stale-connection
 * detection.
 */
@Configuration
public class ConnectionPoolGovernanceConfig {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolGovernanceConfig.class);

    @Bean
    @Primary
    public DataSource governedDataSource(
            DataSourceProperties properties,
            @Value("${spring.datasource.hikari.maximum-pool-size:5}") int maxPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:1}") int minIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeout
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("SanadHikariCP-Governed");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());

        // Pool sizing — environment-variable controlled via DATABASE_POOL_*
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);

        // Governance controls that do not impact memory
        config.setIdleTimeout(600000);              // 10 minutes
        config.setMaxLifetime(1800000);              // 30 minutes
        config.setLeakDetectionThreshold(60000);    // 60 seconds

        // Validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(2000);          // 2 seconds

        log.info("HikariCP pool governance configured: maxPoolSize={} minIdle={} "
                + "connectionTimeout={}ms idleTimeout=600s maxLifetime=1800s leakDetection=60s",
                maxPoolSize, minIdle, connectionTimeout);

        return new HikariDataSource(config);
    }
}
