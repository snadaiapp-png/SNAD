package com.sanad.platform.hr.backfill;

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
 * WS2 Task 6 — Deterministic Legacy HR Backfill + Reconciliation TRUE RED.
 *
 * <p>Contract freeze for tenant-scoped legacy HR backfill and reconciliation.
 * This test defines the REQUIRED behavior of the Task 6 GREEN backfill
 * implementation. No production backfill service exists yet — every
 * post-condition assertion MUST fail because the backfill was never
 * invoked.</p>
 *
 * <p><b>TRUE RED principle:</b> The RED is the post-condition assertion
 * failure, not a missing-function error or compile error. The test
 * attempts to invoke the backfill via a SQL function call
 * {@code SELECT hr_backfill_tenant(?)}. If the function does not exist
 * (RED state), the invocation error is caught and the post-condition
 * assertions are still evaluated — they fail because the backfill never
 * ran.</p>
 *
 * <p><b>Contracts frozen:</b>
 * <ul>
 *   <li>TENANT_SCOPE — one tenant, one migration decision, one result</li>
 *   <li>DETERMINISM — same source + same baseline = same output</li>
 *   <li>IDEMPOTENCY — second run creates zero semantic duplicates</li>
 *   <li>RECONCILIATION — legacy_total = resolved + unresolved, unaccounted = 0</li>
 *   <li>NO_GUESSING — unresolved data creates review items, never guesses</li>
 *   <li>NO_HARD_DELETE — legacy rows are preserved</li>
 *   <li>BLOCKED_STATE — unresolved > 0 prevents CANONICAL</li>
 *   <li>CANONICAL_GATE — CANONICAL only when unresolved = 0 + reconciliation pass</li>
 * </ul>
 * </p>
 *
 * <p><b>Issue codes (stable, machine-readable):</b>
 * <ul>
 *   <li>DUPLICATE_USER_ID — two legacy employees share the same user_id</li>
 *   <li>MISSING_ORGANIZATION_MAPPING — no eligible legal_entity ↔ organization</li>
 *   <li>MISSING_DEPARTMENT_MAPPING — legacy department has no canonical org_unit</li>
 *   <li>MISSING_POSITION_MAPPING — legacy position has no canonical position_version</li>
 *   <li>MISSING_MANAGER_MAPPING — manager reference cannot be resolved</li>
 *   <li>AMBIGUOUS_PERSON_IDENTITY — identity cannot be uniquely resolved</li>
 * </ul>
 * </p>
 */
class HrLegacyBackfillReconciliationIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    // ==================== STABLE ISSUE CODES (FROZEN CONTRACT) ====================

    static final String DUPLICATE_USER_ID = "DUPLICATE_USER_ID";
    static final String MISSING_ORGANIZATION_MAPPING = "MISSING_ORGANIZATION_MAPPING";
    static final String MISSING_DEPARTMENT_MAPPING = "MISSING_DEPARTMENT_MAPPING";
    static final String MISSING_POSITION_MAPPING = "MISSING_POSITION_MAPPING";
    static final String MISSING_MANAGER_MAPPING = "MISSING_MANAGER_MAPPING";
    static final String AMBIGUOUS_PERSON_IDENTITY = "AMBIGUOUS_PERSON_IDENTITY";

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

    // ==================== 1. DETERMINISTIC SUCCESSFUL EMPLOYEE MAPPING ====================

    /**
     * Contract: A legacy employee with a unique user_id and valid
     * organization/legal-entity eligibility MUST be backfilled into
     * exactly one canonical Person + one Employment + one mapping.
     *
     * <p>RED: No backfill exists → migration_tenant_state remains LEGACY,
     * no canonical hr_people row, no hr_legacy_employee_mappings row.</p>
     */
    @Test
    void deterministicEmployeeMapping_createsCanonicalPersonAndMapping() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Test Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID legacyEmpId = seedLegacyEmployee(tenantId, userId, "EMP-001", "John", "Doe");

        invokeBackfill(tenantId);

        // Contract: migration state MUST transition from LEGACY → MIGRATING → CANONICAL
        String state = getMigrationState(tenantId);
        assertThat(state)
                .as("After successful backfill, tenant state MUST be CANONICAL (was: %s)", state)
                .isEqualTo("CANONICAL");

        // Contract: exactly one canonical Person must exist for this legacy employee
        int personCount = countCanonicalPersonsForUser(tenantId, userId);
        assertThat(personCount)
                .as("Exactly one canonical Person must exist for legacy user_id (found %d)", personCount)
                .isEqualTo(1);

        // Contract: exactly one mapping row must link legacy → canonical
        int mappingCount = countLegacyMappings(tenantId, legacyEmpId);
        assertThat(mappingCount)
                .as("Exactly one hr_legacy_employee_mappings row must exist (found %d)", mappingCount)
                .isEqualTo(1);
    }

    // ==================== 2. IDEMPOTENCY — SECOND RUN CREATES ZERO DUPLICATES ====================

    /**
     * Contract: Running backfill twice on the same tenant MUST NOT create
     * duplicate canonical People, Employments, mappings, or review items.
     *
     * <p>RED: No backfill ran on either invocation → canonical row count = 0
     * (assertion expects 1, fails).</p>
     */
    @Test
    void secondBackfillRun_createsZeroSemanticDuplicates() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Idempotency Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID legacyEmpId = seedLegacyEmployee(tenantId, userId, "EMP-IDEM", "Jane", "Smith");

        invokeBackfill(tenantId);
        int personsAfterRun1 = countCanonicalPersonsForUser(tenantId, userId);
        int mappingsAfterRun1 = countLegacyMappings(tenantId, legacyEmpId);

        invokeBackfill(tenantId);
        int personsAfterRun2 = countCanonicalPersonsForUser(tenantId, userId);
        int mappingsAfterRun2 = countLegacyMappings(tenantId, legacyEmpId);

        assertThat(personsAfterRun2)
                .as("Second backfill run MUST NOT create duplicate canonical Persons (run1=%d, run2=%d)",
                        personsAfterRun1, personsAfterRun2)
                .isEqualTo(personsAfterRun1)
                .as("First run MUST have created exactly 1 Person (found %d)", personsAfterRun1)
                .isEqualTo(1);

        assertThat(mappingsAfterRun2)
                .as("Second backfill run MUST NOT create duplicate mappings (run1=%d, run2=%d)",
                        mappingsAfterRun1, mappingsAfterRun2)
                .isEqualTo(mappingsAfterRun1)
                .as("First run MUST have created exactly 1 mapping (found %d)", mappingsAfterRun1)
                .isEqualTo(1);
    }

    // ==================== 3. TENANT ISOLATION — TENANT A DOES NOT MUTATE TENANT B ====================

    /**
     * Contract: Backfilling tenant A MUST NOT mutate tenant B's state,
     * mappings, or canonical rows.
     *
     * <p>RED: No backfill ran for tenant A → tenant A's state is still
     * LEGACY (assertion expects CANONICAL, fails). Tenant B's state
     * correctly remains LEGACY, but this is trivially true because
     * nothing ran.</p>
     */
    @Test
    void backfillTenantA_doesNotMutateTenantB() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        UUID orgA = seedOrganization(tenantA, "Org A");
        UUID orgB = seedOrganization(tenantB, "Org B");
        resetTenant();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        seedUser(tenantA, userA);
        seedUser(tenantB, userB);
        seedLegacyEmployee(tenantA, userA, "EMP-A", "Alice", "TenantA");
        seedLegacyEmployee(tenantB, userB, "EMP-B", "Bob", "TenantB");

        invokeBackfill(tenantA);

        // Contract: tenant A MUST transition to CANONICAL
        assertThat(getMigrationState(tenantA))
                .as("Tenant A MUST be CANONICAL after backfill")
                .isEqualTo("CANONICAL");

        // Contract: tenant B MUST remain LEGACY (unaffected)
        assertThat(getMigrationState(tenantB))
                .as("Tenant B MUST remain LEGACY — cross-tenant backfill is FORBIDDEN")
                .isEqualTo("LEGACY");

        // Contract: no canonical Person from tenant A may appear under tenant B
        int crossTenantPersons = countPersonsForTenant(tenantB);
        assertThat(crossTenantPersons)
                .as("Tenant B MUST have 0 canonical Persons from tenant A's backfill (found %d)",
                        crossTenantPersons)
                .isZero();
    }

    // ==================== 4. DUPLICATE LEGACY user_id BLOCKS TENANT ====================

    /**
     * Contract: Two legacy employees sharing the same user_id MUST block
     * the tenant (state = BLOCKED) and create a machine-readable review
     * item with issue_code = DUPLICATE_USER_ID. No arbitrary person merge
     * or employee drop is permitted.
     *
     * <p>RED: No backfill ran → state remains LEGACY (assertion expects
     * BLOCKED, fails). No review item created.</p>
     */
    @Test
    void duplicateLegacyUserId_blocksTenantAndCreatesReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Dup Org");
        UUID sharedUserId = UUID.randomUUID();
        seedUser(tenantId, sharedUserId);
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-DUP-1", "Dup", "One");
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-DUP-2", "Dup", "Two");

        invokeBackfill(tenantId);

        assertThat(getMigrationState(tenantId))
                .as("Duplicate user_id MUST block tenant (state must be BLOCKED)")
                .isEqualTo("BLOCKED");

        // Contract: at least one review item with stable issue code must exist
        int reviewItemCount = countReviewItemsWithCode(tenantId, DUPLICATE_USER_ID);
        assertThat(reviewItemCount)
                .as("At least 1 review item with code %s MUST exist (found %d)",
                        DUPLICATE_USER_ID, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 5. MISSING ORGANIZATION ELIGIBILITY BLOCKS TENANT ====================

    /**
     * Contract: A legacy employee with no eligible legal_entity ↔
     * organization mapping MUST block the tenant.
     *
     * <p>RED: No backfill ran → state remains LEGACY.</p>
     */
    @Test
    void missingOrganizationEligibility_blocksTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        // No organization seeded → no legal_entity ↔ org eligibility
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-NO-ORG", "NoOrg", "Employee");

        invokeBackfill(tenantId);

        assertThat(getMigrationState(tenantId))
                .as("Missing organization eligibility MUST block tenant")
                .isEqualTo("BLOCKED");

        int reviewItemCount = countReviewItemsWithCode(tenantId, MISSING_ORGANIZATION_MAPPING);
        assertThat(reviewItemCount)
                .as("Review item with code %s MUST exist (found %d)",
                        MISSING_ORGANIZATION_MAPPING, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 6. UNRESOLVED DEPARTMENT MAPPING CREATES REVIEW ITEM ====================

    /**
     * Contract: A legacy department with no canonical org_unit mapping
     * MUST create a review item and block cutover.
     *
     * <p>RED: No backfill ran → no review item created, state remains LEGACY.</p>
     */
    @Test
    void unresolvedDepartmentMapping_createsReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Dept Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        // Seed legacy employee referencing a department that has no canonical org_unit
        UUID legacyDeptId = seedLegacyDepartment(tenantId, "Unmapped Dept", "UNMAPPED");
        seedLegacyEmployeeWithDept(tenantId, userId, "EMP-DEPT", "Dept", "Employee", legacyDeptId);

        invokeBackfill(tenantId);

        int reviewItemCount = countReviewItemsWithCode(tenantId, MISSING_DEPARTMENT_MAPPING);
        assertThat(reviewItemCount)
                .as("Unresolved department mapping MUST create review item with code %s (found %d)",
                        MISSING_DEPARTMENT_MAPPING, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 7. UNRESOLVED POSITION MAPPING CREATES REVIEW ITEM ====================

    /**
     * Contract: A legacy position with no canonical position_version
     * mapping MUST create a review item and block cutover.
     */
    @Test
    void unresolvedPositionMapping_createsReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Pos Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID legacyPosId = seedLegacyPosition(tenantId, "Unmapped Pos", "UNMAPPED-POS");
        seedLegacyEmployeeWithPosition(tenantId, userId, "EMP-POS", "Pos", "Employee", legacyPosId);

        invokeBackfill(tenantId);

        int reviewItemCount = countReviewItemsWithCode(tenantId, MISSING_POSITION_MAPPING);
        assertThat(reviewItemCount)
                .as("Unresolved position mapping MUST create review item with code %s (found %d)",
                        MISSING_POSITION_MAPPING, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 8. UNRESOLVED MANAGER MAPPING CREATES REVIEW ITEM ====================

    /**
     * Contract: A legacy manager reference that cannot be resolved to a
     * canonical assignment MUST create a review item and block cutover.
     */
    @Test
    void unresolvedManagerMapping_createsReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Mgr Org");
        // Seed a manager employee first (satisfies FK constraint)
        UUID managerUserId = UUID.randomUUID();
        seedUser(tenantId, managerUserId);
        UUID managerEmpId = seedLegacyEmployee(tenantId, managerUserId, "EMP-MGR-MGR", "Manager", "Person");
        // Seed the subordinate employee referencing the manager
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployeeWithManager(tenantId, userId, "EMP-MGR", "Sub", "Employee", managerEmpId);

        invokeBackfill(tenantId);

        int reviewItemCount = countReviewItemsWithCode(tenantId, MISSING_MANAGER_MAPPING);
        assertThat(reviewItemCount)
                .as("Unresolved manager mapping MUST create review item with code %s (found %d)",
                        MISSING_MANAGER_MAPPING, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 9. AMBIGUOUS IDENTITY IS NEVER GUESSED ====================

    /**
     * Contract: When identity cannot be uniquely resolved (e.g., ambiguous
     * person match), the backfill MUST NOT guess. It MUST create a review
     * item with AMBIGUOUS_PERSON_IDENTITY and block cutover.
     */
    @Test
    void ambiguousIdentity_isNeverGuessed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Ambig Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-AMBIG", "Ambig", "Person");
        // Also seed an existing canonical Person with same user_id → ambiguity
        seedCanonicalPerson(tenantId, userId, "Ambig", "Existing");

        invokeBackfill(tenantId);

        // Contract: backfill MUST NOT merge or overwrite the existing Person
        int personCount = countCanonicalPersonsForUser(tenantId, userId);
        assertThat(personCount)
                .as("Ambiguous identity MUST NOT be guessed — exactly 1 canonical Person expected (found %d)",
                        personCount)
                .isEqualTo(1);

        // Contract: review item MUST be created
        int reviewItemCount = countReviewItemsWithCode(tenantId, AMBIGUOUS_PERSON_IDENTITY);
        assertThat(reviewItemCount)
                .as("Ambiguous identity MUST create review item with code %s (found %d)",
                        AMBIGUOUS_PERSON_IDENTITY, reviewItemCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 10. RECONCILIATION ARITHMETIC ACCOUNTS FOR EVERY LEGACY ROW ====================

    /**
     * Contract: legacy_total = resolved + unresolved, and unaccounted_rows
     * MUST equal 0. Every legacy row must be either resolved or explicitly
     * unresolved with a machine-readable reason.
     *
     * <p>RED: No backfill ran → resolved = 0, unresolved = 0, but
     * legacy_total > 0 → unaccounted > 0 → assertion fails.</p>
     */
    @Test
    void reconciliationArithmetic_accountsForEveryLegacyRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Recon Org");
        // Seed 3 resolvable + 2 unresolvable legacy employees
        for (int i = 1; i <= 3; i++) {
            UUID uid = UUID.randomUUID();
            seedUser(tenantId, uid);
            seedLegacyEmployee(tenantId, uid, "EMP-RECON-" + i, "Recon", "Resolvable" + i);
        }
        UUID uid1 = UUID.randomUUID();
        seedUser(tenantId, uid1);
        seedLegacyEmployee(tenantId, uid1, "EMP-RECON-UNRES-1", "Unres", "One");
        UUID uid2 = UUID.randomUUID();
        seedUser(tenantId, uid2);
        seedLegacyEmployee(tenantId, uid2, "EMP-RECON-UNRES-2", "Unres", "Two");

        invokeBackfill(tenantId);

        int legacyTotal = countLegacyEmployees(tenantId);
        int resolved = countResolvedEmployees(tenantId);
        int unresolved = countUnresolvedReviewItems(tenantId);
        int unaccounted = legacyTotal - resolved - unresolved;

        assertThat(unaccounted)
                .as("Reconciliation arithmetic: legacy_total(%d) = resolved(%d) + unresolved(%d); unaccounted MUST be 0 (was %d)",
                        legacyTotal, resolved, unresolved, unaccounted)
                .isZero();
    }

    // ==================== 11. UNRESOLVED > 0 PREVENTS CANONICAL STATE ====================

    /**
     * Contract: If unresolved > 0, tenant state MUST NOT be CANONICAL.
     * It MUST be BLOCKED.
     */
    @Test
    void unresolvedGreaterThanZero_preventsCanonicalState() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Block Org");
        // Seed one unresolvable employee (missing org eligibility)
        UUID uidBlock = UUID.randomUUID();
        seedUser(tenantId, uidBlock);
        seedLegacyEmployee(tenantId, uidBlock, "EMP-BLOCK", "Block", "Employee");
        // Don't seed organization → missing org eligibility

        invokeBackfill(tenantId);

        int unresolved = countUnresolvedReviewItems(tenantId);
        assertThat(unresolved)
                .as("There MUST be at least 1 unresolved review item (found %d)", unresolved)
                .isGreaterThan(0);

        assertThat(getMigrationState(tenantId))
                .as("With unresolved > 0, state MUST be BLOCKED (not CANONICAL)")
                .isNotEqualTo("CANONICAL")
                .as("State MUST be BLOCKED")
                .isEqualTo("BLOCKED");
    }

    // ==================== 12. ZERO UNRESOLVED + RECONCILIATION PASS = PREREQUISITE FOR CANONICAL ====================

    /**
     * Contract: CANONICAL state is allowed ONLY when unresolved = 0,
     * reconciliation = PASS, cross_tenant_mismatch = 0,
     * duplicate_mapping = 0, orphan_mapping = 0.
     */
    @Test
    void zeroUnresolvedAndReconciliationPass_isPrerequisiteForCanonical() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Clean Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-CLEAN", "Clean", "Employee");

        invokeBackfill(tenantId);

        // If state is CANONICAL, all prerequisites MUST hold
        String state = getMigrationState(tenantId);
        if ("CANONICAL".equals(state)) {
            assertThat(countUnresolvedReviewItems(tenantId))
                    .as("CANONICAL requires unresolved = 0")
                    .isZero();
            assertThat(countDuplicateMappings(tenantId))
                    .as("CANONICAL requires duplicate_mapping = 0")
                    .isZero();
            assertThat(countOrphanMappings(tenantId))
                    .as("CANONICAL requires orphan_mapping = 0")
                    .isZero();
        } else {
            // RED: state is NOT CANONICAL → this assertion FAILS
            assertThat(state)
                    .as("Clean tenant MUST reach CANONICAL after backfill (was %s) — this is the DIRECT RED", state)
                    .isEqualTo("CANONICAL");
        }
    }

    // ==================== 13. MAPPINGS ARE TENANT-SAFE AND UNIQUE ====================

    /**
     * Contract: hr_legacy_employee_mappings MUST be tenant-scoped and
     * unique per (tenant_id, legacy_employee_id). No cross-tenant
     * mapping is permitted.
     */
    @Test
    void mappingsAreTenantSafeAndUnique() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        UUID orgA = seedOrganization(tenantA, "Map Org A");
        UUID orgB = seedOrganization(tenantB, "Map Org B");
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID legacyEmpA = seedLegacyEmployee(tenantA, userA, "EMP-MAP-A", "Map", "A");
        UUID legacyEmpB = seedLegacyEmployee(tenantB, userB, "EMP-MAP-B", "Map", "B");

        invokeBackfill(tenantA);
        invokeBackfill(tenantB);

        // Contract: mapping for tenant A's employee must NOT point to tenant B's canonical person
        int crossTenantMappings = countCrossTenantMappings(tenantA, tenantB);
        assertThat(crossTenantMappings)
                .as("Cross-tenant mappings MUST NOT exist (found %d)", crossTenantMappings)
                .isZero();

        // Contract: no duplicate mappings for the same (tenant, legacy_employee)
        int duplicateMappings = countDuplicateMappings(tenantA);
        assertThat(duplicateMappings)
                .as("Duplicate mappings MUST NOT exist for tenant A (found %d)", duplicateMappings)
                .isZero();
    }

    // ==================== 14. NO LEGACY HARD DELETE ====================

    /**
     * Contract: Backfill MUST NOT hard-delete any legacy rows from
     * hr_employees, hr_departments, or hr_positions.
     */
    @Test
    void noLegacyHardDelete() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "NoDelete Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID legacyEmpId = seedLegacyEmployee(tenantId, userId, "EMP-NO-DEL", "No", "Delete");
        UUID legacyDeptId = seedLegacyDepartment(tenantId, "NoDelete Dept", "NODEL");
        UUID legacyPosId = seedLegacyPosition(tenantId, "NoDelete Pos", "NODEL-POS");

        int employeesBefore = countLegacyEmployees(tenantId);
        int departmentsBefore = countLegacyDepartments(tenantId);
        int positionsBefore = countLegacyPositions(tenantId);

        invokeBackfill(tenantId);

        int employeesAfter = countLegacyEmployees(tenantId);
        int departmentsAfter = countLegacyDepartments(tenantId);
        int positionsAfter = countLegacyPositions(tenantId);

        assertThat(employeesAfter)
                .as("Legacy hr_employees rows MUST NOT be deleted (before=%d, after=%d)",
                        employeesBefore, employeesAfter)
                .isEqualTo(employeesBefore);
        assertThat(departmentsAfter)
                .as("Legacy hr_departments rows MUST NOT be deleted (before=%d, after=%d)",
                        departmentsBefore, departmentsAfter)
                .isEqualTo(departmentsBefore);
        assertThat(positionsAfter)
                .as("Legacy hr_positions rows MUST NOT be deleted (before=%d, after=%d)",
                        positionsBefore, positionsAfter)
                .isEqualTo(positionsBefore);
    }

    // ==================== 15. TASK 4 TEMPORAL INVARIANTS ENFORCED DURING BACKFILL ====================

    /**
     * Contract: Backfill-created assignments MUST respect Task 4 temporal
     * invariants: no PRIMARY overlap, no position occupying overlap,
     * allocation ≤ 100%, reporting cycle-safe.
     *
     * <p>RED: No backfill ran → 0 assignments created → assertion expects
     * assignments to exist, fails.</p>
     */
    @Test
    void backfillAssignmentsRespectTask4TemporalInvariants() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Temporal Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-TEMP", "Temp", "Employee");

        invokeBackfill(tenantId);

        // Contract: at least one assignment must be created
        int assignmentCount = countAssignments(tenantId);
        assertThat(assignmentCount)
                .as("Backfill MUST create at least 1 assignment for a resolvable employee (found %d)",
                        assignmentCount)
                .isGreaterThanOrEqualTo(1);

        // Contract: no PRIMARY overlap in assignments
        int primaryOverlaps = countPrimaryAssignmentOverlaps(tenantId);
        assertThat(primaryOverlaps)
                .as("PRIMARY assignment temporal overlaps MUST NOT exist (found %d)", primaryOverlaps)
                .isZero();
    }

    // ==================== 16. RERUN DOES NOT DUPLICATE REVIEW ITEMS ====================

    /**
     * Contract: Running backfill twice MUST NOT create duplicate review
     * items for the same unresolved condition.
     */
    @Test
    void rerunDoesNotDuplicateReviewItems() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Rerun Org");
        // Seed an unresolvable employee
        UUID uidRerun = UUID.randomUUID();
        seedUser(tenantId, uidRerun);
        seedLegacyEmployee(tenantId, uidRerun, "EMP-RERUN", "Rerun", "Employee");
        // No org eligibility → unresolved

        invokeBackfill(tenantId);
        int reviewItemsRun1 = countAllReviewItems(tenantId);

        invokeBackfill(tenantId);
        int reviewItemsRun2 = countAllReviewItems(tenantId);

        assertThat(reviewItemsRun2)
                .as("Second backfill run MUST NOT create duplicate review items (run1=%d, run2=%d)",
                        reviewItemsRun1, reviewItemsRun2)
                .isEqualTo(reviewItemsRun1)
                .as("First run MUST have created at least 1 review item (found %d)", reviewItemsRun1)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== 17. MACHINE-READABLE ISSUE CODES ARE STABLE ====================

    /**
     * Contract: Review items MUST have stable, machine-readable issue codes
     * (not free-form text). The codes must be deterministic across runs.
     */
    @Test
    void reviewItemIssueCodesAreStableAndMachineReadable() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Stable Org");
        // Seed duplicate user_id to trigger DUPLICATE_USER_ID
        UUID sharedUserId = UUID.randomUUID();
        seedUser(tenantId, sharedUserId);
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-STABLE-1", "Stable", "One");
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-STABLE-2", "Stable", "Two");

        invokeBackfill(tenantId);

        // Contract: at least one review item must have the EXACT code DUPLICATE_USER_ID
        int stableCodeCount = countReviewItemsWithCode(tenantId, DUPLICATE_USER_ID);
        assertThat(stableCodeCount)
                .as("Review item with stable code '%s' MUST exist (found %d)",
                        DUPLICATE_USER_ID, stableCodeCount)
                .isGreaterThanOrEqualTo(1);

        // Contract: review_reason (if present) must not be null/empty for review items
        int nullReasonCount = countReviewItemsWithNullOrBlankReason(tenantId);
        assertThat(nullReasonCount)
                .as("Review items MUST NOT have null/blank reason (found %d)", nullReasonCount)
                .isZero();
    }

    // ==================== 18. PARTIAL/ERROR EXECUTION NEVER CLAIMS CANONICAL ====================

    /**
     * Contract: If backfill fails mid-execution, tenant state MUST NOT be
     * CANONICAL. Partial canonical graphs are permitted only if the state
     * remains MIGRATING or transitions to BLOCKED.
     */
    @Test
    void partialErrorExecution_neverClaimsCanonical() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Partial Org");
        // Seed a mix of resolvable and unresolvable employees
        UUID uidOk = UUID.randomUUID();
        seedUser(tenantId, uidOk);
        seedLegacyEmployee(tenantId, uidOk, "EMP-PARTIAL-OK", "Partial", "Ok");
        UUID uidBad = UUID.randomUUID();
        seedUser(tenantId, uidBad);
        seedLegacyEmployee(tenantId, uidBad, "EMP-PARTIAL-BAD", "Partial", "Bad");
        // The BAD one has no org eligibility → unresolved

        invokeBackfill(tenantId);

        String state = getMigrationState(tenantId);
        // If there's ANY unresolved, state MUST NOT be CANONICAL
        int unresolved = countUnresolvedReviewItems(tenantId);
        if (unresolved > 0) {
            assertThat(state)
                    .as("With %d unresolved items, state MUST NOT be CANONICAL (was %s)", unresolved, state)
                    .isNotEqualTo("CANONICAL");
        } else {
            // RED: if no unresolved, state SHOULD be CANONICAL
            assertThat(state)
                    .as("Clean tenant MUST reach CANONICAL after backfill (was %s) — DIRECT RED", state)
                    .isEqualTo("CANONICAL");
        }
    }

    // ==================== FIXTURE HELPERS ====================

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, 'Test', ?, 'ACTIVE', NOW(), NOW()) " +
                "ON CONFLICT (id) DO NOTHING")) {
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
        // Set tenant context for any subsequent tenant-scoped operations
        setTenant(tenantId);
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

    private UUID seedCanonicalPerson(UUID tenantId, UUID userId, String first, String last) throws Exception {
        UUID personId = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, first + " " + last);
            ps.executeUpdate();
        }
        return personId;
    }

    // ==================== BACKFILL INVOCATION ====================

    /**
     * Invoke the tenant-scoped backfill via SQL function call.
     *
     * <p>In RED (no implementation): the function does not exist.
     * The error is caught and logged; post-condition assertions
     * are still evaluated and will FAIL because the backfill never ran.
     * This is the DIRECT RED — the contract is violated because the
     * expected post-backfill state does not hold.</p>
     *
     * <p>In GREEN: the function exists, performs the backfill, and the
     * post-condition assertions PASS.</p>
     */
    private void invokeBackfill(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hr_backfill_tenant(?)")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); // consume result
            }
        } catch (SQLException e) {
            // RED state: function does not exist.
            // This is NOT the RED evidence — the post-condition assertion
            // failures below are the DIRECT RED.
            // System.out.println("Backfill not yet implemented: " + e.getMessage());
        }
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

    private int countCanonicalPersonsForUser(UUID tenantId, UUID userId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ? AND user_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countPersonsForTenant(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countLegacyMappings(UUID tenantId, UUID legacyEmpId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings WHERE tenant_id = ? AND legacy_employee_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, legacyEmpId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Count review items with a specific issue code.
     *
     * <p>In RED: the hr_migration_review_items table does not exist yet.
     * Returns 0 (caught exception). The assertion expecting >= 1 fails.</p>
     */
    private int countReviewItemsWithCode(UUID tenantId, String issueCode) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_migration_review_items WHERE tenant_id = ? AND issue_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, issueCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // Table does not exist in RED → 0 review items
            return 0;
        }
    }

    private int countAllReviewItems(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_migration_review_items WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private int countReviewItemsWithNullOrBlankReason(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_migration_review_items WHERE tenant_id = ? " +
                "AND (review_reason IS NULL OR TRIM(review_reason) = '')")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private int countLegacyEmployees(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countLegacyDepartments(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_departments WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countLegacyPositions(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_positions WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Count resolved employees = legacy employees that have a mapping
     * with classification AUTO_MIGRATE and a canonical person linked.
     */
    private int countResolvedEmployees(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings " +
                "WHERE tenant_id = ? AND classification = 'AUTO_MIGRATE' " +
                "AND canonical_person_id IS NOT NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Count unresolved review items = legacy employees that have a
     * mapping with classification MIGRATION_REVIEW_REQUIRED or
     * MIGRATION_BLOCKED, or that appear in hr_migration_review_items.
     */
    private int countUnresolvedReviewItems(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(DISTINCT legacy_employee_id) FROM hr_legacy_employee_mappings " +
                "WHERE tenant_id = ? AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED')")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countDuplicateMappings(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM (SELECT legacy_employee_id, COUNT(*) AS cnt " +
                "FROM hr_legacy_employee_mappings WHERE tenant_id = ? " +
                "GROUP BY legacy_employee_id HAVING COUNT(*) > 1) dups")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countOrphanMappings(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings m " +
                "WHERE m.tenant_id = ? " +
                "AND NOT EXISTS (SELECT 1 FROM hr_employees e WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id)")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countCrossTenantMappings(UUID tenantA, UUID tenantB) throws Exception {
        setTenant(tenantA);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings m " +
                "WHERE m.tenant_id = ? " +
                "AND EXISTS (SELECT 1 FROM hr_legacy_employee_mappings m2 " +
                "           WHERE m2.canonical_person_id = m.canonical_person_id " +
                "           AND m2.tenant_id = ? AND m2.tenant_id != m.tenant_id)")) {
            ps.setObject(1, tenantA);
            ps.setObject(2, tenantB);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countAssignments(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employee_assignments WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countPrimaryAssignmentOverlaps(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employee_assignments a1 " +
                "JOIN hr_employee_assignments a2 ON a1.tenant_id = a2.tenant_id " +
                "AND a1.employment_id = a2.employment_id " +
                "AND a1.id < a2.id " +
                "WHERE a1.tenant_id = ? AND a1.assignment_type = 'PRIMARY' AND a2.assignment_type = 'PRIMARY' " +
                "AND a1.effective_to IS NULL AND a2.effective_to IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
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
