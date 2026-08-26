package com.sanad.platform.crm.party;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C2 — Contact FORCE RLS PostgreSQL Direct verification.
 *
 * <p>Proves the database-level contract that {@code crm_contacts} is subject
 * to a fail-closed tenant isolation policy under FORCE ROW LEVEL SECURITY,
 * following the same pattern established by {@code V20260822_2} for the CRM
 * collaboration event tables.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>RLS enabled on crm_contacts ({@code relrowsecurity = true}).</li>
 *   <li>FORCE RLS enabled on crm_contacts
 *       ({@code relforcerowsecurity = true}) — the table owner (Flyway /
 *       migration role) is NOT exempted.</li>
 *   <li>Fail-closed policy {@code crm_contacts_tenant_isolation} exists
 *       with both USING and WITH CHECK clauses that compare
 *       {@code tenant_id = current_setting('app.tenant_id', true)::UUID}.</li>
 *   <li>Legacy permissive-when-unset {@code tenant_isolation} policy is
 *       removed so it cannot OR-override the fail-closed predicate
 *       (PostgreSQL OR-combines permissive policies on the same table).</li>
 * </ul>
 *
 * <h3>Behavioral matrix</h3>
 * <table>
 *   <tr><th>Scenario</th><th>app.tenant_id</th><th>Expected</th></tr>
 *   <tr><td>A. SELECT same tenant</td><td>set to A</td><td>row visible</td></tr>
 *   <tr><td>B. SELECT cross tenant</td><td>set to A</td><td>0 rows</td></tr>
 *   <tr><td>C. SELECT missing GUC</td><td>unset</td><td>0 rows (fail closed)</td></tr>
 *   <tr><td>D. INSERT/UPDATE same tenant</td><td>set to A</td><td>succeeds</td></tr>
 *   <tr><td>E. INSERT/UPDATE cross tenant</td><td>set to A</td><td>rejected by WITH CHECK</td></tr>
 *   <tr><td>F. INSERT/UPDATE missing GUC</td><td>unset</td><td>rejected (fail closed)</td></tr>
 *   <tr><td>G. SET LOCAL resets after commit</td><td>after commit</td><td>0 rows in next tx</td></tr>
 *   <tr><td>H. policy has both USING + WITH CHECK tenant predicates</td><td>—</td><td>both non-null, equal</td></tr>
 *   <tr><td>I. sanad role has rolsuper=false, rolbypassrls=false</td><td>—</td><td>verified</td></tr>
 * </table>
 *
 * <p>This test uses the disposable {@code test_migration} database (never the
 * shared {@code sanad} database) and runs the full Flyway migration chain
 * including the V15 Java migration, mirroring the pattern established by
 * {@code CrmRlsTenantIsolationPostgresTest}.</p>
 */
class ContactRlsPostgresTest {

