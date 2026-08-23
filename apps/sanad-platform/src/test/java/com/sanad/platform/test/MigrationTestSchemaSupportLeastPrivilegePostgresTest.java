package com.sanad.platform.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task R0-C — least-privilege certification for
 * {@link MigrationTestSchemaSupport#ensureDatabase(String, String, String)}.
 *
 * <p>Proves the contract documented by R0-C:</p>
 * <ul>
 *   <li>When the {@code test_migration} database already exists on the
 *       server, {@code ensureDatabase()} returns successfully even if the
 *       connection role lacks CREATEDB.</li>
 *   <li>The application role {@code sanad} must therefore NOT need
 *       CREATEDB as a permanent privilege — a pre-provisioned
 *       {@code test_migration} database allows a least-privilege
 *       application role (no SUPERUSER, no BYPASSRLS, no CREATEDB,
 *       no CREATEROLE).</li>
 * </ul>
 *
 * <h3>Preconditions</h3>
 * <ul>
 *   <li>PostgreSQL Direct reachable at the URL described by
 *       {@code SPRING_DATASOURCE_URL} (default
 *       {@code jdbc:postgresql://localhost:5432/sanad}).</li>
 *   <li>The {@code test_migration} database has been pre-provisioned
 *       (created by an operator via the {@code postgres} superuser and
 *       owned by {@code sanad}) before this test runs.</li>
 *   <li>The {@code sanad} role has been configured with
 *       {@code NOCREATEDB} (so the fast path is the only viable code
 *       path through {@code ensureDatabase()}).</li>
 * </ul>
 *
 * <p>If PostgreSQL is unavailable locally and we are not in CI, the test
 * skips gracefully (mirroring the policy in
 * {@code Crm009TestEnvironment}). In CI it fails.</p>
 */
class MigrationTestSchemaSupportLeastPrivilegePostgresTest {

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = checkConnectivity();
        } catch (Throwable ignored) {
            available = false;
        }
        boolean isCi = Boolean.parseBoolean(System.getenv("CI"))
                || Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS"))
                || Boolean.parseBoolean(System.getenv("CRM_009_POSTGRES_MANDATORY"));
        if (!available) {
            if (isCi) {
                throw new IllegalStateException(
                        "PostgreSQL Direct (localhost:5432) is MANDATORY for R0-C CI "
                                + "acceptance. Test cannot be skipped in CI.");
            }
            Assumptions.assumeTrue(false,
                    "PostgreSQL Direct is not available — skipping "
                            + "MigrationTestSchemaSupportLeastPrivilegePostgresTest.");
        }
    }

    @Test
    void ensureDatabaseSucceedsWithoutCreatedbWhenDatabasePreExists() throws SQLException {
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        // Pre-condition A: sanad must NOT have CREATEDB.
        // If sanad has CREATEDB here the test is meaningless — it would pass
        // via the slow path. Assert the precondition explicitly.
        assertThat(roleHasCreatedb(url, user, password))
                .as("sanad must NOT have CREATEDB to certify the least-privilege contract")
                .isFalse();

        // Pre-condition B: test_migration must exist and be owned by sanad
        // (i.e. pre-provisioned by an operator, not creatable by the app role).
        assertThat(databaseExists(url, user, password, MigrationTestSchemaSupport.ISOLATED_DB_NAME))
                .as("test_migration database must be pre-provisioned before this test runs")
                .isTrue();

        // Behaviour under test: ensureDatabase() must return successfully
        // WITHOUT attempting CREATE DATABASE (which would fail with
        // SQLSTATE 42501 because sanad has NOCREATEDB).
        // If the fast path is missing, this call throws IllegalStateException
        // wrapping "permission denied to create database".
        MigrationTestSchemaSupport.ensureDatabase(url, user, password);

        // If we reach this assertion, ensureDatabase() took the fast path.
        assertThat(databaseExists(url, user, password, MigrationTestSchemaSupport.ISOLATED_DB_NAME))
                .as("test_migration must still exist after ensureDatabase() returned")
                .isTrue();
    }

    // ---- helpers ----------------------------------------------------------

    private static boolean checkConnectivity() {
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean roleHasCreatedb(String url, String user, String password)
            throws SQLException {
        String adminUrl = MigrationTestSchemaSupport.getAdminJdbcUrl(url);
        try (Connection conn = DriverManager.getConnection(adminUrl, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT rolcreatedb FROM pg_roles WHERE rolname = current_user")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private static boolean databaseExists(String url, String user, String password,
                                          String databaseName) throws SQLException {
        String adminUrl = MigrationTestSchemaSupport.getAdminJdbcUrl(url);
        try (Connection conn = DriverManager.getConnection(adminUrl, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '"
                             + databaseName.replace("'", "''") + "'")) {
            return rs.next();
        }
    }
}
