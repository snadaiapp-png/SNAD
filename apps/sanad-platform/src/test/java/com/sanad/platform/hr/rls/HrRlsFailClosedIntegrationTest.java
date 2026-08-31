package com.sanad.platform.hr.rls;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS2 Task 5 — Fail-Closed HR RLS TRUE RED.
 *
 * <p>Database-level security contract tests for ALL HR tenant tables.
 * The final required behavior:
 * <ul>
 *   <li>CORRECT TENANT: own rows visible + writes allowed</li>
 *   <li>NO TENANT CONTEXT: zero rows visible + writes denied</li>
 *   <li>WRONG TENANT: zero rows visible + writes denied</li>
 *   <li>RLS ENABLED + FORCED on every HR tenant table</li>
 *   <li>No policy expression contains null-context allow behavior</li>
 * </ul>
 * </p>
 *
 * <p>Expected RED: legacy HR tables (hr_employees, hr_departments,
 * hr_positions) have FAIL-OPEN policies that allow no-context reads
 * and lack FORCE RLS. These tests MUST FAIL to prove the defect.</p>
 *
 * <p>Expected GREEN (regression control): canonical HR tables created
 * in Tasks 1-4 with FORCE RLS + fail-closed policies must PASS.</p>
 */
class HrRlsFailClosedIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSql() {
        boolean ok = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { ok = c.isValid(5); }
        } catch (Throwable ignored) {}
        Assumptions.assumeTrue(ok, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ==================== FIXTURE HELPERS ====================

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'RLS Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "rls-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private void resetTenant() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("RESET app.tenant_id");
        }
    }

    /**
     * Seed a row into the given HR table with the given tenant_id.
     * Returns a unique key (UUID) identifying the seeded row for later queries.
     */
    private UUID seedHrRow(String table, UUID tenantId) throws Exception {
        UUID rowId = UUID.randomUUID();
        switch (table) {
            case "hr_employees" -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                        "employment_type, status, hire_date, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'RLS', 'Test', 'RLS Test', 'FULL_TIME', 'ACTIVE', '2026-01-01'::date, 0, NOW(), NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "RLS-" + rowId.toString().substring(0, 8));
                    ps.executeUpdate();
                }
            }
            case "hr_departments" -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_departments (id, tenant_id, name, code, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'RLS Test Dept', ?, 'ACTIVE', NOW(), NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "DEP-" + rowId.toString().substring(0, 8));
                    ps.executeUpdate();
                }
            }
            case "hr_positions" -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'RLS Test Pos', ?, 'ACTIVE', NOW(), NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "POS-" + rowId.toString().substring(0, 8));
                    ps.executeUpdate();
                }
            }
            case "hr_people" -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, 'RLS', 'Person', 'RLS Person', 0, NOW(), NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
            }
            case "hr_person_private" -> {
                // Need to create a person first
                UUID personId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, 'RLS', 'Priv', 'RLS Priv', 0, NOW(), NOW())")) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_person_private (person_id, tenant_id, version, updated_at) VALUES (?, ?, 0, NOW())")) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
                return personId;
            }
            case "hr_person_identifiers" -> {
                UUID personId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, 'RLS', 'Ident', 'RLS Ident', 0, NOW(), NOW())")) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                        "identifier_ciphertext, blind_index, encryption_key_version, blind_index_key_version, status, created_at) " +
                        "VALUES (?, ?, ?, 'NATIONAL_ID', 'SA', 'enc:test', 'blind:test', 'v1', 'v1', 'ACTIVE', NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, personId);
                    ps.executeUpdate();
                }
            }
            case "hr_employment_status_periods" -> {
                UUID empId = seedHrRow("hr_employees", tenantId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_employment_status_periods (id, tenant_id, employment_id, status, effective_from, created_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', '2026-01-01'::date, NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, empId);
                    ps.executeUpdate();
                }
            }
            case "hr_migration_tenant_state" -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at) VALUES (?, 'LEGACY', NOW())")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                }
                return tenantId;
            }
            case "hr_legacy_employee_mappings" -> {
                UUID empId = seedHrRow("hr_employees", tenantId);
                UUID personId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, 'RLS', 'Map', 'RLS Map', 0, NOW(), NOW())")) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_legacy_employee_mappings (id, tenant_id, legacy_employee_id, canonical_person_id, classification, created_at) " +
                        "VALUES (?, ?, ?, ?, 'AUTO_MIGRATE', NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, empId);
                    ps.setObject(4, personId);
                    ps.executeUpdate();
                }
            }
            case "hr_org_units" -> {
                UUID orgId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'RLS Org', 'ACTIVE', NOW(), NOW())")) {
                    ps.setObject(1, orgId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) VALUES (?, ?, ?, ?, NOW())")) {
                    ps.setObject(1, rowId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, orgId);
                    ps.setString(4, "OU-" + rowId.toString().substring(0, 8));
                    ps.executeUpdate();
                }
            }
            default -> throw new IllegalArgumentException("Unknown table: " + table);
        }
        return rowId;
    }

    /** Get the tenant_id column value from a table for a given row. */
    private UUID getTenantIdColumn(String table, UUID rowId) throws Exception {
        String idCol = switch (table) {
            case "hr_person_private", "hr_migration_tenant_state" -> "person_id"; // private uses person_id as PK
            default -> "id";
        };
        // For hr_migration_tenant_state, the PK is tenant_id
        if (table.equals("hr_migration_tenant_state")) return rowId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tenant_id FROM " + table + " WHERE " + idCol + " = ?")) {
            ps.setObject(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject("tenant_id", UUID.class) : null;
            }
        }
    }

    // ==================== TEST DATA PROVIDERS ====================

    /** All HR tenant tables that have a seedable row. */
    static Stream<String> hrTenantTables() {
        return Stream.of(
                "hr_employees",
                "hr_departments",
                "hr_positions",
                "hr_people",
                "hr_person_private",
                "hr_person_identifiers",
                "hr_employment_status_periods",
                "hr_migration_tenant_state",
                "hr_legacy_employee_mappings",
                "hr_org_units"
        );
    }

    // ==================== 1. RUNTIME ROLE SECURITY ====================

    @Test
    void runtimeRole_isNotSuperuser() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolsuper"))
                        .as("RLS tests must run as non-superuser role")
                        .isFalse();
            }
        }
    }

    @Test
    void runtimeRole_doesNotHaveBypassrls() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolbypassrls"))
                        .as("RLS tests must run as role without BYPASSRLS")
                        .isFalse();
            }
        }
    }

    // ==================== 2. CATALOG: RLS ENABLED + FORCED ====================

    @ParameterizedTest
    @MethodSource("hrTenantTables")
    void catalog_rlsEnabledAndForced(String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("table %s must exist", table)
                        .isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("table %s must have ENABLE ROW LEVEL SECURITY", table)
                        .isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("table %s must have FORCE ROW LEVEL SECURITY (owner must not bypass)", table)
                        .isTrue();
            }
        }
    }

    // ==================== 3. CATALOG: NO FAIL-OPEN POLICY EXPRESSION ====================

    @ParameterizedTest
    @MethodSource("hrTenantTables")
    void catalog_policyIsFailClosed(String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT policyname, qual, with_check FROM pg_policies WHERE tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("table %s must have at least one RLS policy", table)
                        .isTrue();
                do {
                    String qual = rs.getString("qual");
                    String withCheck = rs.getString("with_check");
                    if (qual != null) {
                        assertThat(qual)
                                .as("table %s policy %s USING must not contain null-context allow ('IS NULL OR')", table, rs.getString("policyname"))
                                .doesNotContain("IS NULL OR");
                    }
                    if (withCheck != null) {
                        assertThat(withCheck)
                                .as("table %s policy %s WITH CHECK must not contain null-context allow", table, rs.getString("policyname"))
                                .doesNotContain("IS NULL OR");
                    }
                } while (rs.next());
            }
        }
    }

    // ==================== 4. RUNTIME: CORRECT TENANT READ (positive control) ====================

    @ParameterizedTest
    @MethodSource("hrTenantTables")
    void correctTenant_canReadOwnRow(String table) throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        setTenant(tenantA);
        UUID rowId = seedHrRow(table, tenantA);

        // Verify row is visible under correct tenant context
        String idCol = table.equals("hr_person_private") ? "person_id"
                : table.equals("hr_migration_tenant_state") ? "tenant_id" : "id";
        int count = countRows(table, idCol, rowId);
        assertThat(count)
                .as("table %s: correct tenant must see its own row (positive control)", table)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 5. RUNTIME: NO CONTEXT READ (CRITICAL RED) ====================

    @ParameterizedTest
    @MethodSource("hrTenantTables")
    void noTenantContext_seesZeroRows(String table) throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        setTenant(tenantA);
        UUID rowId = seedHrRow(table, tenantA);

        // Positive control: row must exist under correct tenant
        String idCol = table.equals("hr_person_private") ? "person_id"
                : table.equals("hr_migration_tenant_state") ? "tenant_id" : "id";
        assertThat(countRows(table, idCol, rowId))
                .as("table %s: fixture validity — row must exist before testing no-context", table)
                .isGreaterThanOrEqualTo(1);

        // Reset tenant context — fail-closed RLS must return 0 rows
        resetTenant();
        assertThat(countRows(table, idCol, rowId))
                .as("table %s: NO tenant context must see ZERO rows (fail-closed)", table)
                .isZero();
    }

    // ==================== 6. RUNTIME: WRONG TENANT READ ====================

    @ParameterizedTest
    @MethodSource("hrTenantTables")
    void wrongTenant_seesZeroRows(String table) throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID rowId = seedHrRow(table, tenantA);

        // Positive control
        String idCol = table.equals("hr_person_private") ? "person_id"
                : table.equals("hr_migration_tenant_state") ? "tenant_id" : "id";
        assertThat(countRows(table, idCol, rowId))
                .as("table %s: fixture validity — row must exist under tenant A", table)
                .isGreaterThanOrEqualTo(1);

        // Switch to tenant B — must see zero rows
        setTenant(tenantB);
        assertThat(countRows(table, idCol, rowId))
                .as("table %s: wrong tenant must see ZERO rows", table)
                .isZero();
    }

    // ==================== 7. RUNTIME: NO CONTEXT WRITE — LEGACY CORE ====================

    @Test
    void noTenantContext_hrEmployees_insertDenied() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        resetTenant();

        // INSERT without tenant context must be DENIED by RLS
        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, version, created_at, updated_at) " +
                "VALUES (?, ?, 'RLS-NOCTX', 'Test', 'NoCtx', 'Test NoCtx', 'FULL_TIME', 'ACTIVE', 0, NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantA);
            ps.executeUpdate();
        } catch (SQLException e) {
            denied = true;
            // Verify it's an RLS violation, not FK/CHECK/NOT NULL
            assertThat(e.getMessage())
                    .as("no-context INSERT on hr_employees must fail due to RLS policy violation")
                    .containsIgnoringCase("row-level security");
        }
        assertThat(denied)
                .as("no-context INSERT on hr_employees must be DENIED by RLS (or fail with RLS violation)")
                .isTrue();
    }

    @Test
    void noTenantContext_hrDepartments_insertDenied() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        resetTenant();

        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_departments (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 'RLS NoCtx Dept', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantA);
            ps.executeUpdate();
        } catch (SQLException e) {
            denied = true;
            assertThat(e.getMessage())
                    .as("no-context INSERT on hr_departments must fail due to RLS policy violation")
                    .containsIgnoringCase("row-level security");
        }
        assertThat(denied).isTrue();
    }

    @Test
    void noTenantContext_hrPositions_insertDenied() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        resetTenant();

        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, status, created_at, updated_at) " +
                "VALUES (?, ?, 'RLS NoCtx Pos', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantA);
            ps.executeUpdate();
        } catch (SQLException e) {
            denied = true;
            assertThat(e.getMessage())
                    .as("no-context INSERT on hr_positions must fail due to RLS policy violation")
                    .containsIgnoringCase("row-level security");
        }
        assertThat(denied).isTrue();
    }

    // ==================== 8. RUNTIME: WRONG TENANT WRITE ====================

    @Test
    void wrongTenant_hrEmployees_insertDenied() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);

        // Context = Tenant B, but INSERT row with tenant_id = Tenant A → DENIED
        setTenant(tenantB);
        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, version, created_at, updated_at) " +
                "VALUES (?, ?, 'RLS-CROSS', 'Test', 'Cross', 'Test Cross', 'FULL_TIME', 'ACTIVE', 0, NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantA); // tenant_id = A, context = B
            ps.executeUpdate();
        } catch (SQLException e) {
            denied = true;
            assertThat(e.getMessage())
                    .as("cross-tenant INSERT on hr_employees must fail due to RLS WITH CHECK violation")
                    .containsIgnoringCase("row-level security");
        }
        assertThat(denied).isTrue();
    }

    // ==================== 9. RUNTIME: WRONG TENANT UPDATE ====================

    @Test
    void wrongTenant_hrEmployees_updateAffectsZeroRows() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID empId = seedHrRow("hr_employees", tenantA);

        // Switch to tenant B and try to update tenant A's row
        setTenant(tenantB);
        int updated;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE hr_employees SET first_name = 'HACKED' WHERE id = ?")) {
            ps.setObject(1, empId);
            updated = ps.executeUpdate();
        }
        assertThat(updated)
                .as("wrong tenant UPDATE on hr_employees must affect 0 rows (RLS USING filter)")
                .isZero();
    }

    // ==================== 10. RUNTIME: WRONG TENANT DELETE ====================

    @Test
    void wrongTenant_hrEmployees_deleteAffectsZeroRows() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID empId = seedHrRow("hr_employees", tenantA);

        setTenant(tenantB);
        int deleted;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM hr_employees WHERE id = ?")) {
            ps.setObject(1, empId);
            deleted = ps.executeUpdate();
        }
        assertThat(deleted)
                .as("wrong tenant DELETE on hr_employees must affect 0 rows (RLS USING filter)")
                .isZero();
    }

    // ==================== 11. SENSITIVE TABLES (PII) ====================

    @Test
    void sensitive_hrPersonPrivate_noContextSeesZero() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        setTenant(tenantA);
        UUID personId = seedHrRow("hr_person_private", tenantA);

        // Positive control
        assertThat(countRows("hr_person_private", "person_id", personId)).isGreaterThanOrEqualTo(1);

        resetTenant();
        assertThat(countRows("hr_person_private", "person_id", personId))
                .as("hr_person_private: NO tenant context must see ZERO rows (sensitive PII)")
                .isZero();
    }

    @Test
    void sensitive_hrPersonIdentifiers_noContextSeesZero() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);
        setTenant(tenantA);
        UUID identId = seedHrRow("hr_person_identifiers", tenantA);

        assertThat(countRows("hr_person_identifiers", "id", identId)).isGreaterThanOrEqualTo(1);

        resetTenant();
        assertThat(countRows("hr_person_identifiers", "id", identId))
                .as("hr_person_identifiers: NO tenant context must see ZERO rows (sensitive identity)")
                .isZero();
    }

    // ==================== HELPER: COUNT ROWS ====================

    private int countRows(String table, String idCol, UUID rowId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idCol + " = ?")) {
            ps.setObject(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
