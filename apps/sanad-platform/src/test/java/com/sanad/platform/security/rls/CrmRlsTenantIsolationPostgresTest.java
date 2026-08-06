package com.sanad.platform.security.rls;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRM-018 — Row-Level Security tenant isolation verification.
 *
 * <p>Proves at the database level that PostgreSQL RLS policies enforce
 * cross-tenant isolation as a defense-in-depth layer on top of the
 * existing application-level filtering.</p>
 *
 * <h3>Test matrix</h3>
 * <table>
 *   <tr><th>Scenario</th><th>app.tenant_id</th><th>Expected</th></tr>
 *   <tr><td>SELECT same tenant</td><td>set to A</td><td>✅ returns rows</td></tr>
 *   <tr><td>SELECT cross tenant</td><td>set to A</td><td>❌ 0 rows</td></tr>
 *   <tr><td>INSERT same tenant</td><td>set to A</td><td>✅ succeeds</td></tr>
 *   <tr><td>INSERT cross tenant</td><td>set to A</td><td>❌ blocked</td></tr>
 *   <tr><td>No context (fallback)</td><td>unset</td><td>✅ all rows</td></tr>
 *   <tr><td>SET LOCAL resets</td><td>after commit</td><td>✅ all rows</td></tr>
 * </table>
 */
