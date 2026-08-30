package com.sanad.platform.hr.assignment;

import com.sanad.platform.hr.assignment.application.HrAssignmentService;
import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 4 — Reporting Cycle Integration RED baseline.
 *
 * Tests the FINAL required behavior for reporting relationships,
 * self-reporting rejection, 2-node and multi-hop cycle detection,
 * and historical non-overlap reporting.
 * Contract is FROZEN — GREEN must satisfy exact same assertions.
 */
class HrReportingCycleIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private JdbcHrAssignmentRepository repository;
    private HrAssignmentService assignmentService;

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
        repository = new JdbcHrAssignmentRepository(dataSource);
        assignmentService = new HrAssignmentService(repository);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ==================== FIXTURE HELPERS (same as temporal test) ====================

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

    private UUID seedPerson(UUID tenantId, String first, String last) throws Exception {
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

    private UUID seedEmployment(UUID tenantId, UUID personId, UUID legalEntityId, String empNum) throws Exception {
        UUID empId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                "first_name, last_name, display_name, employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, empNum);
            ps.setString(6, "Test");
            ps.setString(7, "Employee");
            ps.setString(8, "Test Employee");
            ps.setString(9, "2026-01-01");
            ps.executeUpdate();
        }
        return empId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 30);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 1);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // Helper: create a full employment chain and return the employmentId
    private UUID[] seedFullEmploymentChain(UUID tenantId, String prefix) throws Exception {
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-" + prefix);
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, prefix, "Person");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-" + prefix);
        seedOrgLegalEntity(tenantId, orgId, leId);
        return new UUID[]{orgId, leId, personId, empId};
    }

    // ==================== M. REPORTS_TO PERSISTENCE ====================

    @Test
    void reportsToAssignmentId_persisted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID[] managerChain = seedFullEmploymentChain(tenantId, "Mgr");
        UUID[] employeeChain = seedFullEmploymentChain(tenantId, "Emp");
        UUID orgId = managerChain[0];
        UUID mgrEmp = managerChain[3];
        UUID empEmp = employeeChain[3];

        // Create manager's PRIMARY assignment.
        HrAssignment mgrAssign = assignmentService.createAssignment(
                tenantId, mgrEmp, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Create employee's assignment reporting to manager.
        HrAssignment empAssign = assignmentService.createAssignment(
                tenantId, empEmp, orgId, null, null, mgrAssign.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        assertThat(empAssign.reportsToAssignmentId()).isEqualTo(mgrAssign.id());
    }

    // ==================== N. SELF-REPORTING REJECTED ====================

    @Test
    void selfReporting_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID[] chain = seedFullEmploymentChain(tenantId, "Self");
        UUID orgId = chain[0];
        UUID empId = chain[3];

        // Create assignment, then try to make it report to itself.
        HrAssignment a = assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Revise to set reports_to = self → should be rejected.
        assertThatThrownBy(() ->
                assignmentService.reviseAssignment(
                        tenantId, a.id(), D1, a.id(), null,
                        OccupancyMode.NON_OCCUPYING, HUNDRED))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== O. 2-NODE REPORTING CYCLE REJECTED ====================

    @Test
    void twoNodeReportingCycle_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID[] mgrChain = seedFullEmploymentChain(tenantId, "TwoA");
        UUID[] empChain = seedFullEmploymentChain(tenantId, "TwoB");
        UUID orgId = mgrChain[0];
        UUID mgrEmp = mgrChain[3];
        UUID empEmp = empChain[3];

        // Manager's assignment (no reporting yet).
        HrAssignment mgrAssign = assignmentService.createAssignment(
                tenantId, mgrEmp, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Employee reports to manager.
        HrAssignment empAssign = assignmentService.createAssignment(
                tenantId, empEmp, orgId, null, null, mgrAssign.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Now try to make manager report to employee → cycle!
        assertThatThrownBy(() ->
                assignmentService.reviseAssignment(
                        tenantId, mgrAssign.id(), D1, empAssign.id(), null,
                        OccupancyMode.NON_OCCUPYING, HUNDRED))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== P. MULTI-HOP REPORTING CYCLE REJECTED ====================

    @Test
    void multiHopReportingCycle_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID[] chainA = seedFullEmploymentChain(tenantId, "MultiA");
        UUID[] chainB = seedFullEmploymentChain(tenantId, "MultiB");
        UUID[] chainC = seedFullEmploymentChain(tenantId, "MultiC");
        UUID orgId = chainA[0];
        UUID empA = chainA[3];
        UUID empB = chainB[3];
        UUID empC = chainC[3];

        // A (no manager)
        HrAssignment assignA = assignmentService.createAssignment(
                tenantId, empA, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // B reports to A
        HrAssignment assignB = assignmentService.createAssignment(
                tenantId, empB, orgId, null, null, assignA.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // C reports to B
        HrAssignment assignC = assignmentService.createAssignment(
                tenantId, empC, orgId, null, null, assignB.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Try to make A report to C → cycle A→C→B→A!
        assertThatThrownBy(() ->
                assignmentService.reviseAssignment(
                        tenantId, assignA.id(), D1, assignC.id(), null,
                        OccupancyMode.NON_OCCUPYING, HUNDRED))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== Q. HISTORICAL NON-OVERLAP REPORTING NO FALSE POSITIVE ====================

    @Test
    void historicalNonOverlapReporting_noFalseCycle() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID[] chainA = seedFullEmploymentChain(tenantId, "HistA");
        UUID[] chainB = seedFullEmploymentChain(tenantId, "HistB");
        UUID orgId = chainA[0];
        UUID empA = chainA[3];
        UUID empB = chainB[3];

        // Period 1: A reports to B (D1 → D2, CLOSED).
        HrAssignment assignB_p1 = assignmentService.createAssignment(
                tenantId, empB, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, D2);

        HrAssignment assignA_p1 = assignmentService.createAssignment(
                tenantId, empA, orgId, null, null, assignB_p1.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, D2);

        // Period 2: B reports to A (D3 → NULL).
        // This is NOT a cycle because Period 1 [D1, D2] is closed
        // and does NOT overlap with Period 2 [D3, ∞).
        HrAssignment assignA_p2 = assignmentService.createAssignment(
                tenantId, empA, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D3, null);

        // B reports to A in Period 2 — no cycle.
        assignmentService.createAssignment(
                tenantId, empB, orgId, null, null, assignA_p2.id(), null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D3, null);
        // No exception expected.
    }

    // ==================== R. CROSS-TENANT REPORTING LINK REJECTED ====================

    @Test
    void crossTenantReportingLink_rejected() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID[] chainA = seedFullEmploymentChain(tenantA, "CrossA");
        UUID orgA = chainA[0];
        UUID empA = chainA[3];

        setTenant(tenantB);
        UUID[] chainB = seedFullEmploymentChain(tenantB, "CrossB");
        UUID orgB = chainB[0];
        UUID empB = chainB[3];

        // Create assignment in Tenant A.
        setTenant(tenantA);
        HrAssignment assignA = assignmentService.createAssignment(
                tenantA, empA, orgA, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Create assignment in Tenant B.
        setTenant(tenantB);
        HrAssignment assignB = assignmentService.createAssignment(
                tenantB, empB, orgB, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Try to make Tenant B assignment report to Tenant A assignment.
        // This must be rejected — cross-tenant reporting link.
        assertThatThrownBy(() ->
                assignmentService.reviseAssignment(
                        tenantB, assignB.id(), D1, assignA.id(), null,
                        OccupancyMode.NON_OCCUPYING, HUNDRED))
                .isInstanceOf(RuntimeException.class);
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


}
