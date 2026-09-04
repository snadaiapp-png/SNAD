package com.sanad.platform.hr.foundation;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS2 Task 6 — Plan Conformance Integration Test.
 *
 * <p>Covers gaps not fully exercised by the existing 19-test
 * HrCanonicalBackfillIntegrationTest:</p>
 * <ul>
 *   <li>Authoritative Legal Entity assignment on hr_employees</li>
 *   <li>Multiple Legal Entity ambiguity (2 LEs → 1 org)</li>
 *   <li>Effective-dated eligibility (future/inactive eligibility excluded)</li>
 *   <li>Department canonical backfill (hr_departments → hr_org_units)</li>
 *   <li>Position canonical backfill (hr_positions → hr_position_versions)</li>
 *   <li>Manager resolution (reports_to_assignment_id)</li>
 *   <li>Open review items block CANONICAL state</li>
 *   <li>Machine-readable reconciliation (function returns TABLE)</li>
 *   <li>SECURITY DEFINER: PUBLIC execute revoked</li>
 * </ul>
 */
class HrCanonicalBackfillPlanConformanceIntegrationTest {

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

    // ==================== 1. LEGAL ENTITY ASSIGNMENT ====================

    /**
     * DIRECT RED: After backfill, hr_employees.legal_entity_id MUST be
     * populated with the authoritative legal entity for AUTO_MIGRATE employees.
     */
    @Test
    void legalEntityAssignment_populatesHrEmployeesLegalEntity() throws Exception {
        UUID tenantId = seedTenantA();
        invokeBackfill(tenantId);

        // Contract: at least 1 employee must have legal_entity_id set
        int withLegalEntity = countEmployeesWithLegalEntity(tenantId);
        assertThat(withLegalEntity)
                .as("At least 1 AUTO_MIGRATE employee MUST have legal_entity_id populated — DIRECT RED")
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 2. MULTIPLE LEGAL ENTITY AMBIGUITY ====================

    /**
     * DIRECT RED: 2 ACTIVE Legal Entities linked to 1 Organization MUST
     * produce MIGRATION_REVIEW_REQUIRED, not AUTO_MIGRATE.
     */
    @Test
    void multipleLegalEntities_producesReviewRequired() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Multi-LE Org");
        UUID le1 = seedLegalEntity(tenantId, "LE-1");
        UUID le2 = seedLegalEntity(tenantId, "LE-2");
        seedOrgLegalEntity(tenantId, orgId, le1);
        seedOrgLegalEntity(tenantId, orgId, le2); // Second LE → ambiguous
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-MLE", "Multi", "LE");

        invokeBackfill(tenantId);

        assertThat(getMigrationState(tenantId))
                .as("Multiple Legal Entities MUST produce BLOCKED — DIRECT RED")
                .isEqualTo("BLOCKED");
    }

    // ==================== 3. EFFECTIVE-DATED ELIGIBILITY ====================

    /**
     * DIRECT RED: Future eligibility (effective_from > as_of_date) MUST
     * NOT be counted as eligible.
     */
    @Test
    void futureEligibility_notCountedAsEligible() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Future-Elig Org");
        UUID leId = seedLegalEntity(tenantId, "LE-FUT");
        // Seed eligibility with future effective_from
        seedOrgLegalEntityWithDates(tenantId, orgId, leId, "2027-01-01", null);
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-FUT", "Future", "Elig");

        invokeBackfill(tenantId);

