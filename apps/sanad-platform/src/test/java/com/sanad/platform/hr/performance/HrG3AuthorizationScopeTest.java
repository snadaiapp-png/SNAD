package com.sanad.platform.hr.performance;

import com.sanad.platform.hr.performance.application.HrPerformanceReviewService;
import com.sanad.platform.hr.performance.application.PerformanceReviewInput;
import com.sanad.platform.hr.performance.domain.PerformanceReview;
import com.sanad.platform.hr.performance.infrastructure.PerformanceReviewRepository;
import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G3 Task 2 — PostgreSQL Direct behavioral authorization-scope contract.
 *
 * <p>Proves on the real database path (execution order §17/§18):</p>
 * <ul>
 *   <li>SELF resolves only through {@code User -> Person -> ACTIVE Employment};
 *       a user without an active employment fails closed;</li>
 *   <li>a SELF-scoped user cannot read another employee's review inside the
 *       same tenant;</li>
 *   <li>manager TEAM visibility derives exclusively from effective-dated
 *       PRIMARY assignment reporting ({@code reports_to_assignment_id}); an
 *       unrelated employee, an expired assignment, and a foreign tenant never
 *       grant access;</li>
 *   <li>there is no implicit admin/wide path in the service layer — every
 *       grant above is denied by default (capability wiring for the
 *       {@code HRM.PERFORMANCE.*} codes arrives with the G3 API slice).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG3AuthorizationScopeTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

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
        scopeResolver = new HrEmploymentScopeResolver(new JdbcTemplate(dataSource));
        repository = new PerformanceReviewRepository(dataSource);
        service = new HrPerformanceReviewService(repository, scopeResolver);
    }

    @AfterEach
    void clearTenantContext() {
        CURRENT_TENANT.remove();
    }

    // ==================== SELF scope ====================

    @Test
    void userWithoutActiveEmploymentFailsClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID userOnlyId = seedTenantUserWithoutEmployment(tenantId);
        actAs(tenantId);

        assertThatThrownBy(() -> service.createSelfReview(
                tenantId, userOnlyId, input("CYCLE-2026-H1", 4, "should fail")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no active HR employment");

        assertThatThrownBy(() -> service.listReviewsForActor(tenantId, userOnlyId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no active HR employment");
    }

    @Test
    void selfScopeCannotReadAnotherEmployeesReview() {
        UUID tenantId = UUID.randomUUID();
        Scenario scenario = seedReportingScenario(tenantId, null);
        UUID unrelatedUserId = seedCanonicalEmployeeGraph(tenantId, "Unrelated", "Peer");
        actAs(tenantId);

        PerformanceReview selfReview = service.createSelfReview(
                scenario.tenantId(), scenario.employeeUserId(),
                input("CYCLE-2026-H1", 4, "employee self assessment"));

        // The unrelated same-tenant employee is denied on read and list.
        assertThatThrownBy(() -> service.getReviewForActor(
                scenario.tenantId(), unrelatedUserId, selfReview.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not visible");
        assertThat(service.listReviewsForActor(scenario.tenantId(), unrelatedUserId)).isEmpty();

        // The unrelated employee cannot mutate someone else's review either.
        assertThatThrownBy(() -> service.submitReview(
                scenario.tenantId(), unrelatedUserId, selfReview.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("canonical reviewer");

        // The subject still sees and submits their own review.
        assertThat(service.getReviewForActor(
                scenario.tenantId(), scenario.employeeUserId(), selfReview.id()).id())
                .isEqualTo(selfReview.id());
    }

    // ==================== TEAM scope ====================

    @Test
    void managerTeamScopeSeesActiveDirectReportReviews() {
        UUID tenantId = UUID.randomUUID();
        Scenario scenario = seedReportingScenario(tenantId, null);
        actAs(tenantId);

        PerformanceReview selfReview = service.createSelfReview(
                scenario.tenantId(), scenario.employeeUserId(),
                input("CYCLE-2026-H1", 3, "report self assessment"));

        // TEAM read: the manager sees the direct report's review.
        List<PerformanceReview> teamReviews =
                service.listTeamReviews(scenario.tenantId(), scenario.managerUserId());
        assertThat(teamReviews).extracting(PerformanceReview::id).containsExactly(selfReview.id());

        // TEAM read-by-id: manager may load the direct report's review.
        assertThat(service.getReviewForActor(
                scenario.tenantId(), scenario.managerUserId(), selfReview.id()).id())
                .isEqualTo(selfReview.id());

        // The manager may create a MANAGER review for the direct report (TEAM write).
        PerformanceReview managerReview = service.createManagerReview(
                scenario.tenantId(), scenario.managerUserId(),
                employmentOf(scenario.tenantId(), scenario.employeeUserId()),
                input("CYCLE-2026-H1", 5, "manager assessment"));
        assertThat(managerReview.source().name()).isEqualTo("MANAGER");
        assertThat(managerReview.reviewerEmploymentId())
                .isEqualTo(employmentOf(scenario.tenantId(), scenario.managerUserId()));

        // The subject (not the manager) acknowledges the submitted review path:
        // manager submits as reviewer, employee acknowledges as subject.
        service.submitReview(scenario.tenantId(), scenario.managerUserId(), managerReview.id());
        PerformanceReview acknowledged =
                service.acknowledgeReview(scenario.tenantId(), scenario.employeeUserId(), managerReview.id());
        assertThat(acknowledged.status().name()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void unrelatedManagerCannotSeeAnotherManagersReports() {
        UUID tenantId = UUID.randomUUID();
        Scenario scenario = seedReportingScenario(tenantId, null);
        Scenario otherScenario = seedReportingScenario(tenantId, null);
        actAs(tenantId);

        PerformanceReview selfReview = service.createSelfReview(
                scenario.tenantId(), scenario.employeeUserId(),
                input("CYCLE-2026-H1", 4, "other manager's report"));

        assertThat(service.listTeamReviews(scenario.tenantId(), otherScenario.managerUserId())).isEmpty();
        assertThatThrownBy(() -> service.getReviewForActor(
                scenario.tenantId(), otherScenario.managerUserId(), selfReview.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not visible");
    }

    @Test
    void expiredAssignmentDoesNotGrantTeamAccess() {
        UUID tenantId = UUID.randomUUID();
        // Reporting assignment ended yesterday: the manager employment stays
        // ACTIVE but the reporting relationship is no longer effective.
        Scenario expired = seedReportingScenario(tenantId, LocalDate.now().minusDays(1));
        actAs(tenantId);

        PerformanceReview selfReview = service.createSelfReview(
                expired.tenantId(), expired.employeeUserId(),
                input("CYCLE-2026-H1", 4, "report left the team"));

        assertThat(service.listTeamReviews(expired.tenantId(), expired.managerUserId())).isEmpty();
        assertThatThrownBy(() -> service.getReviewForActor(
                expired.tenantId(), expired.managerUserId(), selfReview.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not visible");
    }

    // ==================== foreign tenant ====================

    @Test
    void foreignTenantNeverGrantsAnyAccess() {
        UUID tenantA = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        Scenario scenarioA = seedReportingScenario(tenantA, null);
        Scenario foreignScenario = seedReportingScenario(foreignTenant, null);

        actAs(tenantA);
        PerformanceReview reviewA = service.createSelfReview(
                scenarioA.tenantId(), scenarioA.employeeUserId(),
                input("CYCLE-2026-H1", 4, "tenant A only"));

        // A foreign manager has no employment in tenant A — fail closed.
        assertThatThrownBy(() -> service.getReviewForActor(
                scenarioA.tenantId(), foreignScenario.managerUserId(), reviewA.id()))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> service.listTeamReviews(scenarioA.tenantId(), foreignScenario.managerUserId()))
                .isInstanceOf(AccessDeniedException.class);

        // Manager of A cannot reach the foreign tenant's employment either.
        UUID foreignEmployment = employmentOf(foreignTenant, foreignScenario.employeeUserId());
        assertThatThrownBy(() -> service.createManagerReview(
                scenarioA.tenantId(), scenarioA.managerUserId(), foreignEmployment,
                input("CYCLE-2026-H1", 3, "cross tenant")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }

    // ==================== helpers ====================

    private record Scenario(UUID tenantId, UUID managerUserId, UUID employeeUserId) {
    }

    private void actAs(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
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

    private UUID seedTenantUserWithoutEmployment(UUID tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);
            return seedUser(conn, tenantId, UUID.randomUUID());
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    private UUID seedCanonicalEmployeeGraph(UUID tenantId, String first, String last) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);
            UUID userId = seedUser(conn, tenantId, UUID.randomUUID());
            UUID personId = seedPerson(conn, tenantId, userId, first, last);
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            seedEmployment(conn, tenantId, personId, legalEntityId);
            return userId;
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

    /** Tenant + manager/report graph with a PRIMARY reporting assignment. */
    private Scenario seedReportingScenario(UUID tenantId, LocalDate managerAssignmentEffectiveTo) {
        try (Connection conn = dataSource.getConnection()) {
            CURRENT_TENANT.remove();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID orgId = seedOrganization(conn, tenantId);
            UUID legalEntityId = seedLegalEntity(conn, tenantId);

            UUID managerUserId = seedUser(conn, tenantId, UUID.randomUUID());
            UUID managerPersonId = seedPerson(conn, tenantId, managerUserId, "Scope", "Manager");
            UUID managerEmploymentId = seedEmployment(conn, tenantId, managerPersonId, legalEntityId);
            UUID managerAssignmentId = seedAssignment(
                    conn, tenantId, managerEmploymentId, orgId, null, managerAssignmentEffectiveTo);

            UUID employeeUserId = seedUser(conn, tenantId, UUID.randomUUID());
            UUID employeePersonId = seedPerson(conn, tenantId, employeeUserId, "Scope", "Employee");
            UUID employeeEmploymentId = seedEmployment(conn, tenantId, employeePersonId, legalEntityId);
            seedAssignment(conn, tenantId, employeeEmploymentId, orgId, managerAssignmentId, null);

            return new Scenario(tenantId, managerUserId, employeeUserId);
        } catch (SQLException e) {
            throw new IllegalStateException("fixture failed: " + e.getMessage(), e);
        }
    }

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
                "VALUES (?, 'G3 Scope Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "g3a-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedUser(Connection conn, UUID tenantId, UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, display_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, "g3a-" + userId.toString().substring(0, 8) + "@test.local");
            ps.setString(4, "G3 Scope User");
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
        String code = "G3A-" + legalEntityId.toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities " +
                "(id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "G3 Scope Legal Entity " + code);
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
                "VALUES (?, ?, ?, ?, ?, 'G3', 'Scope', 'G3 Scope Employee', 'FULL_TIME', 'ACTIVE', " +
                "DATE '2026-01-01', 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "G3A-EMP-" + employmentId.toString().substring(0, 8));
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
            ps.setString(3, "G3A Org " + orgId.toString().substring(0, 8));
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
