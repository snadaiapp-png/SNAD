package com.sanad.platform.scale.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Stage 08 Sprint 1 — ST8-S1-005 Connection Pool Governance.
 *
 * <p>Provides an alternative HikariCP DataSource with governance controls
 * (leak detection, connection validation). This bean is DISABLED by default
 * and only activates when {@code sanad.scale.connection-pool-governance.enabled}
 * is set to {@code true}.</p>
 *
 * <p>When disabled, Spring Boot's auto-configured DataSource (from
 * {@code spring.datasource.hikari.*} properties in {@code application-prod.yml})
 * is used. This is the production-safe default because:</p>
 * <ol>
 *   <li>The YAML-configured pool already reads from {@code DATABASE_POOL_*}
 *       env vars with appropriate defaults for each deployment target.</li>
 *   <li>The auto-configured DataSource initializes lazily and does not call
 *       {@code checkFailFast()} aggressively, avoiding startup crashes when
 *       the database is briefly unreachable.</li>
 *   <li>On constrained environments (e.g. Render free tier, 512 MB RAM),
 *       the auto-configured pool with {@code maxPoolSize=5, minIdle=1} is
 *       memory-safe.</li>
 * </ol>
 *
 * <p>When enabled, this config creates a {@code @Primary} DataSource that
 * overrides the auto-configured one. Pool sizing is read from the same
 * {@code spring.datasource.hikari.*} properties to stay in sync with YAML.</p>
 */
@Configuration
@ConditionalOnProperty(
        name = "sanad.scale.connection-pool-governance.enabled",
        havingValue = "true",
        matchIfMissing = false)
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
