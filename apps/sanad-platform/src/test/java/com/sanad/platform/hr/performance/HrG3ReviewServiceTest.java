package com.sanad.platform.hr.performance;

import com.sanad.platform.hr.performance.application.HrPerformanceReviewService;
import com.sanad.platform.hr.performance.application.PerformanceReviewInput;
import com.sanad.platform.hr.performance.domain.PerformanceReview;
import com.sanad.platform.hr.performance.domain.PerformanceReview.ReviewStatus;
import com.sanad.platform.hr.performance.infrastructure.PerformanceReviewRepository;
import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G3 Task 2 — PostgreSQL Direct behavioral contract for the review
 * service (lifecycle, audit metadata, validation, duplicate protection).
 *
 * <p>Runs against host-native PostgreSQL only. Docker/Testcontainers are
 * prohibited. The fixture is self-contained per test: tenants, users,
 * canonical Person/Employment graphs and PRIMARY assignments are created
 * deterministically, so test order can never influence results.</p>
 *
 * <p>Tenant context: in production the platform middleware provides
 * {@code app.tenant_id} on every pooled connection before HR services run.
 * The harness reproduces that contract with a tenant-context DataSource
 * driven by the current test's tenant; repositories still re-assert the
 * tenant transaction-locally, and FORCE RLS stays fully enforced.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG3ReviewServiceTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    /** Acting tenant for connections created during the current test (middleware analogue). */
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private DataSource dataSource;
    private HrEmploymentScopeResolver scopeResolver;
    private PerformanceReviewRepository repository;
    private HrPerformanceReviewService service;

    @BeforeAll
    void requirePostgres() {
        Assumptions.assumeTrue(
                System.getenv("SPRING_DATASOURCE_URL") != null
                        || "sanad".equals(System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "")),
                "PostgreSQL Direct tests require SPRING_DATASOURCE_URL"
        );
        dataSource = new TenantContextDataSource();
        scopeResolver = new HrEmploymentScopeResolver(new org.springframework.jdbc.core.JdbcTemplate(dataSource));
        repository = new PerformanceReviewRepository(dataSource);
        service = new HrPerformanceReviewService(repository, scopeResolver);
    }

    @AfterEach
    void clearTenantContext() {
        CURRENT_TENANT.remove();
    }

    // ==================== lifecycle + audit metadata ====================

    @Test
    void selfReviewLifecycleAdvancesWithAuditMetadata() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID employeeUserId = seedCanonicalEmployeeGraph(tenantId, "Lifecycle", "Employee");
        actAs(tenantId);

        PerformanceReview draft = service.createSelfReview(
                tenantId, employeeUserId, input("CYCLE-2026-H1", 4, "self assessment"));

        assertThat(draft.status()).isEqualTo(ReviewStatus.DRAFT);
        assertThat(draft.version()).isZero();
        assertThat(draft.createdBy()).isEqualTo(employeeUserId);
        assertThat(draft.updatedBy()).isNull();
        assertThat(draft.createdAt()).isNotNull();
        assertThat(draft.source().name()).isEqualTo("SELF");
        // SELF reviews carry the subject as the canonical reviewer.
        assertThat(draft.reviewerEmploymentId()).isEqualTo(draft.subjectEmploymentId());
        assertThat(draft.reviewerPersonId()).isEqualTo(draft.subjectPersonId());

        PerformanceReview submitted = service.submitReview(tenantId, employeeUserId, draft.id());
        assertThat(submitted.status()).isEqualTo(ReviewStatus.SUBMITTED);
        assertThat(submitted.version()).isEqualTo(1);
        assertThat(submitted.updatedBy()).isEqualTo(employeeUserId);
        assertThat(submitted.updatedAt()).isNotNull();

        PerformanceReview acknowledged = service.acknowledgeReview(tenantId, employeeUserId, draft.id());
        assertThat(acknowledged.status()).isEqualTo(ReviewStatus.ACKNOWLEDGED);
        assertThat(acknowledged.version()).isEqualTo(2);
        assertThat(acknowledged.updatedBy()).isEqualTo(employeeUserId);
    }

    @Test
    void draftWithoutRatingCannotBeSubmitted() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID employeeUserId = seedCanonicalEmployeeGraph(tenantId, "NoRating", "Employee");
        actAs(tenantId);

        PerformanceReview draft = service.createSelfReview(
                tenantId, employeeUserId, input("CYCLE-2026-H1", null, "draft without rating"));

        assertThatThrownBy(() -> service.submitReview(tenantId, employeeUserId, draft.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_REVIEW_RATING_REQUIRED_FOR_SUBMISSION");

        PerformanceReview unchanged = service.getReviewForActor(tenantId, employeeUserId, draft.id());
        assertThat(unchanged.status()).isEqualTo(ReviewStatus.DRAFT);
        assertThat(unchanged.version()).isZero();
    }

    @Test
    void invalidLifecycleTransitionsAreDenied() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID employeeUserId = seedCanonicalEmployeeGraph(tenantId, "Transition", "Employee");
        actAs(tenantId);

        PerformanceReview draft = service.createSelfReview(
                tenantId, employeeUserId, input("CYCLE-2026-H1", 4, "self assessment"));

        // DRAFT -> ACKNOWLEDGED is denied (must be submitted first).
        assertThatThrownBy(() -> service.acknowledgeReview(tenantId, employeeUserId, draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT -> ACKNOWLEDGED");

        service.submitReview(tenantId, employeeUserId, draft.id());

        // SUBMITTED -> CANCELLED is denied (cancel is a DRAFT-only transition).
        assertThatThrownBy(() -> service.cancelReview(tenantId, employeeUserId, draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED -> CANCELLED");

        // Re-submitting a SUBMITTED review is denied.
        assertThatThrownBy(() -> service.submitReview(tenantId, employeeUserId, draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED -> SUBMITTED");

        PerformanceReview acknowledged = service.acknowledgeReview(tenantId, employeeUserId, draft.id());

        // ACKNOWLEDGED is terminal.
        assertThatThrownBy(() -> service.cancelReview(tenantId, employeeUserId, acknowledged.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACKNOWLEDGED -> CANCELLED");
        assertThatThrownBy(() -> service.acknowledgeReview(tenantId, employeeUserId, acknowledged.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACKNOWLEDGED -> ACKNOWLEDGED");
    }

    // ==================== duplicates + validation ====================

    @Test
    void duplicateManagerReviewForSamePeriodIsRejected() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID managerUserId = UUID.randomUUID();
        UUID employeeUserId = UUID.randomUUID();
        seedManagerReportingGraph(tenantId, managerUserId, employeeUserId, "Dup", null);
        actAs(tenantId);

        UUID employeeEmploymentId = employmentOf(tenantId, employeeUserId);

        service.createManagerReview(
                tenantId, managerUserId, employeeEmploymentId,
                input("CYCLE-2026-H1", 3, "first manager review"));

        assertThatThrownBy(() -> service.createManagerReview(
                tenantId, managerUserId, employeeEmploymentId,
                input("CYCLE-2026-H1", 3, "duplicate manager review")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_REVIEW_DUPLICATE_REVIEWER_SOURCE_PERIOD");
    }

    @Test
    void invalidRatingIsRejectedBeforePersistence() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID employeeUserId = seedCanonicalEmployeeGraph(tenantId, "BadRating", "Employee");
        actAs(tenantId);

        assertThatThrownBy(() -> service.createSelfReview(
                tenantId, employeeUserId, input("CYCLE-2026-H1", 6, "rating out of range")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_REVIEW_RATING_INVALID");

        assertThat(service.listReviewsForActor(tenantId, employeeUserId)).isEmpty();
    }

    @Test
    void peerReviewCannotTargetTheReviewerItself() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID employeeUserId = seedCanonicalEmployeeGraph(tenantId, "Peer", "Employee");
        actAs(tenantId);
        UUID selfEmployment = scopeResolver.requireSelfEmployment(tenantId, employeeUserId);

        assertThatThrownBy(() -> service.createPeerReview(
                tenantId, employeeUserId, selfEmployment,
                input("CYCLE-2026-H1", 4, "peer review of self")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_REVIEW_PEER_REVIEWER_MUST_DIFFER");
    }

    // ==================== reviewer integrity negatives ====================

    @Test
    void managerCannotReviewAnUnrelatedEmployment() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID managerUserId = UUID.randomUUID();
        seedManagerReportingGraph(tenantId, managerUserId, UUID.randomUUID(), "Mgr", null);
        UUID unrelatedUserId = seedCanonicalEmployeeGraph(tenantId, "Unrelated", "Employee");
        actAs(tenantId);
        UUID unrelatedEmployment = employmentOf(tenantId, unrelatedUserId);

        assertThatThrownBy(() -> service.createManagerReview(
                tenantId, managerUserId, unrelatedEmployment,
                input("CYCLE-2026-H1", 3, "not my report")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }

    @Test
    void expiredAssignmentDoesNotAuthorizeManagerReview() {
        UUID tenantId = UUID.randomUUID();
        seedTenantOnce(tenantId);
        UUID managerUserId = UUID.randomUUID();
        UUID employeeUserId = UUID.randomUUID();
        // Reporting assignment already ended yesterday.
        seedManagerReportingGraph(
                tenantId, managerUserId, employeeUserId, "Expired",
                LocalDate.now().minusDays(1));
        actAs(tenantId);

        UUID employeeEmploymentId = employmentOf(tenantId, employeeUserId);

        assertThatThrownBy(() -> service.createManagerReview(
                tenantId, managerUserId, employeeEmploymentId,
                input("CYCLE-2026-H1", 3, "expired reporting line")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }

    @Test
    void foreignTenantSubjectIsNeverReachable() {
        UUID tenantA = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        seedTenantOnce(tenantA);
        seedManagerReportingGraph(tenantA, managerUserId, UUID.randomUUID(), "Foreign", null);

        UUID tenantB = UUID.randomUUID();
        seedTenantOnce(tenantB);
        UUID foreignUserId = seedCanonicalEmployeeGraph(tenantB, "Foreign", "Target");

        actAs(tenantA);
        UUID foreignEmployment = employmentOf(tenantB, foreignUserId);

        assertThatThrownBy(() -> service.createManagerReview(
                tenantA, managerUserId, foreignEmployment,
                input("CYCLE-2026-H1", 3, "cross tenant")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }

    // ==================== helpers ====================

    private void actAs(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /** Seeds the tenant row exactly once per test; graph helpers never seed tenants. */
    private void seedTenantOnce(UUID tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove();
            seedTenant(conn, tenantId);
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    private PerformanceReviewInput input(String cycle, Integer rating, String comments) {
        return new PerformanceReviewInput(
                cycle, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), rating, comments);
    }

    private UUID employmentOf(UUID tenantId, UUID userId) {
        try (Connection conn = dataSource.getConnection()) {
            setTenant(conn, tenantId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT e.id FROM hr_employees e "
                    + "JOIN hr_people p ON p.id = e.person_id AND p.tenant_id = e.tenant_id "
                    + "WHERE e.tenant_id = ? AND p.user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("fixture lost: no employment for user " + userId);
                    }
                    return (UUID) rs.getObject(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    /** Tenant + one employee (user -> person -> ACTIVE employment). Returns the user id. */
    private UUID seedCanonicalEmployeeGraph(UUID tenantId, String first, String last) {
        return seedCanonicalEmployeeGraph(tenantId, UUID.randomUUID(), first, last);
    }

    private UUID seedCanonicalEmployeeGraph(UUID tenantId, UUID userId, String first, String last) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove(); // fixture connections set context explicitly
            setTenant(conn, tenantId);
            UUID seededUserId = seedUser(conn, tenantId, userId);
            UUID personId = seedPerson(conn, tenantId, seededUserId, first, last);
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            seedEmployment(conn, tenantId, personId, legalEntityId);
            return seededUserId;
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    /**
     * Tenant + manager and employee graphs + PRIMARY assignment where the
     * employee reports to the manager. {@code managerAssignmentEffectiveTo}
     * non-null models an expired reporting assignment.
     */
    private void seedManagerReportingGraph(
            UUID tenantId,
            UUID managerUserId,
            UUID employeeUserId,
            String label,
            LocalDate managerAssignmentEffectiveTo) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove(); // fixture connections set context explicitly
            setTenant(conn, tenantId);

            UUID orgId = seedOrganization(conn, tenantId);
            UUID legalEntityId = seedLegalEntity(conn, tenantId);

            UUID mgrUserId = seedUser(conn, tenantId, managerUserId);
            UUID mgrPersonId = seedPerson(conn, tenantId, mgrUserId, label, "Manager");
            UUID mgrEmploymentId = seedEmployment(conn, tenantId, mgrPersonId, legalEntityId);
            UUID mgrAssignmentId = seedAssignment(
                    conn, tenantId, mgrEmploymentId, orgId, null, managerAssignmentEffectiveTo);

            UUID empUserId = seedUser(conn, tenantId, employeeUserId);
            UUID empPersonId = seedPerson(conn, tenantId, empUserId, label, "Employee");
            UUID empEmploymentId = seedEmployment(conn, tenantId, empPersonId, legalEntityId);
            seedAssignment(conn, tenantId, empEmploymentId, orgId, mgrAssignmentId, null);
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    /**
     * DataSource that reproduces the production middleware contract: every
     * connection carries the acting tenant in {@code app.tenant_id} (session
     * scope) before any repository or resolver statement runs.
     */
    private final class TenantContextDataSource extends DriverManagerDataSource {
        private TenantContextDataSource() {
            setUrl(DB_URL);
            setUsername(DB_USER);
            setPassword(DB_PASSWORD);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            UUID tenantId = CURRENT_TENANT.get();
            if (tenantId != null) {
                try (Statement st = connection.createStatement()) {
                    st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
                }
            }
            return connection;
        }
    }

    private void seedTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, 'G3 Service Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "g3s-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedUser(Connection conn, UUID tenantId, UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, display_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, "g3s-" + userId.toString().substring(0, 8) + "@test.local");
            ps.setString(4, "G3 Service User");
            ps.executeUpdate();
        }
        return userId;
    }

    private UUID seedPerson(Connection conn, UUID tenantId, UUID userId, String first, String last)
            throws SQLException {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people " +
                "(id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
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

    private UUID seedLegalEntity(Connection conn, UUID tenantId) throws SQLException {
        UUID legalEntityId = UUID.randomUUID();
        String code = "G3S-" + legalEntityId.toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities " +
                "(id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "G3 Service Legal Entity " + code);
            ps.executeUpdate();
        }
        return legalEntityId;
    }

    private UUID seedEmployment(Connection conn, UUID tenantId, UUID personId, UUID legalEntityId)
            throws SQLException {
        UUID employmentId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees " +
                "(id, tenant_id, person_id, legal_entity_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 'G3', 'Service', 'G3 Service Employee', 'FULL_TIME', 'ACTIVE', " +
                "DATE '2026-01-01', 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "G3S-EMP-" + employmentId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return employmentId;
    }

    private UUID seedOrganization(Connection conn, UUID tenantId) throws SQLException {
        UUID orgId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, NULL, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.setString(3, "G3S Org " + orgId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return orgId;
    }

    private UUID seedAssignment(
            Connection conn,
            UUID tenantId,
            UUID employmentId,
            UUID organizationId,
            UUID reportsToAssignmentId,
            LocalDate effectiveTo) throws SQLException {
        UUID assignmentId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employee_assignments " +
                "(id, tenant_id, employment_id, organization_id, reports_to_assignment_id, " +
                "assignment_type, occupancy_mode, allocation_percent, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, 'PRIMARY', 'OCCUPYING', 100, ?, ?, 'ACTIVE')")) {
            ps.setObject(1, assignmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employmentId);
            ps.setObject(4, organizationId);
            if (reportsToAssignmentId != null) {
                ps.setObject(5, reportsToAssignmentId);
            } else {
                ps.setNull(5, java.sql.Types.OTHER);
            }
            ps.setObject(6, LocalDate.now().minusDays(30));
            if (effectiveTo != null) {
                ps.setObject(7, effectiveTo);
            } else {
                ps.setNull(7, java.sql.Types.DATE);
            }
            ps.executeUpdate();
        }
        return assignmentId;
    }

    private void setTenant(Connection conn, UUID tenantId) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
        }
    }
}