        // Future eligibility → not eligible → BLOCKED
        assertThat(getMigrationState(tenantId))
                .as("Future eligibility MUST NOT be counted — tenant MUST be BLOCKED — DIRECT RED")
                .isEqualTo("BLOCKED");
    }

    // ==================== 4. DEPARTMENT CANONICAL BACKFILL ====================

    /**
     * DIRECT RED: Legacy department MUST be backfilled to hr_org_units.
     */
    @Test
    void departmentBackfill_createsCanonicalOrgUnit() throws Exception {
        UUID tenantId = seedTenantA();
        UUID deptId = seedLegacyDepartment(tenantId, "Eng Dept", "ENG");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployeeWithDept(tenantId, userId, "EMP-DEPT-BF", "Dept", "BF", deptId);

        invokeBackfill(tenantId);

        // Contract: at least 1 canonical hr_org_unit must exist
        int orgUnitCount = countOrgUnits(tenantId);
        assertThat(orgUnitCount)
                .as("Department backfill MUST create at least 1 hr_org_unit — DIRECT RED")
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 5. POSITION CANONICAL BACKFILL ====================

    /**
     * DIRECT RED: Legacy position MUST be backfilled to canonical position version.
     */
    @Test
    void positionBackfill_createsCanonicalPositionVersion() throws Exception {
        UUID tenantId = seedTenantA();
        UUID posId = seedLegacyPosition(tenantId, "Eng Position", "ENG-POS");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployeeWithPosition(tenantId, userId, "EMP-POS-BF", "Pos", "BF", posId);

        invokeBackfill(tenantId);

        // Contract: at least 1 canonical hr_position_version must exist
        int posVerCount = countPositionVersionForLegacyPositions(tenantId);
        assertThat(posVerCount)
                .as("Position backfill MUST create at least 1 hr_position_version — DIRECT RED")
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 6. MANAGER RESOLUTION ====================

    /**
     * DIRECT RED: After backfill, subordinate assignment MUST have
     * reports_to_assignment_id pointing to manager's PRIMARY assignment.
     */
    @Test
    void managerResolution_linksReportsToAssignment() throws Exception {
        UUID tenantId = seedTenantA();
        UUID managerUserId = UUID.randomUUID();
        seedUser(tenantId, managerUserId);
        UUID managerEmpId = seedLegacyEmployee(tenantId, managerUserId, "EMP-MGR-R", "Manager", "R");
        UUID subUserId = UUID.randomUUID();
        seedUser(tenantId, subUserId);
        seedLegacyEmployeeWithManager(tenantId, subUserId, "EMP-SUB-R", "Sub", "R", managerEmpId);

        invokeBackfill(tenantId);

        // Contract: at least 1 assignment must have reports_to_assignment_id set
        int withReportsTo = countAssignmentsWithReportsTo(tenantId);
        assertThat(withReportsTo)
                .as("Manager resolution MUST set reports_to_assignment_id on at least 1 assignment — DIRECT RED")
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 7. OPEN REVIEW ITEMS BLOCK CANONICAL ====================

    /**
     * DIRECT RED: If OPEN review items exist, tenant MUST NOT be CANONICAL.
     */
    @Test
    void openReviewItems_blockCanonicalState() throws Exception {
        UUID tenantId = seedTenantA();

        invokeBackfill(tenantId);

        // After backfill, if state is CANONICAL, there MUST be 0 open review items
        String state = getMigrationState(tenantId);
        if ("CANONICAL".equals(state)) {
            int openItems = countOpenReviewItems(tenantId);
            assertThat(openItems)
                    .as("CANONICAL state requires 0 OPEN review items — DIRECT RED")
                    .isZero();
        } else {
            // If not CANONICAL, this test proves the gap exists
            assertThat(state)
                    .as("Tenant A MUST reach CANONICAL after clean backfill — DIRECT RED")
                    .isEqualTo("CANONICAL");
        }
    }

    // ==================== 8. MACHINE-READABLE RECONCILIATION ====================

    /**
     * DIRECT RED: Reconciliation MUST return machine-readable result
     * (not just VOID). The function hr_reconcile_tenant_report must exist.
     */
    @Test
    void reconciliationReturnsMachineReadableResult() throws Exception {
        UUID tenantId = seedTenantA();
        invokeBackfill(tenantId);

        // Contract: hr_reconcile_tenant_report function must exist and return rows
        setTenant(tenantId);
        int rowCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM hr_reconcile_tenant_report(?)")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                }
            }
        } catch (SQLException e) {
            // Function doesn't exist in RED
            if (isUndefinedFunction(e)) {
                rowCount = 0;
            } else {
                throw e;
            }
        }

        assertThat(rowCount)
                .as("Reconciliation MUST return machine-readable rows (found %d) — DIRECT RED", rowCount)
                .isGreaterThan(0);
    }

    // ==================== 9. SECURITY DEFINER — PUBLIC EXECUTE REVOKED ====================

    /**
     * DIRECT RED: PUBLIC must NOT have EXECUTE on hr_backfill_tenant.
     */
    @Test
    void publicExecuteRevoked_onBackfillFunctions() throws Exception {
        setTenant(UUID.randomUUID());
        int publicCanExecute = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.role_routine_grants " +
                "WHERE routine_name IN ('hr_backfill_tenant', 'hr_precheck_tenant', 'hr_reconcile_tenant') " +
                "AND grantee = 'PUBLIC'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                publicCanExecute = rs.getInt(1);
            }
        }

        assertThat(publicCanExecute)
                .as("PUBLIC must NOT have EXECUTE on backfill functions (found %d grants) — DIRECT RED",
                        publicCanExecute)
                .isZero();
    }

    // ==================== FIXTURE HELPERS — TENANT A (reused from existing test) ====================

    private UUID seedTenantA() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedEligibleEmploymentContext(tenantId, "LE-A", "Tenant A Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-A-" + tenantId.toString().substring(0, 8), "TenantA", "Employee");
        return tenantId;
    }

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, 'Test', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void seedUser(UUID tenantId, UUID userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, display_name, status, created_at, updated_at, " +
                "must_change_password, session_version, platform_admin) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW(), false, 0, false)")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, "user-" + userId.toString().substring(0, 8) + "@test.example");
            ps.setString(4, "Test User " + userId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedOrganization(UUID tenantId, String name) throws Exception {
        UUID orgId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.setString(3, name + " " + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
        return orgId;
    }

    private UUID seedLegalEntity(UUID tenantId, String code) throws Exception {
        UUID leId = UUID.randomUUID();
        setTenant(tenantId);
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
        setTenant(tenantId);
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

    private void seedOrgLegalEntityWithDates(UUID tenantId, UUID orgId, UUID leId, String effFrom, String effTo) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organization_legal_entities (id, tenant_id, organization_id, legal_entity_id, " +
                "effective_from, effective_to, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?::date, ?::date, 'ACTIVE', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.setObject(4, leId);
            ps.setString(5, effFrom);
            ps.setString(6, effTo);
            ps.executeUpdate();
        }
    }

    private UUID seedEligibleEmploymentContext(UUID tenantId, String leCode, String orgName) throws Exception {
        UUID orgId = seedOrganization(tenantId, orgName);
        UUID leId = seedLegalEntity(tenantId, leCode);
        seedOrgLegalEntity(tenantId, orgId, leId);
        return orgId;
    }

    private UUID seedLegacyEmployee(UUID tenantId, UUID userId, String empNum, String first, String last) throws Exception {
        UUID empId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, empNum);
            ps.setString(5, first);
            ps.setString(6, last);
            ps.setString(7, first + " " + last);
            ps.setString(8, "2026-01-01");
            ps.executeUpdate();
        }
        return empId;
    }

    private UUID seedLegacyEmployeeWithDept(UUID tenantId, UUID userId, String empNum, String first, String last, UUID deptId) throws Exception {
        UUID empId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, department_id, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, empNum);
            ps.setString(5, first);
            ps.setString(6, last);
            ps.setString(7, first + " " + last);
            ps.setString(8, "2026-01-01");
            ps.setObject(9, deptId);
            ps.executeUpdate();
        }
        return empId;
    }

    private UUID seedLegacyEmployeeWithPosition(UUID tenantId, UUID userId, String empNum, String first, String last, UUID posId) throws Exception {
        UUID empId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, position_id, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, empNum);
            ps.setString(5, first);
            ps.setString(6, last);
            ps.setString(7, first + " " + last);
            ps.setString(8, "2026-01-01");
            ps.setObject(9, posId);
            ps.executeUpdate();
        }
        return empId;
    }

    private UUID seedLegacyEmployeeWithManager(UUID tenantId, UUID userId, String empNum, String first, String last, UUID managerId) throws Exception {
        UUID empId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, manager_id, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, empNum);
            ps.setString(5, first);
            ps.setString(6, last);
            ps.setString(7, first + " " + last);
            ps.setString(8, "2026-01-01");
            ps.setObject(9, managerId);
            ps.executeUpdate();
        }
        return empId;
    }

    private UUID seedLegacyDepartment(UUID tenantId, String name, String code) throws Exception {
        UUID deptId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_departments (id, tenant_id, name, code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, deptId);
            ps.setObject(2, tenantId);
            ps.setString(3, name);
            ps.setString(4, code);
            ps.executeUpdate();
        }
        return deptId;
    }

    private UUID seedLegacyPosition(UUID tenantId, String title, String code) throws Exception {
        UUID posId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, posId);
            ps.setObject(2, tenantId);
            ps.setString(3, title);
            ps.setString(4, code);
            ps.executeUpdate();
        }
        return posId;
    }

    // ==================== BACKFILL INVOCATION ====================

    private void invokeBackfill(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement("SELECT hr_backfill_tenant(?)")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); }
        } catch (SQLException e) {
            if (isUndefinedFunction(e)) return;
            throw e;
        }
    }

    private boolean isUndefinedFunction(SQLException e) {
        return "42883".equals(e.getSQLState()) ||
               (e.getMessage() != null && e.getMessage().contains("function") && e.getMessage().contains("does not exist"));
    }

    // ==================== QUERY HELPERS ====================

    private String getMigrationState(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM hr_migration_tenant_state WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("state");
                return "LEGACY";
            }
        }
    }

    private int countEmployeesWithLegalEntity(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND legal_entity_id IS NOT NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int countOrgUnits(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_org_units WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int countPositionVersionForLegacyPositions(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_position_versions WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int countAssignmentsWithReportsTo(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employee_assignments WHERE tenant_id = ? AND reports_to_assignment_id IS NOT NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int countOpenReviewItems(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_migration_review_items WHERE tenant_id = ? AND resolution_state = 'OPEN'")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            if (isUndefinedTable(e)) return 0;
            throw e;
        }
    }

    private boolean isUndefinedTable(SQLException e) {
        return "42P01".equals(e.getSQLState()) ||
               (e.getMessage() != null && e.getMessage().contains("does not exist"));
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }
}