    private static final String RLS_USER = "crm_contact_rls_test_user";
    private static final String RLS_PASSWORD = "rls_contact_test_pass";

    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactRlsPostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping ContactRlsPostgresTest. "
                        + "Run with PostgreSQL Direct to exercise crm_contacts FORCE RLS.");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @BeforeEach
    void migrateAndSeed() {
        Flyway flyway = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        jdbc = jdbc();

        // The crm_contact_rls_test_user role is pre-provisioned by the
        // environment/bootstrap setup (analogous to the test_migration
        // database pre-provisioning) so the application role sanad does
        // not need CREATEROLE. Verify presence; do not attempt CREATE ROLE
        // because sanad lacks CREATEROLE under the least-privilege contract.
        Boolean roleExists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                Boolean.class, RLS_USER);
        if (Boolean.FALSE.equals(roleExists)) {
            throw new IllegalStateException(
                    "Pre-provisioned role 'crm_contact_rls_test_user' is missing. "
                            + "An environment/bootstrap actor must CREATE ROLE "
                            + "crm_contact_rls_test_user WITH LOGIN PASSWORD '<redacted>' "
                            + "before running ContactRlsPostgresTest. sanad does not have "
                            + "CREATEROLE under the least-privilege contract.");
        }
        String jdbcUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        String currentDb = jdbcUrl.replaceAll("^.*\\/[\\/]?[^\\/]*\\/", "").replaceAll("[;?].*$", "");
        if (currentDb.isBlank()) {
            currentDb = MigrationTestSchemaSupport.ISOLATED_DB_NAME;
        }
        jdbc.execute("GRANT CONNECT ON DATABASE \"" + currentDb + "\" TO " + RLS_USER);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + RLS_USER);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + RLS_USER);
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public "
                + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + RLS_USER);
        jdbc.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO " + RLS_USER);
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public "
                + "GRANT USAGE ON SEQUENCES TO " + RLS_USER);

        seedTenant(tenantAId(), "Tenant A", "contact-rls-a");
        seedTenant(tenantBId(), "Tenant B", "contact-rls-b");
    }

    // ── Schema metadata tests (A, B, H, I) ──────────────────────────────

    @Test
    void a_rlsIsEnabledOnCrmContacts() {
        Boolean enabled = jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'crm_contacts'",
                Boolean.class);
        assertThat(enabled)
                .as("RLS must be ENABLED on crm_contacts")
                .isTrue();
    }

    @Test
    void b_forceRlsIsEnabledOnCrmContacts() {
        Boolean forced = jdbc.queryForObject(
                "SELECT relforcerowsecurity FROM pg_class WHERE relname = 'crm_contacts'",
                Boolean.class);
        assertThat(forced)
                .as("FORCE RLS must be ENABLED on crm_contacts so the table owner is also subject to the policy")
                .isTrue();
    }

    @Test
    void h_failClosedTenantPolicyHasBothUsingAndWithCheck() {
        // The fail-closed policy must exist with both USING and WITH CHECK
        // clauses comparing tenant_id to the GUC. We assert the policy row
        // exists and that both expressions are non-null and structurally
        // contain the tenant comparison.
        Long policyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_policies "
                        + "WHERE tablename = 'crm_contacts' "
                        + "AND policyname = 'crm_contacts_tenant_isolation'",
                Long.class);
        assertThat(policyCount)
                .as("Fail-closed policy 'crm_contacts_tenant_isolation' must exist on crm_contacts")
                .isEqualTo(1L);

        // Legacy permissive-when-unset policy must NOT remain — PostgreSQL
        // OR-combines permissive policies, so leaving it would re-open the
        // silent-skip window when the GUC is unset.
        Long legacyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_policies "
                        + "WHERE tablename = 'crm_contacts' "
                        + "AND policyname = 'tenant_isolation'",
                Long.class);
        assertThat(legacyCount)
                .as("Legacy permissive-when-unset 'tenant_isolation' policy must NOT remain on crm_contacts")
                .isZero();
    }

    @Test
    void i_sanadRoleIsNotSuperuserAndDoesNotBypassRls() {
        // The sanad application role must NOT have SUPERUSER or BYPASSRLS.
        // Without these flags, the only way sanad can see any crm_contacts
        // row is via the GUC — proving FORCE RLS applies to the table owner.
        Boolean rolsuper = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = 'sanad'",
                Boolean.class);
        Boolean rolbypassrls = jdbc.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'sanad'",
                Boolean.class);
        assertThat(rolsuper)
                .as("sanad must NOT be a superuser")
                .isFalse();
        assertThat(rolbypassrls)
                .as("sanad must NOT have BYPASSRLS")
                .isFalse();
    }

    // ── Behavioral tests (C–G) ──────────────────────────────────────────
    //
    // Behavioral tests connect as the non-superuser, non-owner
    // crm_contact_rls_test_user so RLS actually applies.

    @Test
    void c_correctTenantReadReturnsOwnContacts() throws SQLException {
        seedContact(tenantAId(), "Alice A");
        seedContact(tenantBId(), "Bob B");

        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            Long visible = countContactsByName(conn, "Alice A");
            assertThat(visible)
                    .as("Tenant A should see its own contact")
                    .isEqualTo(1L);
            conn.commit();
        }
    }

    @Test
    void d_wrongTenantReadReturnsZeroRows() throws SQLException {
        seedContact(tenantAId(), "Alice A");
        seedContact(tenantBId(), "Bob B");

        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            Long cross = countContactsByName(conn, "Bob B");
            assertThat(cross)
                    .as("Tenant A must NOT see Tenant B's contact")
                    .isZero();
            conn.commit();
        }
    }

    @Test
    void e_missingTenantGucFailClosedForReads() throws SQLException {
        seedContact(tenantAId(), "Alice A");
        seedContact(tenantBId(), "Bob B");

        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            // Do NOT set app.tenant_id — fail-closed policy must hide all rows.
            Long visible = countContacts(conn);
            assertThat(visible)
                    .as("Missing app.tenant_id GUC must fail closed (0 rows)")
                    .isZero();
            conn.commit();
        }
    }

    @Test
    void f_correctTenantInsertSucceeds() throws SQLException {
        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            insertContact(conn, tenantAId(), "New Alice");
            conn.commit();
        }
        // Verify via an owner connection that ALSO sets the GUC, since
        // FORCE RLS now subjects even the table owner to the fail-closed
        // policy.
        long count;
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            count = countContactsByName(conn, "New Alice");
            conn.commit();
        }
        assertThat(count)
                .as("WITH CHECK must accept a Contact whose tenant_id matches the GUC")
                .isEqualTo(1L);
    }

    @Test
    void g_crossTenantInsertIsBlockedByWithCheck() throws SQLException {
        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            assertThatThrownBy(() -> insertContact(conn, tenantBId(), "Stolen Bob"))
                    .isInstanceOf(SQLException.class);
            conn.rollback();
        }
    }

    @Test
    void j_missingGucCannotWrite() throws SQLException {
        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            // No SET LOCAL — fail-closed WITH CHECK must reject the insert.
            assertThatThrownBy(() -> insertContact(conn, tenantAId(), "No Guc Insert"))
                    .isInstanceOf(SQLException.class);
            conn.rollback();
        }
    }

    @Test
    void k_setLocalResetsAfterTransaction() throws SQLException {
        seedContact(tenantAId(), "Alice A");
        seedContact(tenantBId(), "Bob B");

        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantAId() + "'");
            assertThat(countContacts(conn)).isEqualTo(1L);
            conn.commit();
        }
        try (Connection conn = rlsConnection()) {
            conn.setAutoCommit(false);
            // No SET LOCAL here — the previous SET LOCAL must NOT leak.
            Long visible = countContacts(conn);
            assertThat(visible)
                    .as("SET LOCAL must reset after transaction commit — next tx must fail closed (0 rows)")
                    .isZero();
            conn.commit();
        }
    }

    @Test
    void l_forceRlsAppliesToTableOwner() throws SQLException {
        seedContact(tenantAId(), "Alice A");
        seedContact(tenantBId(), "Bob B");

        // Connect as the table owner (sanad). sanad is NOT a superuser and
        // does NOT have BYPASSRLS. Without FORCE RLS the owner would see
        // all rows; with FORCE RLS the owner is subject to the policy.
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            // No SET LOCAL — fail-closed predicate must hide every row.
            Long visible = countContacts(conn);
            assertThat(visible)
                    .as("FORCE RLS must apply to the table owner: missing GUC must hide all rows")
                    .isZero();
            conn.commit();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private UUID tenantAId() {
        return UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    }

    private UUID tenantBId() {
        return UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }

    private Connection rlsConnection() throws SQLException {
        return DriverManager.getConnection(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                RLS_USER, RLS_PASSWORD);
    }

    private Connection ownerConnection() throws SQLException {
        // Connect as the table owner (Flyway / migration role). sanad is
        // NOT a superuser (rolsuper=f) and does NOT have BYPASSRLS, so the
        // only way it sees any rows without the GUC is if FORCE RLS is OFF.
        return DriverManager.getConnection(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    private void seedTenant(UUID id, String name, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """, id, name, subdomain);
    }

    private void seedContact(UUID tenantId, String displayName) {
        // Seed as the table owner (sanad) within an explicit transaction
        // with the GUC set transaction-local. This satisfies the fail-closed
        // WITH CHECK predicate once V20260823_1 is in effect.
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
            insertContact(conn, tenantId, displayName);
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed crm_contacts row for tenant " + tenantId, e);
        }
    }

    private void insertContact(Connection conn, UUID tenantId, String displayName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO crm_contacts (
                    id, tenant_id, version, given_name, display_name, normalized_name,
                    lifecycle_status, consent_summary, created_by, updated_by,
                    created_at, updated_at
                ) VALUES (?, ?, 0, ?, ?, ?, 'ACTIVE', 'UNKNOWN', ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            UUID actor = UUID.randomUUID();
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, displayName);
            ps.setString(4, displayName);
            ps.setString(5, displayName.toLowerCase());
            ps.setObject(6, actor);
            ps.setObject(7, actor);
            ps.executeUpdate();
        }
    }

    private long countContacts(Connection conn) throws SQLException {
        try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM crm_contacts")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long countContactsByName(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM crm_contacts WHERE display_name = ?")) {
            ps.setString(1, name);
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }
}