@Testcontainers
class CrmRlsTenantIsolationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is not available — skipping CrmRlsTenantIsolationPostgresTest. "
                        + "Run on a CI runner with Docker to exercise PostgreSQL RLS.");
    }

    private static final String RLS_USER = "crm_rls_test_user";
    private static final String RLS_PASSWORD = "rls_test_pass";

    @BeforeEach
    void migrateAndSeed() {
        // Run all migrations including V20260730_1 (RLS enable).
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        jdbc = jdbc();

        // Create a non-superuser for RLS testing — superusers bypass RLS by default.
        try {
            jdbc.execute("DROP OWNED BY crm_rls_test_user");
        } catch (Exception ignored) {
            // Role may not exist yet
        }
        try {
            jdbc.execute("DROP ROLE IF EXISTS crm_rls_test_user");
        } catch (Exception ignored) {
            // Role may not exist
        }
        jdbc.execute("CREATE ROLE " + RLS_USER + " WITH LOGIN PASSWORD '" + RLS_PASSWORD + "'");
        jdbc.execute("GRANT CONNECT ON DATABASE test TO " + RLS_USER);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + RLS_USER);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + RLS_USER);
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + RLS_USER);
        // Grant usage on sequences needed for INSERT
        jdbc.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO " + RLS_USER);
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO " + RLS_USER);

        // Seed two tenants.
        insertTenant(UUID.randomUUID(), "Tenant A", "rls-a");
        insertTenant(UUID.randomUUID(), "Tenant B", "rls-b");
    }

    @Test
    void rlsIsEnabledOnCrmTables() {
        // Verify RLS is actually enabled on CRM tables.
        // NOTE: 'crm\\_%' in raw string → SQL LIKE 'crm\_%' where \ escapes the _.
        Long enabledCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_tables
                WHERE tablename LIKE 'crm\\_%'
                  AND rowsecurity = true
                """, Long.class);
        assertThat(enabledCount)
                .as("RLS should be enabled on all CRM tables with tenant_id")
                .isGreaterThan(0L);
    }

    @Test
    void rlsPolicyExistsOnCrmTables() {
        Long policyCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_policies
                WHERE tablename LIKE 'crm\\_%'
                  AND policyname = 'tenant_isolation'
                """, Long.class);
        assertThat(policyCount)
                .as("tenant_isolation policy should exist on all CRM tables")
                .isGreaterThan(0L);
    }

    @Test
    void selectWithTenantContextReturnsOnlyOwnRows() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");
        seedAccount(tenantA, "Account A");
        seedAccount(tenantB, "Account B");

        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");

            Long visibleToA = countAccounts(conn);
            assertThat(visibleToA)
                    .as("Tenant A should only see its own accounts")
                    .isEqualTo(1L);

            conn.commit();
        }
    }

    @Test
    void selectCrossTenantReturnsZeroRows() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");
        seedAccount(tenantA, "Account A");
        seedAccount(tenantB, "Account B");

        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            // Tenant A's context
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");

            // Try to read tenant B's data — should get 0 rows
            Long crossTenantVisible = countAccountsByName(conn, "Account B");
            assertThat(crossTenantVisible)
                    .as("Tenant A must NOT see Tenant B's accounts")
                    .isZero();

            conn.commit();
        }
    }

    @Test
    void insertSameTenantSucceeds() throws SQLException {
        UUID tenantA = tenantId("rls-a");

        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");

            // Insert with matching tenant — should succeed
            insertAccount(conn, tenantA, "New Account A");

            conn.commit();
        }

        // Verify it was committed
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND display_name = ?",
                Long.class, tenantA, "New Account A");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void insertCrossTenantIsBlockedByWithCheck() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");

        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            // Context is tenant A
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");

            // Attempt to INSERT a row with tenant B's id — WITH CHECK should block it
            assertThatThrownBy(() -> insertAccount(conn, tenantB, "Stolen Account B"))
                    .isInstanceOf(SQLException.class);

            conn.rollback();
        }
    }

    @Test
    void withoutTenantContextAllRowsVisible() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");
        seedAccount(tenantA, "Account A");
        seedAccount(tenantB, "Account B");

        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            // Do NOT set app.tenant_id — fallback should be permissive
            Long allVisible = countAccounts(conn);
            assertThat(allVisible)
                    .as("Without tenant context, RLS should be permissive (fallback mode)")
                    .isGreaterThanOrEqualTo(2L);

            conn.commit();
        }
    }

    @Test
    void setLocalResetsAfterTransaction() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");
        seedAccount(tenantA, "Account A");
        seedAccount(tenantB, "Account B");

        // Transaction 1: set tenant A
        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");
            assertThat(countAccounts(conn)).isEqualTo(1L);
            conn.commit();
        }

        // Transaction 2: same connection pool semantics — SET LOCAL must have reset
        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            // No SET LOCAL here — should see all rows (permissive fallback)
            Long allVisible = countAccounts(conn);
            assertThat(allVisible)
                    .as("SET LOCAL must reset after transaction commit")
                    .isGreaterThanOrEqualTo(2L);
            conn.commit();
        }
    }

    @Test
    void rollbackMigrationDisablesRls() throws SQLException {
        UUID tenantA = tenantId("rls-a");
        UUID tenantB = tenantId("rls-b");
        seedAccount(tenantA, "Account A");
        seedAccount(tenantB, "Account B");

        // Simulate the rollback migration: disable RLS on all CRM tables
        // (mirrors V20260730_2__disable_crm_row_level_security.sql).
        // We disable RLS directly rather than re-running flyway.clean()+migrate()
        // because clean() drops tables and wipes the crm_rls_test_user grants.
        jdbc.execute("""
                DO $$
                DECLARE tbl record;
                BEGIN
                    FOR tbl IN
                        SELECT c.table_name FROM information_schema.columns c
                        JOIN information_schema.tables t ON t.table_name = c.table_name AND t.table_schema = c.table_schema
                        WHERE c.table_schema = 'public' AND c.column_name = 'tenant_id'
                          AND c.table_name LIKE 'crm\\_%' AND t.table_type = 'BASE TABLE'
                    LOOP
                        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl.table_name);
                        EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', tbl.table_name);
                    END LOOP;
                END $$;
                """);

        // After rollback: cross-tenant access should be possible (RLS disabled)
        try (Connection conn = rawConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantA + "'");
            Long visible = countAccounts(conn);
            // With RLS disabled, SET LOCAL has no effect — all rows visible
            assertThat(visible)
                    .as("After rollback, RLS is disabled — SET LOCAL has no filtering effect")
                    .isGreaterThanOrEqualTo(2L);
            conn.commit();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private JdbcTemplate jdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(ds);
    }

    private Connection rawConnection() throws SQLException {
        // Use non-superuser to verify RLS — superusers bypass RLS by default.
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), RLS_USER, RLS_PASSWORD);
    }

    private void insertTenant(UUID id, String name, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, name, subdomain);
    }

    private UUID tenantId(String subdomain) {
        return jdbc.queryForObject(
                "SELECT id FROM tenants WHERE subdomain = ?", UUID.class, subdomain);
    }

    private void seedAccount(UUID tenantId, String name) {
        jdbc.update("""
                INSERT INTO crm_accounts (
                    id, tenant_id, version, display_name, normalized_name, account_type,
                    lifecycle_status, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 0, ?, ?, 'BUSINESS', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), tenantId, name, name.toLowerCase(),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private void insertAccount(Connection conn, UUID tenantId, String name) throws SQLException {
        try (var ps = conn.prepareStatement("""
                INSERT INTO crm_accounts (
                    id, tenant_id, version, display_name, normalized_name, account_type,
                    lifecycle_status, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 0, ?, ?, 'BUSINESS', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            UUID actor = UUID.randomUUID();
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, name);
            ps.setString(4, name.toLowerCase());
            ps.setObject(5, actor);
            ps.setObject(6, actor);
            ps.executeUpdate();
        }
    }

    private long countAccounts(Connection conn) throws SQLException {
        try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM crm_accounts")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long countAccountsByName(Connection conn, String name) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM crm_accounts WHERE display_name = ?")) {
            ps.setString(1, name);
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }
}
