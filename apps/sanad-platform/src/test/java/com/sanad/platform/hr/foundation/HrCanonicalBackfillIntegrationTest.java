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
 * WS2 Task 6 — Canonical HR Backfill + Reconciliation TRUE RED Contract.
 *
 * <p>Authoritative plan alignment:
 * <ul>
 *   <li>PRECHECK — detect ambiguity before mutation</li>
 *   <li>BACKFILL — deterministic canonical graph creation</li>
 *   <li>RECONCILE — verify source vs canonical arithmetic</li>
 * </ul>
 *
 * <p>Test classification:
 * <ul>
 *   <li>EXPECTED_DIRECT_RED — tests that assert a post-backfill
 *       postcondition that CANNOT hold in RED (no implementation).
 *       These tests MUST fail with a contract assertion failure.</li>
 *   <li>EXPECTED_CONTROL_PASS — tests that assert safety invariants
 *       that are trivially true even in RED (e.g., legacy rows are
 *       not deleted when no backfill ran). These MAY pass in RED.</li>
 * </ul>
 *
 * <p>False-green prevention:
 * <ul>
 *   <li>invokeBackfill only swallows SQLSTATE 42883 (undefined_function).
 *       All other SQLExceptions are rethrown.</li>
 *   <li>Review-item helpers distinguish "table not present" (RED bootstrap)
 *       from real SQL errors. Any error other than undefined_table is
 *       rethrown.</li>
 *   <li>Tests that assert "zero cross-tenant" or "zero duplicates" first
 *       assert a POSITIVE CONTROL (at least 1 mapping was created) before
 *       the zero-assertion.</li>
 *   <li>Tests that assert "no hard delete" first prove backfill executed
 *       (canonical Person created, mapping created) before asserting
 *       legacy row preservation.</li>
 * </ul>
 *
 * <p>Three authoritative tenant fixtures:
 * <ul>
 *   <li>TENANT A — AUTO_READY: 1 legal entity, 1 eligible org, 1 employee
 *       with unique user_id. Backfill MUST resolve and create canonical graph.</li>
 *   <li>TENANT B — MIGRATION_REVIEW_REQUIRED: 2 eligible orgs (ambiguous).
 *       Backfill MUST create review items and NOT guess.</li>
 *   <li>TENANT C — MIGRATION_BLOCKED: 0 legal entities. Backfill MUST block.</li>
 * </ul>
 * </p>
 */
class HrCanonicalBackfillIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    // ==================== STABLE ISSUE CODES (FROZEN) ====================

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

    // ==================== TENANT A: AUTO_READY CONTRACT ====================

    /**
     * DIRECT_RED: Tenant A with a clean, resolvable legacy employee MUST
     * produce exactly one canonical Person, one Employment, one mapping,
     * and one PRIMARY Assignment. In RED, none exist.
     */
    @Test
    void tenantA_autoReady_createsCanonicalGraph() throws Exception {
        UUID tenantId = seedTenantA();

        invokeBackfill(tenantId);

        // Contract: state MUST be CANONICAL
        assertThat(getMigrationState(tenantId))
                .as("Tenant A (AUTO_READY) MUST reach CANONICAL after backfill — DIRECT RED")
                .isEqualTo("CANONICAL");

        // Contract: exactly 1 canonical Person
        int personCount = countPersonsForTenant(tenantId);
        assertThat(personCount)
                .as("Tenant A MUST have exactly 1 canonical Person (found %d) — DIRECT RED", personCount)
                .isEqualTo(1);

        // Contract: exactly 1 legacy mapping with AUTO_MIGRATE
        int mappingCount = countAutoMigrateMappings(tenantId);
        assertThat(mappingCount)
                .as("Tenant A MUST have exactly 1 AUTO_MIGRATE mapping (found %d) — DIRECT RED", mappingCount)
                .isEqualTo(1);

        // Contract: at least 1 PRIMARY assignment
        int assignmentCount = countAssignments(tenantId);
        assertThat(assignmentCount)
                .as("Tenant A MUST have at least 1 assignment (found %d) — DIRECT RED", assignmentCount)
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * DIRECT_RED: Second backfill run on Tenant A MUST NOT create
     * duplicate Persons, mappings, or assignments.
     */
    @Test
    void tenantA_secondRunIsIdempotent() throws Exception {
        UUID tenantId = seedTenantA();

        invokeBackfill(tenantId);
        int personsAfterRun1 = countPersonsForTenant(tenantId);
        int mappingsAfterRun1 = countAutoMigrateMappings(tenantId);
        int assignmentsAfterRun1 = countAssignments(tenantId);

        invokeBackfill(tenantId);
        int personsAfterRun2 = countPersonsForTenant(tenantId);
        int mappingsAfterRun2 = countAutoMigrateMappings(tenantId);
        int assignmentsAfterRun2 = countAssignments(tenantId);

        // POSITIVE CONTROL: first run must have created at least 1
        assertThat(personsAfterRun1)
                .as("First run MUST have created at least 1 Person (found %d) — DIRECT RED", personsAfterRun1)
                .isGreaterThanOrEqualTo(1);

        // IDEMPOTENCY: second run must not duplicate
        assertThat(personsAfterRun2)
                .as("Second run MUST NOT create duplicate Persons (run1=%d, run2=%d)", personsAfterRun1, personsAfterRun2)
                .isEqualTo(personsAfterRun1);
        assertThat(mappingsAfterRun2)
                .as("Second run MUST NOT create duplicate mappings (run1=%d, run2=%d)", mappingsAfterRun1, mappingsAfterRun2)
                .isEqualTo(mappingsAfterRun1);
        assertThat(assignmentsAfterRun2)
                .as("Second run MUST NOT create duplicate assignments (run1=%d, run2=%d)", assignmentsAfterRun1, assignmentsAfterRun2)
                .isEqualTo(assignmentsAfterRun1);
    }

    // ==================== TENANT B: MIGRATION_REVIEW_REQUIRED CONTRACT ====================

    /**
     * DIRECT_RED: Tenant B with ambiguous organization mapping (2 eligible orgs)
     * MUST create review items with MIGRATION_REVIEW_REQUIRED classification,
     * MUST NOT guess, and MUST NOT be CANONICAL.
     */
    @Test
    void tenantB_ambiguousOrg_createsReviewItemsAndBlocks() throws Exception {
        UUID tenantId = seedTenantB();

        invokeBackfill(tenantId);

        // Contract: state MUST NOT be CANONICAL
        assertThat(getMigrationState(tenantId))
                .as("Tenant B (ambiguous org) MUST NOT be CANONICAL — DIRECT RED")
                .isNotEqualTo("CANONICAL");

        // Contract: at least 1 review item with MISSING_ORGANIZATION_MAPPING
        int reviewCount = countReviewItemsWithCode(tenantId, MISSING_ORGANIZATION_MAPPING);
        assertThat(reviewCount)
                .as("Tenant B MUST have at least 1 review item with code %s (found %d) — DIRECT RED",
                        MISSING_ORGANIZATION_MAPPING, reviewCount)
                .isGreaterThanOrEqualTo(1);

        // Contract: NO guessing — no canonical Person should be created from ambiguous data
        int personCount = countPersonsForTenant(tenantId);
        assertThat(personCount)
                .as("Tenant B MUST NOT guess — 0 canonical Persons expected (found %d)", personCount)
                .isZero();
    }

    // ==================== TENANT C: MIGRATION_BLOCKED CONTRACT ====================

    /**
     * DIRECT_RED: Tenant C with 0 legal entities MUST be BLOCKED and
     * create review items with MISSING_ORGANIZATION_MAPPING.
     */
    @Test
    void tenantC_noLegalEntity_blocksAndCreatesReviewItems() throws Exception {
        UUID tenantId = seedTenantC();

        invokeBackfill(tenantId);

        assertThat(getMigrationState(tenantId))
                .as("Tenant C (no legal entity) MUST be BLOCKED — DIRECT RED")
                .isEqualTo("BLOCKED");

        int reviewCount = countReviewItemsWithCode(tenantId, MISSING_ORGANIZATION_MAPPING);
        assertThat(reviewCount)
                .as("Tenant C MUST have at least 1 review item with code %s (found %d) — DIRECT RED",
                        MISSING_ORGANIZATION_MAPPING, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== TENANT ISOLATION ====================

    /**
     * DIRECT_RED: Backfilling Tenant A MUST NOT mutate Tenant B.
     * Requires POSITIVE CONTROL: Tenant A MUST have mappings.
     */
    @Test
    void backfillTenantA_doesNotMutateTenantB() throws Exception {
        UUID tenantA = seedTenantA();
        UUID tenantB = seedTenantC(); // Use Tenant C fixture (no legal entity)

        invokeBackfill(tenantA);

        // POSITIVE CONTROL: Tenant A must have at least 1 mapping
        int mappingsA = countAllMappings(tenantA);
        assertThat(mappingsA)
                .as("POSITIVE CONTROL: Tenant A MUST have at least 1 mapping after backfill (found %d) — DIRECT RED", mappingsA)
                .isGreaterThanOrEqualTo(1);

        // NEGATIVE CONTROL: Tenant B must remain untouched
        assertThat(getMigrationState(tenantB))
                .as("Tenant B MUST remain LEGACY (unaffected by Tenant A backfill)")
                .isEqualTo("LEGACY");

        int personsB = countPersonsForTenant(tenantB);
        assertThat(personsB)
                .as("Tenant B MUST have 0 canonical Persons from Tenant A's backfill (found %d)", personsB)
                .isZero();
    }

    // ==================== DUPLICATE USER_ID ====================

    /**
     * DIRECT_RED: Two legacy employees sharing the same user_id MUST block
     * the tenant and create a review item with DUPLICATE_USER_ID.
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
                .as("Duplicate user_id MUST block tenant (expected BLOCKED) — DIRECT RED")
                .isEqualTo("BLOCKED");

        int reviewCount = countReviewItemsWithCode(tenantId, DUPLICATE_USER_ID);
        assertThat(reviewCount)
                .as("At least 1 review item with code %s MUST exist (found %d) — DIRECT RED",
                        DUPLICATE_USER_ID, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== MISSING DEPARTMENT MAPPING ====================

    /**
     * DIRECT_RED: Legacy department with no canonical org_unit mapping
     * MUST create a review item.
     */
    @Test
    void unresolvedDepartmentMapping_createsReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Dept Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID legacyDeptId = seedLegacyDepartment(tenantId, "Unmapped Dept", "UNMAPPED");
        seedLegacyEmployeeWithDept(tenantId, userId, "EMP-DEPT", "Dept", "Employee", legacyDeptId);

        invokeBackfill(tenantId);

        int reviewCount = countReviewItemsWithCode(tenantId, MISSING_DEPARTMENT_MAPPING);
        assertThat(reviewCount)
                .as("Unresolved department mapping MUST create review item with code %s (found %d) — DIRECT RED",
                        MISSING_DEPARTMENT_MAPPING, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== MISSING POSITION MAPPING ====================

    /**
     * DIRECT_RED: Legacy position with no canonical position_version
     * mapping MUST create a review item.
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

        int reviewCount = countReviewItemsWithCode(tenantId, MISSING_POSITION_MAPPING);
        assertThat(reviewCount)
                .as("Unresolved position mapping MUST create review item with code %s (found %d) — DIRECT RED",
                        MISSING_POSITION_MAPPING, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== MISSING MANAGER MAPPING ====================

    /**
     * DIRECT_RED: Manager reference that cannot be resolved to a canonical
     * assignment MUST create a review item.
     */
    @Test
    void unresolvedManagerMapping_createsReviewItem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Mgr Org");
        UUID managerUserId = UUID.randomUUID();
        seedUser(tenantId, managerUserId);
        UUID managerEmpId = seedLegacyEmployee(tenantId, managerUserId, "EMP-MGR-MGR", "Manager", "Person");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployeeWithManager(tenantId, userId, "EMP-MGR", "Sub", "Employee", managerEmpId);

        invokeBackfill(tenantId);

        int reviewCount = countReviewItemsWithCode(tenantId, MISSING_MANAGER_MAPPING);
        assertThat(reviewCount)
                .as("Unresolved manager mapping MUST create review item with code %s (found %d) — DIRECT RED",
                        MISSING_MANAGER_MAPPING, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== AMBIGUOUS IDENTITY — NEVER GUESS ====================

    /**
     * DIRECT_RED: When identity cannot be uniquely resolved, the backfill
     * MUST NOT guess. It MUST create a review item.
     */
    @Test
    void ambiguousIdentity_isNeverGuessed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Ambig Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-AMBIG", "Ambig", "Person");
        // Pre-existing canonical Person with same user_id → ambiguity
        seedCanonicalPerson(tenantId, userId, "Ambig", "Existing");

        invokeBackfill(tenantId);

        // Contract: backfill MUST NOT merge or overwrite the existing Person
        int personCount = countCanonicalPersonsForUser(tenantId, userId);
        assertThat(personCount)
                .as("Ambiguous identity MUST NOT be guessed — exactly 1 canonical Person expected (found %d)", personCount)
                .isEqualTo(1);

        int reviewCount = countReviewItemsWithCode(tenantId, AMBIGUOUS_PERSON_IDENTITY);
        assertThat(reviewCount)
                .as("Ambiguous identity MUST create review item with code %s (found %d) — DIRECT RED",
                        AMBIGUOUS_PERSON_IDENTITY, reviewCount)
                .isGreaterThanOrEqualTo(1);
    }

    // ==================== RECONCILIATION ARITHMETIC ====================

    /**
     * DIRECT_RED: legacy_total = resolved + unresolved, unaccounted = 0.
     * Uses actual fixture row count, not column count.
     */
    @Test
    void reconciliationArithmetic_accountsForEveryLegacyRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Recon Org");
        // Seed 3 resolvable employees
        for (int i = 1; i <= 3; i++) {
            UUID uid = UUID.randomUUID();
            seedUser(tenantId, uid);
            seedLegacyEmployee(tenantId, uid, "EMP-RECON-" + i, "Recon", "Resolvable" + i);
        }
        // Seed 2 unresolvable employees (duplicate user_id)
        UUID dupUserId = UUID.randomUUID();
        seedUser(tenantId, dupUserId);
        seedLegacyEmployee(tenantId, dupUserId, "EMP-RECON-UNRES-1", "Unres", "One");
        seedLegacyEmployee(tenantId, dupUserId, "EMP-RECON-UNRES-2", "Unres", "Two");

        invokeBackfill(tenantId);

        int legacyTotal = countLegacyEmployees(tenantId);
        int resolved = countResolvedMappings(tenantId);
        int unresolved = countUnresolvedMappings(tenantId);
        int unaccounted = legacyTotal - resolved - unresolved;

        // POSITIVE CONTROL: legacy_total must be > 0 (we seeded 5)
        assertThat(legacyTotal)
                .as("POSITIVE CONTROL: legacy_total must be 5 (found %d)", legacyTotal)
                .isEqualTo(5);

        assertThat(unaccounted)
                .as("Reconciliation: legacy_total(%d) = resolved(%d) + unresolved(%d); unaccounted MUST be 0 (was %d) — DIRECT RED",
                        legacyTotal, resolved, unresolved, unaccounted)
                .isZero();
    }

    // ==================== UNRESOLVED > 0 PREVENTS CANONICAL ====================

    /**
     * DIRECT_RED: If unresolved > 0, tenant state MUST be BLOCKED.
     */
    @Test
    void unresolvedGreaterThanZero_preventsCanonicalState() throws Exception {
        UUID tenantId = seedTenantC(); // 0 legal entities → unresolvable

        invokeBackfill(tenantId);

        int unresolved = countUnresolvedMappings(tenantId);
        assertThat(unresolved)
                .as("There MUST be at least 1 unresolved mapping (found %d) — DIRECT RED", unresolved)
                .isGreaterThan(0);

        assertThat(getMigrationState(tenantId))
                .as("With unresolved > 0, state MUST be BLOCKED (not CANONICAL) — DIRECT RED")
                .isEqualTo("BLOCKED");
    }

    // ==================== CANONICAL GATE ====================

    /**
     * DIRECT_RED: CANONICAL state is allowed ONLY when unresolved = 0,
     * duplicate_mapping = 0, orphan_mapping = 0.
     */
    @Test
    void canonicalStateRequiresZeroUnresolved() throws Exception {
        UUID tenantId = seedTenantA(); // Clean, resolvable

        invokeBackfill(tenantId);

        String state = getMigrationState(tenantId);
        // DIRECT RED: state must be CANONICAL
        assertThat(state)
                .as("Clean tenant A MUST reach CANONICAL after backfill (was %s) — DIRECT RED", state)
                .isEqualTo("CANONICAL");

        // If state IS CANONICAL (in GREEN), all gates must hold
        if ("CANONICAL".equals(state)) {
            assertThat(countUnresolvedMappings(tenantId))
                    .as("CANONICAL requires unresolved = 0").isZero();
            assertThat(countDuplicateMappings(tenantId))
                    .as("CANONICAL requires duplicate_mapping = 0").isZero();
            assertThat(countOrphanMappings(tenantId))
                    .as("CANONICAL requires orphan_mapping = 0").isZero();
        }
    }

    // ==================== MAPPING TENANT-SAFETY + UNIQUE ====================

    /**
     * DIRECT_RED: Mappings MUST be tenant-safe and unique.
     * Requires POSITIVE CONTROL: at least 1 mapping must exist per tenant first.
     */
    @Test
    void mappingsAreTenantSafeAndUnique() throws Exception {
        UUID tenantA = seedTenantA();
        UUID tenantB = seedTenantA(); // Second clean tenant

        invokeBackfill(tenantA);
        invokeBackfill(tenantB);

        // POSITIVE CONTROL: both tenants must have at least 1 mapping
        int mappingsA = countAllMappings(tenantA);
        int mappingsB = countAllMappings(tenantB);
        assertThat(mappingsA)
                .as("POSITIVE CONTROL: Tenant A MUST have at least 1 mapping (found %d) — DIRECT RED", mappingsA)
                .isGreaterThanOrEqualTo(1);
        assertThat(mappingsB)
                .as("POSITIVE CONTROL: Tenant B MUST have at least 1 mapping (found %d) — DIRECT RED", mappingsB)
                .isGreaterThanOrEqualTo(1);

        // SECURITY: no cross-tenant mappings
        int crossTenantMappings = countCrossTenantMappings(tenantA, tenantB);
        assertThat(crossTenantMappings)
                .as("Cross-tenant mappings MUST NOT exist (found %d)", crossTenantMappings)
                .isZero();

        // SECURITY: no duplicate mappings
        assertThat(countDuplicateMappings(tenantA))
                .as("Duplicate mappings MUST NOT exist for Tenant A").isZero();
        assertThat(countDuplicateMappings(tenantB))
                .as("Duplicate mappings MUST NOT exist for Tenant B").isZero();
    }

    // ==================== NO LEGACY HARD DELETE (with execution proof) ====================

    /**
     * DIRECT_RED: Backfill MUST NOT hard-delete legacy rows.
     * Requires POSITIVE CONTROL: backfill must have executed (canonical Person created).
     */
    @Test
    void noLegacyHardDelete_afterBackfillExecution() throws Exception {
        UUID tenantId = seedTenantA();

        int employeesBefore = countLegacyEmployees(tenantId);
        int departmentsBefore = countLegacyDepartments(tenantId);
        int positionsBefore = countLegacyPositions(tenantId);

        invokeBackfill(tenantId);

        // POSITIVE CONTROL: backfill MUST have executed — canonical Person created
        int personsCreated = countPersonsForTenant(tenantId);
        assertThat(personsCreated)
                .as("POSITIVE CONTROL: Backfill MUST have created at least 1 canonical Person (found %d) — DIRECT RED", personsCreated)
                .isGreaterThanOrEqualTo(1);

        // SAFETY: legacy rows must not be deleted
        assertThat(countLegacyEmployees(tenantId))
                .as("Legacy hr_employees rows MUST NOT be deleted (before=%d, after=%d)",
                        employeesBefore, countLegacyEmployees(tenantId))
                .isEqualTo(employeesBefore);
        assertThat(countLegacyDepartments(tenantId))
                .as("Legacy hr_departments rows MUST NOT be deleted (before=%d, after=%d)",
                        departmentsBefore, countLegacyDepartments(tenantId))
                .isEqualTo(departmentsBefore);
        assertThat(countLegacyPositions(tenantId))
                .as("Legacy hr_positions rows MUST NOT be deleted (before=%d, after=%d)",
                        positionsBefore, countLegacyPositions(tenantId))
                .isEqualTo(positionsBefore);
    }

    // ==================== TASK 4 TEMPORAL INVARIANTS ====================

    /**
     * DIRECT_RED: Backfill-created assignments MUST respect Task 4 temporal
     * invariants (no PRIMARY overlap).
     */
    @Test
    void backfillAssignmentsRespectTask4TemporalInvariants() throws Exception {
        UUID tenantId = seedTenantA();

        invokeBackfill(tenantId);

        // POSITIVE CONTROL: at least 1 assignment must exist
        int assignmentCount = countAssignments(tenantId);
        assertThat(assignmentCount)
                .as("Backfill MUST create at least 1 assignment (found %d) — DIRECT RED", assignmentCount)
                .isGreaterThanOrEqualTo(1);

        // SAFETY: no PRIMARY overlap
        int primaryOverlaps = countPrimaryAssignmentOverlaps(tenantId);
        assertThat(primaryOverlaps)
                .as("PRIMARY assignment temporal overlaps MUST NOT exist (found %d)", primaryOverlaps)
                .isZero();
    }

    // ==================== RERUN DOES NOT DUPLICATE REVIEW ITEMS ====================

    /**
     * DIRECT_RED: Running backfill twice MUST NOT create duplicate review items.
     */
    @Test
    void rerunDoesNotDuplicateReviewItems() throws Exception {
        UUID tenantId = seedTenantC(); // Will produce review items

        invokeBackfill(tenantId);
        int reviewItemsRun1 = countAllReviewItems(tenantId);

        // POSITIVE CONTROL: first run must have created at least 1 review item
        assertThat(reviewItemsRun1)
                .as("POSITIVE CONTROL: First run MUST have created at least 1 review item (found %d) — DIRECT RED", reviewItemsRun1)
                .isGreaterThanOrEqualTo(1);

        invokeBackfill(tenantId);
        int reviewItemsRun2 = countAllReviewItems(tenantId);

        assertThat(reviewItemsRun2)
                .as("Second backfill run MUST NOT create duplicate review items (run1=%d, run2=%d)",
                        reviewItemsRun1, reviewItemsRun2)
                .isEqualTo(reviewItemsRun1);
    }

    // ==================== STABLE MACHINE-READABLE ISSUE CODES ====================

    /**
     * DIRECT_RED: Review items MUST have stable, machine-readable issue codes
     * and MUST NOT have null/blank reasons.
     */
    @Test
    void reviewItemIssueCodesAreStableAndMachineReadable() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Stable Org");
        UUID sharedUserId = UUID.randomUUID();
        seedUser(tenantId, sharedUserId);
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-STABLE-1", "Stable", "One");
        seedLegacyEmployee(tenantId, sharedUserId, "EMP-STABLE-2", "Stable", "Two");

        invokeBackfill(tenantId);

        int stableCodeCount = countReviewItemsWithCode(tenantId, DUPLICATE_USER_ID);
        assertThat(stableCodeCount)
                .as("Review item with stable code '%s' MUST exist (found %d) — DIRECT RED",
                        DUPLICATE_USER_ID, stableCodeCount)
                .isGreaterThanOrEqualTo(1);

        int nullReasonCount = countReviewItemsWithNullOrBlankReason(tenantId);
        assertThat(nullReasonCount)
                .as("Review items MUST NOT have null/blank reason (found %d)", nullReasonCount)
                .isZero();
    }

    // ==================== PARTIAL/ERROR EXECUTION NEVER CLAIMS CANONICAL ====================

    /**
     * DIRECT_RED: If there's ANY unresolved item, state MUST NOT be CANONICAL.
     */
    @Test
    void partialErrorExecution_neverClaimsCanonical() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Partial Org");
        // Seed 1 resolvable + 1 unresolvable (duplicate user_id)
        UUID uid1 = UUID.randomUUID();
        seedUser(tenantId, uid1);
        seedLegacyEmployee(tenantId, uid1, "EMP-PARTIAL-OK", "Partial", "Ok");
        UUID dupUserId = UUID.randomUUID();
        seedUser(tenantId, dupUserId);
        seedLegacyEmployee(tenantId, dupUserId, "EMP-PARTIAL-DUP-1", "Partial", "Dup1");
        seedLegacyEmployee(tenantId, dupUserId, "EMP-PARTIAL-DUP-2", "Partial", "Dup2");

        invokeBackfill(tenantId);

        int unresolved = countUnresolvedMappings(tenantId);
        // There MUST be unresolved items (the duplicate user_id pair)
        assertThat(unresolved)
                .as("There MUST be at least 1 unresolved item (found %d) — DIRECT RED", unresolved)
                .isGreaterThan(0);

        // State MUST NOT be CANONICAL
        assertThat(getMigrationState(tenantId))
                .as("With %d unresolved items, state MUST NOT be CANONICAL — DIRECT RED", unresolved)
                .isNotEqualTo("CANONICAL");
    }

    // ==================== FIXTURE HELPERS — TENANT A (AUTO_READY) ====================

    /**
     * Tenant A fixture: 1 tenant + 1 legal entity + 1 eligible org + 1 user + 1 legacy employee.
     * Expected classification: AUTO_MIGRATE → CANONICAL.
     */
    private UUID seedTenantA() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Tenant A Org");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-A-" + tenantId.toString().substring(0, 8), "TenantA", "Employee");
        return tenantId;
    }

    /**
     * Tenant B fixture: 1 tenant + 2 eligible orgs (ambiguous).
     * Expected classification: MIGRATION_REVIEW_REQUIRED.
     */
    private UUID seedTenantB() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        seedOrganization(tenantId, "Tenant B Org 1");
        seedOrganization(tenantId, "Tenant B Org 2"); // Second org → ambiguous
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-B-" + tenantId.toString().substring(0, 8), "TenantB", "Employee");
        return tenantId;
    }

    /**
     * Tenant C fixture: 1 tenant + 0 legal entities (blocked).
     * Expected classification: MIGRATION_BLOCKED.
     */
    private UUID seedTenantC() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        // No organization seeded → no legal_entity ↔ org eligibility
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-C-" + tenantId.toString().substring(0, 8), "TenantC", "Employee");
        return tenantId;
    }

    // ==================== FIXTURE HELPERS — BASE ====================

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
     * Invoke the tenant-scoped backfill.
     *
     * <p>In RED (no implementation): the SQL function hr_backfill_tenant
     * does not exist. PostgreSQL raises SQLSTATE 42883 (undefined_function).
     * This is the ONLY SQLException that is tolerated — it means the GREEN
     * implementation has not been created yet.</p>
     *
     * <p>ALL other SQLExceptions (RLS failure, FK violation, CHECK violation,
     * syntax error, connection failure, etc.) are RETROWN. They are NOT
     * swallowed.</p>
     *
     * <p>Required: UNEXPECTED_SQL_EXCEPTION_SWALLOWED = 0</p>
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
            // ONLY tolerate undefined_function (SQLSTATE 42883) — the
            // GREEN implementation does not exist yet.
            if (isUndefinedFunction(e)) {
                return; // RED bootstrap — post-condition assertions will fail
            }
            // ALL other SQLExceptions MUST be rethrown — never swallowed.
            throw e;
        }
    }

    /**
     * Check if the SQLException is specifically "undefined_function" (SQLSTATE 42883).
     * This is the ONLY tolerated condition in RED.
     */
    private boolean isUndefinedFunction(SQLException e) {
        String sqlState = e.getSQLState();
        if ("42883".equals(sqlState)) {
            return true;
        }
        // Also check message as fallback (some PostgreSQL drivers return 42883
        // under different SQLSTATE in certain contexts)
        String msg = e.getMessage();
        if (msg != null && msg.contains("function") && msg.contains("does not exist")) {
            return true;
        }
        return false;
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

    private int countAllMappings(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countAutoMigrateMappings(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_legacy_employee_mappings " +
                "WHERE tenant_id = ? AND classification = 'AUTO_MIGRATE'")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countResolvedMappings(UUID tenantId) throws Exception {
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

    private int countUnresolvedMappings(UUID tenantId) throws Exception {
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

    /**
     * Count review items with a specific issue code.
     *
     * In RED: the hr_migration_review_items table does not exist.
     * PostgreSQL raises SQLSTATE 42P01 (undefined_table).
     * This is the ONLY tolerated condition — returns 0.
     *
     * ALL other SQLExceptions are RETROWN.
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
            if (isUndefinedTable(e)) {
                return 0; // RED bootstrap — table not yet created
            }
            throw e; // NEVER swallow unexpected SQL errors
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
            if (isUndefinedTable(e)) {
                return 0;
            }
            throw e;
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
            if (isUndefinedTable(e)) {
                return 0;
            }
            throw e;
        }
    }

    /**
     * Check if the SQLException is specifically "undefined_table" (SQLSTATE 42P01).
     * This is the ONLY tolerated condition for review-item queries in RED.
     */
    private boolean isUndefinedTable(SQLException e) {
        String sqlState = e.getSQLState();
        if ("42P01".equals(sqlState)) {
            return true;
        }
        String msg = e.getMessage();
        if (msg != null && msg.contains("does not exist") && msg.contains("hr_migration_review_items")) {
            return true;
        }
        return false;
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
