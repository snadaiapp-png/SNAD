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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 4 — Assignment Temporal Constraint RED baseline.
 *
 * Tests the FINAL required behavior for assignment persistence,
 * PRIMARY overlap, position occupancy, allocation, and RLS.
 * Contract is FROZEN — GREEN must satisfy exact same assertions.
 */
class HrAssignmentTemporalConstraintTest {

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

    private UUID seedLegacyPosition(UUID tenantId) throws Exception {
        UUID posId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) VALUES (?, ?, 'Test Pos', ?, 'ACTIVE', NOW(), NOW())")) {
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
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal TWENTY = new BigDecimal("20");

    // ==================== A. ASSIGNMENT PERSISTENCE ====================

    @Test
    void assignment_persistedWithCorrectFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-A");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Assign", "Test");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-A");

        HrAssignment a = assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        assertThat(a.id()).isNotNull();
        assertThat(a.tenantId()).isEqualTo(tenantId);
        assertThat(a.employmentId()).isEqualTo(empId);
        assertThat(a.organizationId()).isEqualTo(orgId);
        assertThat(a.assignmentType()).isEqualTo(AssignmentType.PRIMARY);
        assertThat(a.allocationPercent()).isEqualByComparingTo(HUNDRED);
        assertThat(a.status()).isEqualTo("ACTIVE");
    }

    // ==================== B. EMPLOYMENT → ASSIGNMENT RELATION ====================

    @Test
    void assignment_linkedToEmployment() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-B");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Emp", "Link");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-B");

        HrAssignment a = assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        var found = repository.findAssignmentById(tenantId, a.id());
        assertThat(found).isPresent();
        assertThat(found.get().employmentId()).isEqualTo(empId);
    }

    // ==================== C. PRIMARY OVERLAP REJECTED ====================

    @Test
    void primaryOverlap_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-C");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Primary", "Overlap");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-C");

        // First PRIMARY — open from D1.
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Second PRIMARY — overlapping (starts mid-period).
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        HUNDRED, LocalDate.of(2026, 6, 1), null))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== D. PRIMARY ADJACENT PERIODS ALLOWED ====================

    @Test
    void primaryAdjacentPeriods_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-D");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Adjacent", "Primary");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-D");

        // First PRIMARY — D1 to D2 (closed).
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, D2);

        // Second PRIMARY — D3 onward (adjacent, not overlapping).
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D3, null);
        // No exception expected.
    }

    // ==================== E. POSITION OCCUPYING OVERLAP REJECTED ====================

    @Test
    void positionOccupyingOverlap_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-E");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personA = seedPerson(tenantId, "Pos", "A");
        UUID personB = seedPerson(tenantId, "Pos", "B");
        UUID empA = seedEmployment(tenantId, personA, leId, "EMP-E1");
        UUID empB = seedEmployment(tenantId, personB, leId, "EMP-E2");
        UUID posId = seedLegacyPosition(tenantId);
        seedPositionVersion(tenantId, posId, orgId, "Test Pos", D1, null);

        // First OCCUPYING — open from D1.
        assignmentService.createAssignment(
                tenantId, empA, orgId, null, posId, null, null,
                AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                HUNDRED, D1, null);

        // Second OCCUPYING on same Position — overlapping.
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empB, orgId, null, posId, null, null,
                        AssignmentType.SECONDARY, OccupancyMode.OCCUPYING,
                        TWENTY, LocalDate.of(2026, 5, 1), null))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== F. POSITION ADJACENT OCCUPANCY ALLOWED ====================

    @Test
    void positionAdjacentOccupancy_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-F");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personA = seedPerson(tenantId, "Adj", "PosA");
        UUID personB = seedPerson(tenantId, "Adj", "PosB");
        UUID empA = seedEmployment(tenantId, personA, leId, "EMP-F1");
        UUID empB = seedEmployment(tenantId, personB, leId, "EMP-F2");
        UUID posId = seedLegacyPosition(tenantId);
        seedPositionVersion(tenantId, posId, orgId, "Test Pos", D1, null);

        // First OCCUPYING — D1 to D2 (closed).
        assignmentService.createAssignment(
                tenantId, empA, orgId, null, posId, null, null,
                AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                HUNDRED, D1, D2);

        // Second OCCUPYING — D3 onward (adjacent).
        assignmentService.createAssignment(
                tenantId, empB, orgId, null, posId, null, null,
                AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                HUNDRED, D3, null);
        // No exception expected.
    }

    // ==================== G. NON_OCCUPYING DOES NOT CONSUME SEAT ====================

    @Test
    void nonOccupying_doesNotConsumeSeat() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-G");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personA = seedPerson(tenantId, "Non", "OccA");
        UUID personB = seedPerson(tenantId, "Non", "OccB");
        UUID empA = seedEmployment(tenantId, personA, leId, "EMP-G1");
        UUID empB = seedEmployment(tenantId, personB, leId, "EMP-G2");
        UUID posId = seedLegacyPosition(tenantId);
        seedPositionVersion(tenantId, posId, orgId, "Test Pos", D1, null);

        // First OCCUPYING — open from D1.
        assignmentService.createAssignment(
                tenantId, empA, orgId, null, posId, null, null,
                AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                HUNDRED, D1, null);

        // Second NON_OCCUPYING on same Position — should be ALLOWED
        // (does not consume seat exclusivity).
        assignmentService.createAssignment(
                tenantId, empB, orgId, null, posId, null, null,
                AssignmentType.SECONDARY, OccupancyMode.NON_OCCUPYING,
                TWENTY, LocalDate.of(2026, 5, 1), null);
        // No exception expected.
    }

    // ==================== H. ALLOCATION > 0 ====================

    @Test
    void allocation_zero_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-H");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Alloc", "Zero");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-H");

        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        BigDecimal.ZERO, D1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allocation");
    }

    // ==================== I. ALLOCATION <= 100 ====================

    @Test
    void allocation_overHundred_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-I");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Alloc", "Over");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-I");

        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        new BigDecimal("101"), D1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allocation");
    }

    // ==================== S. NEW TABLE RLS ENABLED + FORCED ====================

    @Test
    void newAssignmentTable_hasForceRls() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'hr_employee_assignments'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("hr_employee_assignments table must exist")
                        .isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("hr_employee_assignments must have ENABLE RLS").isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("hr_employee_assignments must have FORCE RLS").isTrue();
            }
        }
    }

    // ==================== U. NO-CONTEXT FAIL-CLOSED ====================

    @Test
    void noTenantContext_failClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-U");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Fail", "Closed");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-U");

        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Reset tenant context — SELECT must return 0 rows.
        try (Statement s = conn.createStatement()) {
            s.execute("RESET app.tenant_id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_employee_assignments")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("no tenant context → 0 rows (fail-closed)").isZero();
            }
        }
    }

    // ==================== H2. ALLOCATION NEGATIVE REJECTED ====================

    @Test
    void allocation_negative_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-H2");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Alloc", "Neg");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-H2");

        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        new BigDecimal("-1"), D1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allocation");
    }

    // ==================== J. TOTAL EFFECTIVE ALLOCATION = 100% ALLOWED ====================

    @Test
    void totalEffectiveAllocation_100Percent_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-J");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Total", "Alloc");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-J");

        // PRIMARY 80% + SECONDARY 20% = 100% → PASS
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                EIGHTY, D1, null);
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.SECONDARY, OccupancyMode.NON_OCCUPYING,
                TWENTY, D1, null);
        // No exception expected.
    }

    // ==================== J2. TOTAL EFFECTIVE ALLOCATION > 100% REJECTED ====================

    @Test
    void totalEffectiveAllocation_over100_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-J2");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Over", "Alloc");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-J2");

        // PRIMARY 80%
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                EIGHTY, D1, null);

        // SECONDARY 30% → total = 110% → REJECT
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, null, null, null,
                        AssignmentType.SECONDARY, OccupancyMode.NON_OCCUPYING,
                        new BigDecimal("30"), D1, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("allocation");
    }

    // ==================== J3. PERIOD-AWARE ALLOCATION — NON-OVERLAPPING ====================

    @Test
    void allocation_periodAware_nonOverlappingAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-J3");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Period", "Aware");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-J3");

        // PRIMARY 100% in Period 1 [D1, D2] (closed)
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, D2);

        // SECONDARY 20% in Period 2 [D3, NULL] — non-overlapping → PASS
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.SECONDARY, OccupancyMode.NON_OCCUPYING,
                TWENTY, D3, null);
        // No exception expected.
    }

    // ==================== K. LEGAL ENTITY ↔ ORGANIZATION ELIGIBILITY ====================

    @Test
    void legalEntityOrgEligibility_eligibleOrg_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-K");
        seedOrgLegalEntity(tenantId, orgId, leId);
        // Seed eligibility: org ↔ le is ACTIVE
        UUID personId = seedPerson(tenantId, "Eligible", "Org");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K");

        // Assignment to eligible org → PASS
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);
        // No exception expected.
    }

    @Test
    void legalEntityOrgEligibility_ineligibleOrg_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID orgId2 = seedOrganization(tenantId); // second org, no eligibility
        UUID leId = seedLegalEntity(tenantId, "LE-K2");
        // Only orgId is eligible — orgId2 is NOT
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Ineligible", "Org");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K2");

        // Assignment to ineligible orgId2 → REJECT
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId2, null, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        HUNDRED, D1, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("eligib");
    }

    // ==================== K2. EFFECTIVE ORG UNIT VALIDATION ====================

    @Test
    void orgUnitEffectiveness_expiredOrgUnit_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-K2B");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "OrgUnit", "Expired");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K2B");

        // Create Org Unit with a version that EXPIRED before D1
        UUID orgUnitId = seedOrgUnitWithVersion(tenantId, orgId, "OU-EXP",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)); // expired

        // Assignment at D1 references expired Org Unit → REJECT
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, orgUnitId, null, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                        HUNDRED, D1, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("org unit");
    }

    @Test
    void orgUnitEffectiveness_effectiveOrgUnit_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-K2C");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "OrgUnit", "Active");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K2C");

        // Create Org Unit with an effective version covering D1
        UUID orgUnitId = seedOrgUnitWithVersion(tenantId, orgId, "OU-ACT",
                D1, null); // open from D1

        // Assignment at D1 references effective Org Unit → PASS
        assignmentService.createAssignment(
                tenantId, empId, orgId, orgUnitId, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);
        // No exception expected.
    }

    // ==================== K3. EFFECTIVE POSITION VALIDATION ====================

    @Test
    void positionEffectiveness_noVersion_rejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-K3");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Pos", "NoVer");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K3");
        UUID posId = seedLegacyPosition(tenantId);

        // Position exists as stable identity but has NO Position Version
        // → Assignment referencing it should be rejected
        assertThatThrownBy(() ->
                assignmentService.createAssignment(
                        tenantId, empId, orgId, null, posId, null, null,
                        AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                        HUNDRED, D1, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("position");
    }

    @Test
    void positionEffectiveness_effectiveVersion_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId, "LE-K3B");
        seedOrgLegalEntity(tenantId, orgId, leId);
        UUID personId = seedPerson(tenantId, "Pos", "EffVer");
        UUID empId = seedEmployment(tenantId, personId, leId, "EMP-K3B");
        UUID posId = seedLegacyPosition(tenantId);

        // Create Position Version covering D1

        // Assignment referencing Position with effective version → PASS
        assignmentService.createAssignment(
                tenantId, empId, orgId, null, posId, null, null,
                AssignmentType.PRIMARY, OccupancyMode.OCCUPYING,
                HUNDRED, D1, null);
        // No exception expected.
    }

    // ==================== V. WRONG-TENANT READ BLOCKED ====================

    @Test
    void wrongTenantRead_blocked() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID orgA = seedOrganization(tenantA);
        UUID leA = seedLegalEntity(tenantA, "LE-V");
        seedOrgLegalEntity(tenantA, orgA, leA);
        UUID personA = seedPerson(tenantA, "Wrong", "Read");
        UUID empA = seedEmployment(tenantA, personA, leA, "EMP-V");

        // Create assignment in Tenant A
        HrAssignment a = assignmentService.createAssignment(
                tenantA, empA, orgA, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Tenant B tries to read Tenant A's assignment → must not find it
        setTenant(tenantB);
        var found = repository.findAssignmentById(tenantB, a.id());
        assertThat(found)
                .as("Tenant B must not see Tenant A's assignment")
                .isEmpty();
    }

    // ==================== W. WRONG-TENANT WRITE BLOCKED ====================

    @Test
    void wrongTenantWrite_blocked() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID orgA = seedOrganization(tenantA);
        UUID leA = seedLegalEntity(tenantA, "LE-W");
        seedOrgLegalEntity(tenantA, orgA, leA);
        UUID personA = seedPerson(tenantA, "Wrong", "Write");
        UUID empA = seedEmployment(tenantA, personA, leA, "EMP-W");

        HrAssignment a = assignmentService.createAssignment(
                tenantA, empA, orgA, null, null, null, null,
                AssignmentType.PRIMARY, OccupancyMode.NON_OCCUPYING,
                HUNDRED, D1, null);

        // Tenant B tries to revise Tenant A's assignment → must be rejected
        setTenant(tenantB);
        assertThatThrownBy(() ->
                assignmentService.reviseAssignment(
                        tenantB, a.id(), D1, null, null,
                        OccupancyMode.NON_OCCUPYING, HUNDRED))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== FIXTURE HELPERS (additional) ====================

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

    private UUID seedOrgUnitWithVersion(UUID tenantId, UUID orgId, String code,
                                          LocalDate from, LocalDate to) throws Exception {
        UUID orgUnitId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())")) {
            ps.setObject(1, orgUnitId);
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.setString(4, code);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_org_unit_versions (id, tenant_id, org_unit_id, name, code, unit_type, " +
                "parent_org_unit_id, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, 'DEPARTMENT', NULL, ?::date, ?::date, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgUnitId);
            ps.setString(4, code);
            ps.setString(5, code);
            ps.setString(6, from.toString());
            if (to != null) ps.setString(7, to.toString());
            else ps.setNull(7, java.sql.Types.DATE);
            ps.executeUpdate();
        }
        return orgUnitId;
    }

    private void seedPositionVersion(UUID tenantId, UUID posId, UUID orgId,
                                        String title, LocalDate from, LocalDate to) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, " +
                "job_id, org_unit_id, title, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?::date, ?::date, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, posId);
            ps.setObject(4, orgId);
            ps.setString(5, title);
            ps.setString(6, from.toString());
            if (to != null) ps.setString(7, to.toString());
            else ps.setNull(7, java.sql.Types.DATE);
            ps.executeUpdate();
        }
    }

}
