package com.sanad.platform.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test-schema maintenance executed over a dedicated connection using the
 * test DDL identity.
 *
 * <p><strong>Why this exists (Issue #1462, P0 migrator/runtime separation):</strong>
 * under the separated deployment topology the application role {@code sanad}
 * is no longer the table owner, and deliberately holds no TRUNCATE privilege
 * (TRUNCATE is not subject to row-level security). Test-suite cleanup
 * TRUNCATEs therefore run here over a separate connection using
 * {@code SANAD_TEST_DDL_USERNAME} / {@code SANAD_TEST_DDL_PASSWORD}, which
 * CI provisions as the migrator-class identity.</p>
 *
 * <p>In local and fixture topologies (owner-as-runner) the defaults fall back
 * to the application datasource credentials, preserving the previous behavior
 * unchanged.</p>
 */
public final class TestIsolation {

    private TestIsolation() {}

    /**
     * Executes {@code TRUNCATE TABLE <tables> RESTART IDENTITY CASCADE} over a
     * dedicated maintenance connection (never the pooled application
     * datasource).
     *
     * @param tables the tables to truncate, in dependency-safe order
     */
    public static void truncateAll(String... tables) {
        if (tables == null || tables.length == 0) {
            return;
        }
        String url = envOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String user = envOrDefault("SANAD_TEST_DDL_USERNAME",
                envOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        String password = envOrDefault("SANAD_TEST_DDL_PASSWORD",
                envOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        StringBuilder sql = new StringBuilder("TRUNCATE TABLE ");
        for (int i = 0; i < tables.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(tables[i]);
        }
        sql.append(" RESTART IDENTITY CASCADE");
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql.toString());
        } catch (SQLException e) {
            throw new IllegalStateException("TestIsolation.truncateAll failed: " + sql, e);
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
