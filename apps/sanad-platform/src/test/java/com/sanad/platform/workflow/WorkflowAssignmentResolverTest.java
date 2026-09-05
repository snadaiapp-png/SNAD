package com.sanad.platform.workflow;

import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.ResolvedAssignment;
import com.sanad.platform.workflow.application.WorkflowAssignmentContext;
import com.sanad.platform.workflow.application.WorkflowAssignmentResolver;
import com.sanad.platform.workflow.application.WorkflowAssignmentResolver.WorkflowAssignmentResolutionException;
import com.sanad.platform.workflow.domain.WorkflowAssignmentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 / Task 8 — assignment resolution and eligibility snapshots (D3/N3).
 *
 * <p>Proves rules resolve to concrete ACTIVE Employee ids, that role and
 * permission rules read the canonical RBAC catalog (not HR names) and never
 * return employees whose linked user is missing or inactive, and that
 * unresolvable rules fail closed.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowAssignmentResolverTest {

    @Autowired
    private WorkflowAssignmentResolver resolver;

    @Autowired
    private HrEmployeeRepository employeeRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID managerEmployeeId;
    private UUID employeeWithoutUser;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Assignment Resolver', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-asg-" + tenantId.toString().substring(0, 8), now, now);

        // G0 fail-closed RLS (V20260905_5): production applies the JWT tenant via
        // TenantRlsConnectionHandler (SET LOCAL per transaction); the fixture mirrors that contract.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        UUID managerUserId = createUser("asg-mgr");
        UUID linkedUserId = createUser("asg-link");

        managerEmployeeId = createEmployee("M-100", managerUserId, null, null, "ACTIVE");
        createEmployee("E-101", linkedUserId, managerEmployeeId, null, "ACTIVE");
        employeeWithoutUser = createEmployee("E-102", null, null, null, "ACTIVE");
        createEmployee("E-103", null, null, null, "SUSPENDED");

        // ADMIN role exists in every tenant; bind WORKFLOW.ADMIN capability to
        // it so the Role and Permission rules have canonical RBAC truth to read.
        UUID adminRoleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at)
                VALUES (?, ?, 'ADMIN', 'Administrator', 'ACTIVE', ?, ?)
                """, adminRoleId, tenantId, now, now);
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT ?, ?, ?, ac.id, NOW() FROM access_capabilities ac WHERE ac.code = 'WORKFLOW.ADMIN'
                """, UUID.randomUUID(), tenantId, adminRoleId);
    }

    @Test
    void employeeRuleResolvesActiveEmployee() {
        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Employee(managerEmployeeId), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(managerEmployeeId);
        assertThat(resolved.resolutionSource()).isEqualTo("Employee");
    }

    @Test
    void managerRuleResolvesSubjectManagerAsEmployee() {
        var subject = jdbc.queryForObject(
                "SELECT id FROM hr_employees WHERE tenant_id = ? AND employee_number LIKE 'E-101%'",
                UUID.class, tenantId);
        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Manager(subject), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(managerEmployeeId);
    }

    @Test
    void departmentRuleResolvesOnlyActiveEmployees() {
        UUID departmentId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO hr_departments (id, tenant_id, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Sales', 'ACTIVE', ?, ?)",
                departmentId, tenantId, now, now);
        UUID member = createEmployee("E-201", null, null, departmentId, "ACTIVE");
        createEmployee("E-202", null, null, departmentId, "SUSPENDED");

        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Department(departmentId), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(member);
    }

    @Test
    void roleRuleResolvesEmployeesThroughLinkedActiveUsers() {
        UUID adminRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'ADMIN'", UUID.class, tenantId);
        UUID managerUserId = jdbc.queryForObject(
                "SELECT user_id FROM hr_employees WHERE id = ?", UUID.class, managerEmployeeId);
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, managerUserId, adminRoleId,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Role("ADMIN"), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(managerEmployeeId);
    }

    @Test
    void permissionRuleResolvesThroughCanonicalCapabilityCatalog() {
        UUID adminRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'ADMIN'", UUID.class, tenantId);
        UUID managerUserId = jdbc.queryForObject(
                "SELECT user_id FROM hr_employees WHERE id = ?", UUID.class, managerEmployeeId);
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, managerUserId, adminRoleId,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Permission("WORKFLOW.ADMIN"), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(managerEmployeeId);
    }

    @Test
    void unassignedRoleResolvesToNoEmployeeAndFailsClosed() {
        // No user in this fixture tenant holds any role assignment yet.
        assertThatThrownBy(() -> resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Role("ADMIN"), WorkflowAssignmentContext.empty()))
                .isInstanceOf(WorkflowAssignmentResolutionException.class);

        // An employee without any linked user can never appear in the
        // candidate set of a role or permission rule (A1/B1 fail-closed).
        assertThat(employeeRepo.findById(tenantId, employeeWithoutUser).orElseThrow().userId())
                .isNull();
    }

    @Test
    void nonActiveEmployeeFailsClosed() {
        var resolved = resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Employee(employeeWithoutUser), WorkflowAssignmentContext.empty());
        assertThat(resolved.employeeIds()).containsExactly(employeeWithoutUser);

        UUID terminated = createEmployee("E-900", null, null, null, "TERMINATED");
        assertThatThrownBy(() -> resolver.resolve(tenantId,
                new WorkflowAssignmentRule.Employee(terminated), WorkflowAssignmentContext.empty()))
                .isInstanceOf(WorkflowAssignmentResolutionException.class);
    }

    @Test
    void crossTenantEmployeeReferenceFailsClosed() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> resolver.resolve(otherTenant,
                new WorkflowAssignmentRule.Employee(managerEmployeeId),
                WorkflowAssignmentContext.empty()))
                .isInstanceOf(WorkflowAssignmentResolutionException.class);
    }

    // ===== fixture helpers =====

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenantId, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Assignment User", now, now);
        return id;
    }

    private UUID createEmployee(String number, UUID linkedUserId, UUID managerId,
                                UUID departmentId, String status) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, department_id, manager_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Assignment', 'Employee', ?, 'FULL_TIME', ?, ?, ?, ?, ?)
                """, id, tenantId, linkedUserId, number + "-" + id.toString().substring(0, 8),
                number + " Employee", status, departmentId, managerId, now, now);
        return id;
    }
}
