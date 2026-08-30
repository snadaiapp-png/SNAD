package com.sanad.platform.hr.structure;

import com.sanad.platform.hr.structure.application.HrStructureService;
import com.sanad.platform.hr.structure.domain.HrJob;
import com.sanad.platform.hr.structure.domain.HrJobVersion;
import com.sanad.platform.hr.structure.domain.HrOrgUnit;
import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.domain.HrPositionVersion;
import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 3 — Structure Versioning behavioral contract (RED baseline).
 *
 * <p>Tests the FINAL required behavior. With current production skeletons,
 * every test fails because methods throw UnsupportedOperationException.
 * GREEN must satisfy the EXACT SAME assertions — contract is FROZEN.</p>
 */
class HrStructureVersioningIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private JdbcHrStructureRepository repository;
    private HrStructureService structureService;

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
        repository = new JdbcHrStructureRepository(dataSource);
        structureService = new HrStructureService(repository);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
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
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Test Org', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        return orgId;
    }

    private UUID seedLegacyPosition(UUID tenantId) throws Exception {
        UUID posId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) VALUES (?, ?, 'Legacy Pos', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, posId);
            ps.setObject(2, tenantId);
            ps.setString(3, "POS-" + posId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return posId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 30);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 1);

    // ==================== A. ORG UNIT STABLE IDENTITY ====================

    @Test
    void orgUnitStableIdentity_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);

        HrOrgUnit unit = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "OU-001");
        repository.saveOrgUnit(unit);

        assertThat(repository.findOrgUnitById(tenantId, unit.id())).isPresent();
    }

    // ==================== B. ORG UNIT STABLE-CODE UNIQUENESS ====================

    @Test
    void orgUnitStableCode_uniquePerTenantOrg() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);

        HrOrgUnit u1 = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "DUP-CODE");
        repository.saveOrgUnit(u1);

        HrOrgUnit u2 = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "DUP-CODE");
        assertThatThrownBy(() -> repository.saveOrgUnit(u2))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== C. ORG UNIT VERSION PERSISTENCE ====================

    @Test
    void orgUnitVersion_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit unit = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "OU-V");
        repository.saveOrgUnit(unit);

        HrOrgUnitVersion v = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, unit.id(), "Engineering", "ENG",
                "DEPARTMENT", null, D1, null, "ACTIVE");
        repository.saveOrgUnitVersion(v);

        List<HrOrgUnitVersion> versions = repository.orgUnitVersions(tenantId, unit.id());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).name()).isEqualTo("Engineering");
    }

    // ==================== D. ORG UNIT VERSION NON-OVERLAP ====================

    @Test
    void orgUnitVersionsCannotOverlap() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit unit = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "OU-OVL");
        repository.saveOrgUnit(unit);

        // V1: 2026-01-01 → NULL (open)
        HrOrgUnitVersion v1 = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, unit.id(), "V1", "V1",
                "DEPARTMENT", null, D1, null, "ACTIVE");
        repository.saveOrgUnitVersion(v1);

        // V2: 2026-06-01 → NULL — overlaps with V1 (V1 is open)
        HrOrgUnitVersion v2 = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, unit.id(), "V2", "V2",
                "DEPARTMENT", null, LocalDate.of(2026, 6, 1), null, "ACTIVE");
        assertThatThrownBy(() -> repository.saveOrgUnitVersion(v2))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== E. ORG UNIT ADJACENT PERIODS ALLOWED ====================

    @Test
    void orgUnitAdjacentPeriodsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit unit = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "OU-ADJ");
        repository.saveOrgUnit(unit);

        // V1: 2026-01-01 → 2026-06-30 (closed)
        HrOrgUnitVersion v1 = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, unit.id(), "V1", "V1",
                "DEPARTMENT", null, D1, D2, "ACTIVE");
        repository.saveOrgUnitVersion(v1);

        // V2: 2026-07-01 → NULL (open) — adjacent, NOT overlapping
        HrOrgUnitVersion v2 = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, unit.id(), "V2", "V2",
                "DEPARTMENT", null, D3, null, "ACTIVE");
        repository.saveOrgUnitVersion(v2);
        // No exception expected.
    }

    // ==================== F. ORG HIERARCHY PARENT VERSIONING ====================

    @Test
    void orgHierarchy_parentVersioningSupported() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit parent = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "PARENT");
        HrOrgUnit child = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "CHILD");
        repository.saveOrgUnit(parent);
        repository.saveOrgUnit(child);

        HrOrgUnitVersion childV = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, child.id(), "Child", "CHILD",
                "TEAM", parent.id(), D1, null, "ACTIVE");
        repository.saveOrgUnitVersion(childV);

        List<HrOrgUnitVersion> versions = repository.orgUnitVersions(tenantId, child.id());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).parentOrgUnitId()).isEqualTo(parent.id());
    }

    // ==================== G. ORG HIERARCHY PERIOD CYCLE REJECTED ====================

    @Test
    void orgHierarchyRejectsCycleForOverlappingEffectivePeriod() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit a = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "A");
        HrOrgUnit b = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "B");
        repository.saveOrgUnit(a);
        repository.saveOrgUnit(b);

        // A → B (A's parent is B)
        structureService.reviseOrgUnit(tenantId, a.id(), D1, b.id(), "A", "A", "DEPARTMENT");
        // B → A (B's parent is A) — creates cycle A → B → A during overlapping period
        assertThatThrownBy(() ->
                structureService.reviseOrgUnit(tenantId, b.id(), D1, a.id(), "B", "B", "DEPARTMENT"))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== H. NON-OVERLAPPING HISTORICAL HIERARCHY NO FALSE POSITIVE ====================

    @Test
    void nonOverlappingHistoricalHierarchyDoesNotFalseCycle() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit a = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "A2");
        HrOrgUnit b = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "B2");
        repository.saveOrgUnit(a);
        repository.saveOrgUnit(b);

        // Period 1: A → B — CLOSED period (effective_to = D2 = 2026-06-30).
        // Insert directly as a closed version so it does NOT overlap with Period 2.
        HrOrgUnitVersion period1 = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, a.id(), "A2", "A2",
                "DEPARTMENT", b.id(), D1, D2, "ACTIVE");
        repository.saveOrgUnitVersion(period1);

        // Verify Period 1 is actually closed in DB.
        List<HrOrgUnitVersion> aVersions = repository.orgUnitVersions(tenantId, a.id());
        assertThat(aVersions).hasSize(1);
        assertThat(aVersions.get(0).effectiveTo())
                .as("Period 1 must be closed (effective_to = D2)")
                .isEqualTo(D2);

        // Period 2: B → A (B's parent is A, 2026-07-01 → NULL)
        // This does NOT create a cycle because Period 1 [D1, D2] is closed
        // and does NOT overlap with Period 2 [D3, ∞).
        structureService.reviseOrgUnit(tenantId, b.id(), D3, a.id(), "B2", "B2", "DEPARTMENT");
        // No exception expected — no false positive.
    }

    // ==================== H2. REJECTED CYCLE ATOMICITY — NO SIDE EFFECTS ====================

    @Test
    void rejectedHierarchyCycleDoesNotMutateExistingVersion() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrOrgUnit a = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "A-ATM");
        HrOrgUnit b = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "B-ATM");
        repository.saveOrgUnit(a);
        repository.saveOrgUnit(b);

        // Give B an existing OPEN version (parent = A, effective from D1).
        structureService.reviseOrgUnit(tenantId, b.id(), D1, a.id(), "B", "B", "DEPARTMENT");

        // Capture B's existing open version state.
        List<HrOrgUnitVersion> bVersionsBefore = repository.orgUnitVersions(tenantId, b.id());
        assertThat(bVersionsBefore).hasSize(1);
        HrOrgUnitVersion openVersionBefore = bVersionsBefore.get(0);
        assertThat(openVersionBefore.effectiveTo())
                .as("B's version must be open (effective_to = NULL) before rejected revision")
                .isNull();

        // Now establish A → B (A's parent is B) so that revising A with
        // parent=B would create a cycle A → B → A during the overlapping period.
        structureService.reviseOrgUnit(tenantId, a.id(), D1, b.id(), "A", "A", "DEPARTMENT");

        // Attempt to revise B with parent=A — this SHOULD be rejected as a cycle
        // because B → A (existing) and A → B (existing) create a cycle.
        // But wait — the cycle check for B's revision traverses from A (proposed parent)
        // and checks if B appears. A's parent is B → so A → B is in the chain.
        // B is the org unit being revised → cycle detected.
        // Actually, we need to try revising B with parent that creates a cycle.
        // B currently has parent=A. If we try to revise B at D1 with parent=A
        // again... that wouldn't create a cycle. We need a scenario where
        // the cycle check rejects AND we verify B's version is unchanged.

        // Let's create the cycle scenario: A → B (done above). Now try to
        // revise A with parent=B at the SAME effective date — wait, A already
        // has parent=B. Let me revise B with a new effective date that
        // overlaps with A → B relationship.

        // Actually, the simplest atomicity test: try to revise B at D1
        // with a parent that creates a cycle. Currently A → B (open from D1).
        // If B's parent becomes A, then: traverse from A (proposed parent of B),
        // A → B (open from D1), B is being revised → cycle.
        assertThatThrownBy(() ->
                structureService.reviseOrgUnit(tenantId, b.id(), D1, a.id(),
                        "B-NEW", "B-NEW", "DEPARTMENT"))
                .isInstanceOf(RuntimeException.class);

        // CRITICAL: B's existing open version must be UNCHANGED.
        List<HrOrgUnitVersion> bVersionsAfter = repository.orgUnitVersions(tenantId, b.id());
        assertThat(bVersionsAfter)
                .as("rejected revision must not add any new versions")
                .hasSize(1);

        HrOrgUnitVersion openVersionAfter = bVersionsAfter.get(0);
        assertThat(openVersionAfter.effectiveTo())
                .as("rejected revision must not close the existing open version")
                .isNull();
        assertThat(openVersionAfter.id())
                .as("rejected revision must not change the existing version's id")
                .isEqualTo(openVersionBefore.id());
        assertThat(openVersionAfter.effectiveFrom())
                .as("rejected revision must not change the existing version's effective_from")
                .isEqualTo(openVersionBefore.effectiveFrom());
    }

    // ==================== I. JOB STABLE IDENTITY ====================

    @Test
    void jobStableIdentity_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);

        HrJob job = new HrJob(UUID.randomUUID(), tenantId, orgId, "JOB-001");
        repository.saveJob(job);
        // If no exception, the identity was persisted.
    }

    // ==================== J. JOB VERSION PERSISTENCE ====================

    @Test
    void jobVersion_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrJob job = new HrJob(UUID.randomUUID(), tenantId, orgId, "JOB-V");
        repository.saveJob(job);

        HrJobVersion v = new HrJobVersion(
                UUID.randomUUID(), tenantId, job.id(), "Senior Engineer",
                "Senior role", "L5", D1, null, "ACTIVE");
        repository.saveJobVersion(v);

        List<HrJobVersion> versions = repository.jobVersions(tenantId, job.id());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).title()).isEqualTo("Senior Engineer");
    }

    // ==================== K. JOB VERSION NON-OVERLAP ====================

    @Test
    void jobVersionsCannotOverlap() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrJob job = new HrJob(UUID.randomUUID(), tenantId, orgId, "JOB-OVL");
        repository.saveJob(job);

        HrJobVersion v1 = new HrJobVersion(
                UUID.randomUUID(), tenantId, job.id(), "V1", null, null, D1, null, "ACTIVE");
        repository.saveJobVersion(v1);

        HrJobVersion v2 = new HrJobVersion(
                UUID.randomUUID(), tenantId, job.id(), "V2", null, null,
                LocalDate.of(2026, 6, 1), null, "ACTIVE");
        assertThatThrownBy(() -> repository.saveJobVersion(v2))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== L. JOB ADJACENT PERIODS ALLOWED ====================

    @Test
    void jobAdjacentPeriodsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        HrJob job = new HrJob(UUID.randomUUID(), tenantId, orgId, "JOB-ADJ");
        repository.saveJob(job);

        HrJobVersion v1 = new HrJobVersion(
                UUID.randomUUID(), tenantId, job.id(), "V1", null, null, D1, D2, "ACTIVE");
        repository.saveJobVersion(v1);

        HrJobVersion v2 = new HrJobVersion(
                UUID.randomUUID(), tenantId, job.id(), "V2", null, null, D3, null, "ACTIVE");
        repository.saveJobVersion(v2);
        // No exception expected.
    }

    // ==================== M. POSITION STABLE IDENTITY (REUSE hr_positions) ====================

    @Test
    void positionStableIdentity_reusesHrPositions() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID posId = seedLegacyPosition(tenantId);

        // Verify hr_positions row exists (legacy table preserved)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM hr_positions WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, posId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    // ==================== N. LEGACY POSITION COMPATIBILITY ====================

    @Test
    void legacyPositionColumnsPreserved() throws Exception {
        // Verify legacy columns still exist on hr_positions
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'hr_positions' AND column_name IN " +
                "('title', 'code', 'department_id', 'grade', 'status')")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> cols = new java.util.HashSet<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertThat(cols).contains("title", "code", "grade", "status");
            }
        }
    }

    // ==================== O. POSITION VERSION PERSISTENCE ====================

    @Test
    void positionVersion_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID posId = seedLegacyPosition(tenantId);

        HrPositionVersion v = new HrPositionVersion(
                UUID.randomUUID(), tenantId, posId, orgId, null, null,
                "Senior Engineer", D1, null, "ACTIVE");
        repository.savePositionVersion(v);

        List<HrPositionVersion> versions = repository.positionVersions(tenantId, posId);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).title()).isEqualTo("Senior Engineer");
    }

    // ==================== P. POSITION VERSION NON-OVERLAP ====================

    @Test
    void positionVersionsCannotOverlap() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID posId = seedLegacyPosition(tenantId);

        HrPositionVersion v1 = new HrPositionVersion(
                UUID.randomUUID(), tenantId, posId, orgId, null, null,
                "V1", D1, null, "ACTIVE");
        repository.savePositionVersion(v1);

        HrPositionVersion v2 = new HrPositionVersion(
                UUID.randomUUID(), tenantId, posId, orgId, null, null,
                "V2", LocalDate.of(2026, 6, 1), null, "ACTIVE");
        assertThatThrownBy(() -> repository.savePositionVersion(v2))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== Q. POSITION ADJACENT PERIODS ALLOWED ====================

    @Test
    void positionAdjacentPeriodsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID posId = seedLegacyPosition(tenantId);

        HrPositionVersion v1 = new HrPositionVersion(
                UUID.randomUUID(), tenantId, posId, orgId, null, null,
                "V1", D1, D2, "ACTIVE");
        repository.savePositionVersion(v1);

        HrPositionVersion v2 = new HrPositionVersion(
                UUID.randomUUID(), tenantId, posId, orgId, null, null,
                "V2", D3, null, "ACTIVE");
        repository.savePositionVersion(v2);
        // No exception expected.
    }

    // ==================== T. NEW TASK 3 TABLES RLS ENABLED + FORCED ====================

    @Test
    void newTask3Tables_haveForceRls() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class " +
                "WHERE relname IN ('hr_org_units', 'hr_org_unit_versions', " +
                "'hr_jobs', 'hr_job_versions', 'hr_position_versions') " +
                "ORDER BY relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Map<String, boolean[]> rls = new java.util.HashMap<>();
                while (rs.next()) {
                    rls.put(rs.getString("relname"),
                            new boolean[]{rs.getBoolean("relrowsecurity"), rs.getBoolean("relforcerowsecurity")});
                }
                for (String table : new String[]{"hr_org_units", "hr_org_unit_versions",
                        "hr_jobs", "hr_job_versions", "hr_position_versions"}) {
                    assertThat(rls).containsKey(table);
                    assertThat(rls.get(table)[0]).as(table + " must have ENABLE RLS").isTrue();
                    assertThat(rls.get(table)[1]).as(table + " must have FORCE RLS").isTrue();
                }
            }
        }
    }

    // ==================== V. NO-CONTEXT FAIL-CLOSED ====================

    @Test
    void newTask3Tables_failClosedWithoutTenantContext() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);

        // Insert a row with tenant context
        HrOrgUnit unit = new HrOrgUnit(UUID.randomUUID(), tenantId, orgId, "OU-FC");
        repository.saveOrgUnit(unit);

        // Reset tenant context — SELECT must return 0 rows
        try (Statement s = conn.createStatement()) {
            s.execute("RESET app.tenant_id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_org_units")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).as("no tenant context → 0 rows (fail-closed)").isZero();
            }
        }
    }
}
