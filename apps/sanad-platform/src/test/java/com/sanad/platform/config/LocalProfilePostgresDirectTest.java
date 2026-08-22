package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the {@code local} Spring profile is configured for PostgreSQL
 * Direct (not H2). This contract test enforces the architectural decision
 * that H2 is deprecated as a backend acceptance runtime.
 *
 * <p>The test boots a minimal Spring context with the {@code local} profile
 * and verifies that:
 * <ul>
 *   <li>The database product name is PostgreSQL (not H2).</li>
 *   <li>The JDBC URL starts with {@code jdbc:postgresql:} (not {@code jdbc:h2:}).</li>
 *   <li>The driver class is {@code org.postgresql.Driver} (not {@code org.h2.Driver}).</li>
 * </ul>
 *
 * <p>Requires a disposable PostgreSQL instance on localhost:5432 with database
 * {@code sanad} accessible to role {@code sanad} (trust or password auth).
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalProfilePostgresDirectTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("local profile uses PostgreSQL as the backend database")
    void localProfileUsesPostgresDirect() throws Exception {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        String productName = metaData.getDatabaseProductName();
        String url = metaData.getURL();
        String driverName = metaData.getDriverName();

        assertThat(productName)
                .as("local profile must use PostgreSQL, not H2")
                .containsIgnoringCase("PostgreSQL");
        assertThat(url)
                .as("local profile JDBC URL must start with jdbc:postgresql:")
                .startsWith("jdbc:postgresql:");
        assertThat(driverName)
                .as("local profile driver must be PostgreSQL JDBC Driver")
                .containsIgnoringCase("PostgreSQL");
    }
}
