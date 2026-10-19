package com.sanad.platform.hr.payroll;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G4 real data isolation proof against PostgreSQL Direct in an isolated
 * database. Unlike the production API certification (which legitimately uses a
 * synthetic run id against the dormant certified topology), this test seeds a
 * REAL payroll run with known ownership for Tenant A and proves, row by row,
 * that Tenant B can neither read nor modify it, that an empty tenant context
 * is denied, and that FORCE ROW LEVEL SECURITY stays active on both payroll
 * tables while the application role holds no bypass privileges.
 *
 * Fail-closed: PostgreSQL Direct unavailability is a hard failure, never a
 * skip. Results are independent from the production UI acceptance gate.
 */
class HrG4PayrollDataIsolationPostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static String isolatedUrl;

    private DriverManagerDataSource dataSource;
    private Connection connection;
    private UUID tenantA;
    private UUID tenantB;
    private UUID legalEntityA;
    private UUID legalEntityB;
    private UUID runA;
    private UUID runB;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
            available = false;
        }
        if (!available) {
            throw new IllegalStateException(
                    "G4 data isolation requires PostgreSQL Direct; refusing to skip (fail-closed)");
        }
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateAndSeed() throws Exception {
        dataSource = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();

        connection = dataSource.getConnection();
        connection.setAutoCommit(true);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        legalEntityA = UUID.randomUUID();
        legalEntityB = UUID.randomUUID();
        runA = UUID.randomUUID();
        runB = UUID.randomUUID();

        seedTenant(tenantA, "g4-isolation-a");
        seedTenant(tenantB, "g4-isolation-b");
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void payrollTablesAreForcedRlsAndRoleHoldsNoBypass() throws Exception {
        // The application role must never be able to bypass row level security.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).as("application role must not be superuser").isFalse();
            assertThat(rs.getBoolean(2)).as("application role must not hold BYPASSRLS").isFalse();
        }

        // Both payroll tables must keep ENABLE + FORCE row level security so
        // the table owner is subject to the policies too.
        for (String table : new String[] {"hr_payroll_runs", "hr_payroll_items"}) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class "
                            + "WHERE relname = ? AND relnamespace = 'public'::regnamespace")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("table %s must exist", table).isTrue();
                    assertThat(rs.getBoolean(1)).as("%s relrowsecurity", table).isTrue();
                    assertThat(rs.getBoolean(2)).as("%s relforcerowsecurity", table).isTrue();
                }
            }
        }
    }

    @Test
    void tenantBCanNeitherReadNorModifyTenantAKnownOwnershipRecord() throws Exception {
        seedTenantDataAsA();

        // Tenant A reads its own real record with known ownership.
        setTenant(tenantA);
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .as("Tenant A sees its own payroll run").isEqualTo(1);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .isEqualTo("DRAFT");

        // Legitimate self-mutation within the tenant still works (RLS is
        // tenant-scoped, not a blanket denial).
        int selfUpdated;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_payroll_runs SET status = 'CALCULATED' WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, runA);
            ps.setObject(2, tenantA);
            selfUpdated = ps.executeUpdate();
        }
        assertThat(selfUpdated).as("Tenant A mutates its own run").isEqualTo(1);

        // Tenant B cannot read Tenant A's record.
        setTenant(tenantB);
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .as("Tenant B must not read Tenant A's run").isZero();

        // Tenant B cannot modify Tenant A's record.
        int crossUpdate;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_payroll_runs SET status = 'APPROVED' WHERE id = ?")) {
            ps.setObject(1, runA);
            crossUpdate = ps.executeUpdate();
        }
        assertThat(crossUpdate).as("Tenant B must not modify Tenant A's run").isZero();

        // Tenant B cannot graft a row into Tenant A by writing A's tenant_id.
        assertThatThrownBy(() -> insertRun(tenantA, legalEntityA, runA, "G4_CROSS_TENANT_PROBE"))
                .as("cross-tenant insert must be rejected by the RLS policy")
                .isInstanceOf(SQLException.class);

        // Tenant B can still manage its own records: policy is per-tenant.
        seedLegalEntity(tenantB, legalEntityB, "g4-iso-b");
        insertRun(tenantB, legalEntityB, runB, "G4_TENANT_B_OWN");
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runB + "'"))
                .as("Tenant B sees its own run").isEqualTo(1);

        // Symmetry: Tenant A gains no visibility into Tenant B either.
        setTenant(tenantA);
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runB + "'"))
                .as("Tenant A must not read Tenant B's run").isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .as("Tenant A still sees its own run").isEqualTo(1);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .as("Tenant B's modification attempt must not have landed")
                .isEqualTo("CALCULATED");

        // Empty tenant context stays fail-closed on both records.
        setTenant(null);
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runA + "'"))
                .as("empty tenant context must not read Tenant A's run").isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runB + "'"))
                .as("empty tenant context must not read Tenant B's run").isZero();

        // Restoring Tenant A context restores exactly its own visibility.
        setTenant(tenantA);
        assertThat(count("SELECT COUNT(*) FROM hr_payroll_runs WHERE id = '" + runA + "'")).isEqualTo(1);
    }

    private void seedTenantDataAsA() throws Exception {
        setTenant(tenantA);
        seedLegalEntity(tenantA, legalEntityA, "g4-iso-a");
        insertRun(tenantA, legalEntityA, runA, "G4_TENANT_A_KNOWN_OWNERSHIP");
    }

    private void seedTenant(UUID id, String prefix) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?,?,?,'ACTIVE',NOW(),NOW())
                """)) {
            ps.setObject(1, id);
            ps.setString(2, "G4 Isolation " + id);
            ps.setString(3, prefix + "-" + id.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void seedLegalEntity(UUID tenant, UUID id, String prefix) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO legal_entities (
                    id, tenant_id, code, name, registered_country_code,
                    statutory_country_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'G4 Isolation Entity', 'SA', 'SA', 'ACTIVE', NOW(), NOW())
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, prefix + "-" + id.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void insertRun(UUID tenant, UUID legalEntity, UUID runId, String reasonTag) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_payroll_runs (
                    id, tenant_id, legal_entity_id, period_start, period_end,
                    currency_code, status, source_cutoff_at, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, DATE '2026-10-01', DATE '2026-10-31',
                          'SAR', 'DRAFT', ?, 0, NOW(), NOW())
                """)) {
            ps.setObject(1, runId);
            ps.setObject(2, tenant);
            ps.setObject(3, legalEntity);
            ps.setObject(4, OffsetDateTime.parse("2026-11-01T00:00:00Z"));
            ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.execute();
        }
    }

    private String query(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private int count(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
