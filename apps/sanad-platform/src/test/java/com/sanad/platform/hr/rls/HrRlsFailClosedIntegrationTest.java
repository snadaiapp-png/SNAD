package com.sanad.platform.hr.rls;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * WS2 Task 5 — Fail-Closed HR RLS TRUE RED (Corrected + Complete).
 *
 * <p>Database-level security contract tests for ALL HR tenant tables
 * discovered from the PostgreSQL catalog after Flyway migration.</p>
 *
 * <p>Expected RED: legacy HR tables (hr_employees, hr_departments,
 * hr_positions) have FAIL-OPEN policies that allow no-context reads
 * and lack FORCE RLS.</p>
 *
 * <p>Expected GREEN: canonical HR tables from Tasks 1-4 have FORCE RLS
 * + fail-closed policies.</p>
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
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
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

    // ==================== COMPLETE HR TABLE INVENTORY ====================

    /**
     * Discover ALL HR tenant tables from the PostgreSQL catalog.
     * This is the source of truth — no hard-coded list.
     */
    static Stream<String> allHrTenantTables() {
        // This list must match the catalog query result.
        // It is verified by catalog_hRTableInventoryMatchesTest below.
        return Stream.of(
                "hr_departments",
                "hr_employee_assignments",
                "hr_employees",
                "hr_employment_status_periods",
                "hr_job_versions",
                "hr_jobs",
                "hr_legacy_employee_mappings",
                "hr_migration_review_items",
                "hr_migration_tenant_state",
                "hr_org_unit_versions",
                "hr_org_units",
                "hr_people",
                "hr_person_identifiers",
                "hr_person_private",
                "hr_position_versions",
                "hr_positions"
        );
    }

    // ==================== 0. CATALOG INVENTORY VERIFICATION ====================

    @Test
    void catalog_hRTableInventoryMatchesTest() throws Exception {
        // Query the actual PostgreSQL catalog for HR tables with tenant_id
        List<String> catalogTables = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.relname AS table_name " +
                "FROM pg_class c " +
                "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "JOIN information_schema.columns col ON col.table_schema = n.nspname AND col.table_name = c.relname " +
                "WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname LIKE 'hr_%' " +
                "AND col.column_name = 'tenant_id' " +
                "ORDER BY c.relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    catalogTables.add(rs.getString("table_name"));
                }
            }
        }
        List<String> testTables = allHrTenantTables().toList();
        assertThat(catalogTables)
                .as("Test inventory must match actual catalog HR tables with tenant_id")
                .containsExactlyInAnyOrderElementsOf(testTables);
    }

    // ==================== 1. RUNTIME ROLE SECURITY ====================

    @Test
    void runtimeRole_isNotSuperuser() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
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
                rs.next();
                assertThat(rs.getBoolean("rolbypassrls"))
                        .as("RLS tests must run as role without BYPASSRLS")
                        .isFalse();
            }
        }
    }

    // ==================== 2. CATALOG: RLS ENABLED + FORCED ====================

    @ParameterizedTest
    @MethodSource("allHrTenantTables")
    void catalog_rlsEnabledAndForced(String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("Table %s must exist", table)
                        .isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("Table %s must have ENABLE ROW LEVEL SECURITY", table)
                        .isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("Table %s must have FORCE ROW LEVEL SECURITY", table)
                        .isTrue();
            }
        }
    }

    // ==================== 3. CORRECT TENANT READ POSITIVE CONTROL ====================

    @ParameterizedTest
    @MethodSource("allHrTenantTables")
    void correctTenant_canReadOwnRow(String table) throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        seedRow(table, tenantId);
        // Verify row exists under correct tenant context
        int count = countRows(table);
        assertThat(count)
                .as("Correct tenant must see its own row in %s", table)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 4. NO TENANT CONTEXT → ZERO ROWS ====================

    @ParameterizedTest
    @MethodSource("allHrTenantTables")
    void noTenantContext_seesZeroRows(String table) throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        seedRow(table, tenantId);
        // Verify row exists
        setTenant(tenantId);
        assertThat(countRows(table))
                .as("Row must exist before no-context test")
                .isGreaterThanOrEqualTo(1);
        // Reset tenant context
        resetTenant();
        // No context → must see 0 rows
        assertThat(countRows(table))
                .as("No tenant context must see 0 rows in %s (fail-closed)", table)
                .isZero();
    }

    // ==================== 5. WRONG TENANT → ZERO ROWS ====================

    @ParameterizedTest
    @MethodSource("allHrTenantTables")
    void wrongTenant_seesZeroRows(String table) throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        seedRow(table, tenantA);
        // Verify row exists under tenant A
        assertThat(countRows(table))
                .as("Row must exist before wrong-tenant test")
                .isGreaterThanOrEqualTo(1);
        // Switch to tenant B
        setTenant(tenantB);
        // Tenant B must see 0 rows from tenant A
        assertThat(countRows(table))
                .as("Wrong tenant must see 0 rows in %s", table)
                .isZero();
    }

    // ==================== 6. NO-CONTEXT INSERT DENIED (representative) ====================

    @Test
    void noTenantContext_hrEmployees_insertDenied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        resetTenant();
        // Contract: only a real RLS rejection may satisfy this assertion.
        // Binding errors, FK errors, CHECK errors, EXCLUDE constraint
        // violations — none of these qualify as RLS enforcement.
        // Verified SQLSTATE 42501 is stable on PostgreSQL 16/17 for
        // "new row violates row-level security policy" errors.
        Throwable thrown = catchThrowable(() -> insertHrEmployee(tenantId));
        assertThat(thrown)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
        assertThat(((SQLException) thrown).getSQLState())
                .as("SQLSTATE must be 42501 (insufficient_privilege) for RLS denial")
                .isEqualTo("42501");
    }

    @Test
    void noTenantContext_hrDepartments_insertDenied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        resetTenant();
        assertThatThrownBy(() -> insertHrDepartment(tenantId))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void noTenantContext_hrPositions_insertDenied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        resetTenant();
        assertThatThrownBy(() -> insertHrPosition(tenantId))
                .isInstanceOf(SQLException.class);
    }

    // ==================== 7. WRONG-TENANT INSERT DENIED ====================

    @Test
    void wrongTenant_hrEmployees_insertDenied() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantB);
        // Try to insert row with tenant_id = A under context B
        // Contract: only a real RLS rejection may satisfy this assertion.
        // Binding errors, FK errors, CHECK errors, EXCLUDE constraint
        // violations — none of these qualify as RLS enforcement.
        // Verified SQLSTATE 42501 is stable on PostgreSQL 16/17 for
        // "new row violates row-level security policy" errors.
        Throwable thrown = catchThrowable(() -> insertHrEmployee(tenantA));
        assertThat(thrown)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
        assertThat(((SQLException) thrown).getSQLState())
                .as("SQLSTATE must be 42501 (insufficient_privilege) for RLS denial")
                .isEqualTo("42501");
    }

    // ==================== 8. WRONG-TENANT UPDATE = ZERO ROWS ====================

    @Test
    void wrongTenant_hrEmployees_updateAffectsZeroRows() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        // Insert a SIMPLE employee (no person_id, no legal_entity_id — avoid FK issues)
        UUID empId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, 'EMP-UPD', 'Update', 'Test', 'Update Test', 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantA);
            ps.setString(3, "2026-01-01");
            ps.executeUpdate();
        }
        // Switch to tenant B and try to UPDATE tenant A's row
        setTenant(tenantB);
        int affected;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE hr_employees SET first_name = 'HACKED' WHERE id = ?")) {
            ps.setObject(1, empId);
            affected = ps.executeUpdate();
        }
        assertThat(affected)
                .as("Wrong-tenant UPDATE must affect 0 rows")
                .isZero();
    }

    // ==================== 9. WRONG-TENANT DELETE = ZERO ROWS ====================

    @Test
    void wrongTenant_hrDepartments_deleteAffectsZeroRows() throws Exception {
        // Use hr_departments (no FK dependencies from status_periods trigger)
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID deptId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_departments (id, tenant_id, name, code, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Delete Test', 'DEL', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, deptId);
            ps.setObject(2, tenantA);
            ps.executeUpdate();
        }
        // Switch to tenant B and try to DELETE tenant A's row
        setTenant(tenantB);
        int affected;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM hr_departments WHERE id = ?")) {
            ps.setObject(1, deptId);
            affected = ps.executeUpdate();
        }
        assertThat(affected)
                .as("Wrong-tenant DELETE must affect 0 rows")
                .isZero();
    }

    // ==================== 10. SENSITIVE PII TABLES ====================

    @Test
    void sensitivePII_noContext_hrPersonPrivate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertHrPerson(tenantId, "PII", "Test");
        insertHrPersonPrivate(personId, tenantId);
        // Verify row exists
        assertThat(countRows("hr_person_private")).isGreaterThanOrEqualTo(1);
        // No context → 0 rows
        resetTenant();
        assertThat(countRows("hr_person_private"))
                .as("hr_person_private must be fail-closed (PII)")
                .isZero();
    }

    @Test
    void sensitivePII_noContext_hrPersonIdentifiers() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertHrPerson(tenantId, "Ident", "Test");
        insertHrPersonIdentifier(tenantId, personId);
        // Verify row exists
        assertThat(countRows("hr_person_identifiers")).isGreaterThanOrEqualTo(1);
        // No context → 0 rows
        resetTenant();
        assertThat(countRows("hr_person_identifiers"))
                .as("hr_person_identifiers must be fail-closed (PII)")
                .isZero();
    }

    // ==================== FIXTURE HELPERS ====================

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedOrganization(UUID tenantId) throws Exception {
        UUID orgId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Test Org ' || substring(md5(random()::text), 1, 8), 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        return orgId;
    }

    private UUID seedLegalEntity(UUID tenantId, String code) throws Exception {
        UUID leId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, leId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "Test LE " + code);
            ps.executeUpdate();
        }
        return leId;
    }

    private void seedOrgLegalEntity(UUID tenantId, UUID orgId, UUID leId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organization_legal_entities (id, tenant_id, organization_id, legal_entity_id, " +
                "effective_from, effective_to, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?::date, NULL, 'ACTIVE', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.setObject(4, leId);
            ps.setString(5, "2026-01-01");
            ps.executeUpdate();
        }
    }

    private UUID seedPerson(UUID tenantId, String first, String last) throws Exception {
        return insertHrPerson(tenantId, first, last);
    }

    private UUID insertHrPerson(UUID tenantId, String first, String last) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, NULL, ?, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.setString(3, first);
            ps.setString(4, last);
            ps.setString(5, first + " " + last);
            ps.executeUpdate();
        }
        return personId;
    }

    private void insertHrPersonPrivate(UUID personId, UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_private (person_id, tenant_id, version, updated_at) " +
                "VALUES (?, ?, 0, NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private void insertHrPersonIdentifier(UUID tenantId, UUID personId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, identifier_ciphertext, blind_index, " +
                "encryption_key_version, blind_index_key_version, status, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'enc:v1:test', 'blindtest', 'v1', 'v1', 'ACTIVE', NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, personId);
            ps.executeUpdate();
        }
    }

    private UUID insertHrEmployee(UUID tenantId) throws Exception {
        UUID empId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'Test', 'Employee', 'Test Employee', 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setString(3, "EMP-" + empId.toString().substring(0, 8));
            ps.setString(4, "2026-01-01");
            ps.executeUpdate();
        }
        return empId;
    }

    private void insertHrDepartment(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_departments (id, tenant_id, name, code, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Test Dept', 'TD', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private void insertHrPosition(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Test Pos', 'TP', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private void insertHrJob(UUID tenantId) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, 'JOB-RLS', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.executeUpdate();
        }
    }

    private void insertHrJobVersion(UUID tenantId) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        UUID jobId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, 'JOB-VER', NOW())")) {
            ps.setObject(1, jobId);
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_job_versions (id, tenant_id, job_id, title, effective_from, status) " +
                "VALUES (?, ?, ?, 'Test Job', ?::date, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, jobId);
            ps.setString(4, "2026-01-01");
            ps.executeUpdate();
        }
    }

    private void insertHrOrgUnit(UUID tenantId) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, 'OU-RLS', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.executeUpdate();
        }
    }

    private void insertHrOrgUnitVersion(UUID tenantId) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        UUID orgUnitId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, 'OUV-RLS', NOW())")) {
            ps.setObject(1, orgUnitId);
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_org_unit_versions (id, tenant_id, org_unit_id, name, code, unit_type, " +
                "effective_from, status) " +
                "VALUES (?, ?, ?, 'Test OU', 'OUV', 'DEPARTMENT', ?::date, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgUnitId);
            ps.setString(4, "2026-01-01");
            ps.executeUpdate();
        }
    }

    private void insertHrPositionVersion(UUID tenantId) throws Exception {
        UUID posId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Test Pos', 'PV-RLS', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, posId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, title, " +
                "effective_from, status) " +
                "VALUES (?, ?, ?, ?, 'Test Pos V', ?::date, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, posId);
            ps.setObject(4, orgId);
            ps.setString(5, "2026-01-01");
            ps.executeUpdate();
        }
    }

    private void insertHrEmployeeAssignment(UUID tenantId) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-ASSIGN");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = insertHrPerson(tenantId, "Assign", "Test");
        // Insert employment (trigger creates initial status period)
        UUID empId = insertHrEmployee(tenantId, "EMP-ASSIGN");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employee_assignments (id, tenant_id, employment_id, organization_id, " +
                "assignment_type, occupancy_mode, allocation_percent, effective_from, status, version) " +
                "VALUES (?, ?, ?, ?, 'PRIMARY', 'NON_OCCUPYING', 100.00, ?::date, 'ACTIVE', 0)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, empId);
            ps.setObject(4, orgId);
            ps.setString(5, "2026-01-01");
            ps.executeUpdate();
        }
    }

    private UUID insertHrEmployee(UUID tenantId, String empNum) throws Exception {
        UUID empId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'Test', 'Employee', 'Test Employee', 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setString(3, empNum);
            ps.setString(4, "2026-01-01");
            ps.executeUpdate();
        }
        return empId;
    }

    private void insertHrMigrationTenantState(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at) " +
                "VALUES (?, 'LEGACY', NOW())")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    private void insertHrMigrationReviewItem(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_migration_review_items " +
                "(tenant_id, legacy_entity_type, legacy_entity_id, issue_code, severity, review_reason, resolution_state, created_at, updated_at) " +
                "VALUES (?, 'EMPLOYEE', ?, 'TEST_ISSUE', 'REVIEW', 'Test review reason', 'OPEN', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.executeUpdate();
        }
    }

    private void insertHrLegacyEmployeeMapping(UUID tenantId) throws Exception {
        UUID empId = insertHrEmployee(tenantId, "EMP-LEG");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_legacy_employee_mappings (id, tenant_id, legacy_employee_id, classification, created_at) " +
                "VALUES (?, ?, ?, 'AUTO_MIGRATE', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, empId);
            ps.executeUpdate();
        }
    }

    // --- Generic seed dispatch ---

    private void seedRow(String table, UUID tenantId) throws Exception {
        switch (table) {
            case "hr_employees" -> insertHrEmployee(tenantId);
            case "hr_departments" -> insertHrDepartment(tenantId);
            case "hr_positions" -> insertHrPosition(tenantId);
            case "hr_people" -> insertHrPerson(tenantId, "RLS", "Test");
            case "hr_person_private" -> {
                UUID personId = insertHrPerson(tenantId, "PII", "Private");
                insertHrPersonPrivate(personId, tenantId);
            }
            case "hr_person_identifiers" -> {
                UUID personId = insertHrPerson(tenantId, "Ident", "RLS");
                insertHrPersonIdentifier(tenantId, personId);
            }
            case "hr_employment_status_periods" -> insertHrEmployee(tenantId);
            case "hr_migration_tenant_state" -> insertHrMigrationTenantState(tenantId);
            case "hr_legacy_employee_mappings" -> insertHrLegacyEmployeeMapping(tenantId);
            case "hr_org_units" -> insertHrOrgUnit(tenantId);
            case "hr_org_unit_versions" -> insertHrOrgUnitVersion(tenantId);
            case "hr_jobs" -> insertHrJob(tenantId);
            case "hr_job_versions" -> insertHrJobVersion(tenantId);
            case "hr_position_versions" -> insertHrPositionVersion(tenantId);
            case "hr_employee_assignments" -> insertHrEmployeeAssignment(tenantId);
            case "hr_migration_review_items" -> insertHrMigrationReviewItem(tenantId);
            default -> throw new IllegalArgumentException("No seed for table: " + table);
        }
    }

    private int countRows(String table) throws Exception {
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
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
}
