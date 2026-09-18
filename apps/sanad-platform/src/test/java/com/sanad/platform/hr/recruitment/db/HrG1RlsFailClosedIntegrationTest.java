package com.sanad.platform.hr.recruitment.db;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1-T2 — §8.1 RLS fail-closed behavior probes on HOST-NATIVE PostgreSQL.
 *
 * <p>Exercises the REAL application role ({@code sanad}, NOSUPERUSER
 * NOBYPASSRLS — asserted here too) against the shared {@code sanad}
 * database, on tables whose policies are byte-identical in shape. Session
 * tenant context is controlled via {@code SET app.tenant_id} on a single
 * connection, exactly like the G0 RLS probe pattern.</p>
 *
 * <p>Behavior matrix (per §8.1): own-tenant read allowed; cross-tenant read
 * DENIED (empty set); cross-tenant write DENIED (42501-class); NO context
 * DENIED (fail closed); FORCE RLS so even the table owner cannot bypass.</p>
 *
 * <p>RED contract: before the G1 migrations exist this class fails in
 * {@code requireG1Schema} — the suite refuses to pass while the security
 * surface is absent (fail-closed RED).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG1RlsFailClosedIntegrationTest {

    private static final List<String> HR_G1_TABLES = HrG1MigrationTest.HR_G1_TABLES;

    private Connection connection;
    private String tenantA;
    private String tenantB;
    private final List<String> cleanupStatements = new ArrayList<>();

    @BeforeAll
    void requireG1Schema() throws Exception {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "HrG1RlsFailClosedIntegrationTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping HrG1RlsFailClosedIntegrationTest.");

        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/sanad");
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        MigrationTestSchemaSupport.ensureDatabase(url, username, password);

        // Keep the shared sanad database current (append-only migrations).
        Flyway.configure()
                .dataSource(url, username, password)
                .validateOnMigrate(false)
                .load()
                .migrate();

        connection = DriverManager.getConnection(url, username, password);

        // Fail closed if the G1 security surface is not applied.
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM pg_tables WHERE schemaname='public' "
                             + "AND tablename = 'hr_candidates'")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("G1 schema must be applied before RLS probes run")
                    .isEqualTo(1);
        }

        // Two real tenants (reuse existing rows, seed only if missing).
        tenantA = ensureTenant("hr-g1-rls-a");
        tenantB = ensureTenant("hr-g1-rls-b");
        seedProbeRows();
    }

    private String ensureTenant(String subdomain) throws SQLException {
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id FROM tenants WHERE subdomain = ?")) {
            find.setString(1, subdomain);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            insert.setObject(1, java.util.UUID.fromString(id));
            insert.setString(2, "HR G1 RLS " + subdomain);
            insert.setString(3, subdomain);
            insert.executeUpdate();
        }
        return id;
    }

    private void seedProbeRows() throws SQLException {
        cleanupStatements.add("DELETE FROM hr_onboarding_checklist_templates WHERE code = 'G1-RLS-PROBE'");
        cleanupStatements.add("DELETE FROM hr_candidates WHERE candidate_number = 'G1-RLS-PROBE-A'");
        cleanupStatements.add("DELETE FROM hr_candidates WHERE candidate_number = 'G1-RLS-PROBE-B'");

        exec("SET app.tenant_id = '" + tenantA + "'");
        exec("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state) "
                + "VALUES (gen_random_uuid(), '" + tenantA + "', 'G1-RLS-PROBE-A', 'RLS Probe A', 'ACTIVE')");
        exec("INSERT INTO hr_onboarding_checklist_templates (id, tenant_id, code, name, version, definition) "
                + "VALUES (gen_random_uuid(), '" + tenantA + "', 'G1-RLS-PROBE', 'RLS Probe Template', 1, '[]'::jsonb)");
        exec("SET app.tenant_id = '" + tenantB + "'");
        exec("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state) "
                + "VALUES (gen_random_uuid(), '" + tenantB + "', 'G1-RLS-PROBE-B', 'RLS Probe B', 'ACTIVE')");
        exec("RESET app.tenant_id");
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    private int countRows(String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void ownTenantRead_isAllowed() throws Exception {
        exec("SET app.tenant_id = '" + tenantA + "'");
        int own = countRows("SELECT COUNT(*) FROM hr_candidates WHERE candidate_number = 'G1-RLS-PROBE-A'");
        assertThat(own).as("own-tenant rows visible under own context").isEqualTo(1);
    }

    @Test
    void crossTenantRead_returnsEmpty() throws Exception {
        exec("SET app.tenant_id = '" + tenantA + "'");
        int leaked = countRows("SELECT COUNT(*) FROM hr_candidates WHERE candidate_number = 'G1-RLS-PROBE-B'");
        assertThat(leaked)
                .as("§8.1: cross-tenant read must be DENIED (empty set)")
                .isZero();
    }

    @Test
    void crossTenantWrite_isDeniedWithPolicyViolation() throws Exception {
        exec("SET app.tenant_id = '" + tenantA + "'");
        assertThatThrownBy(() -> exec(
                "INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state) "
                        + "VALUES (gen_random_uuid(), '" + tenantB + "', 'G1-RLS-PROBE-B2', 'Illegal', 'ACTIVE')"))
                .as("§8.1: cross-tenant write must be DENIED (42501-class policy violation)")
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }

    @Test
    void noContext_failsClosed_forReadAndWrite() throws Exception {
        exec("RESET app.tenant_id");
        int leaked = countRows("SELECT COUNT(*) FROM hr_candidates WHERE candidate_number = 'G1-RLS-PROBE-A'");
        assertThat(leaked)
                .as("§8.1: NO tenant context must see nothing (fail closed)")
                .isZero();

        assertThatThrownBy(() -> exec(
                "INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state) "
                        + "VALUES (gen_random_uuid(), '" + tenantA + "', 'G1-RLS-PROBE-NOCTX', 'NoCtx', 'ACTIVE')"))
                .as("§8.1: NO tenant context must deny writes (fail closed)")
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }

    @Test
    void forceRls_verifiedViaCatalog_onEveryG1Table() throws Exception {
        for (String table : HR_G1_TABLES) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class c "
                            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname='public' AND c.relname = ?")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("table %s exists", table).isTrue();
                    assertThat(rs.getBoolean("relrowsecurity")).as("RLS enabled on %s", table).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).as("FORCE RLS on %s", table).isTrue();
                }
            }
        }
    }

    @Test
    void applicationRoleContract_isLeastPrivilege() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT rolsuper, rolcreatedb, rolcreaterole, rolcanlogin, rolbypassrls "
                             + "FROM pg_roles WHERE rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper")).as("NOSUPERUSER (§8.2)").isFalse();
            assertThat(rs.getBoolean("rolcreaterole")).as("NOCREATEROLE (§8.2)").isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).as("NOBYPASSRLS (§8.2)").isFalse();
            assertThat(rs.getBoolean("rolcanlogin")).as("LOGIN role (§8.2)").isTrue();
        }
    }

    @AfterAll
    void cleanupProbeRows() throws Exception {
        if (connection == null) {
            return;
        }
        for (String tenant : new String[] {tenantA, tenantB}) {
            if (tenant == null) {
                continue;
            }
            exec("SET app.tenant_id = '" + tenant + "'");
            for (String sql : cleanupStatements) {
                try {
                    exec(sql);
                } catch (SQLException ignored) {
                    // cleanup best-effort
                }
            }
        }
        exec("RESET app.tenant_id");
        connection.close();
    }
}
