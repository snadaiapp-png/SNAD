package com.sanad.platform.workflow;

import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowActionabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowActionabilityServiceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HrEmployeeRepository employees;

    @Autowired
    private WorkflowActionabilityService actionability;

    private UUID tenantId;
    private UUID activeUserId;
    private UUID inactiveUserId;
    private UUID suspendedEmployeeUserId;
    private UUID activeEmployeeId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        activeUserId = UUID.randomUUID();
        inactiveUserId = UUID.randomUUID();
        suspendedEmployeeUserId = UUID.randomUUID();
        activeEmployeeId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "Workflow Actionability Test", "wf-act-" + tenantId.toString().substring(0, 8), now, now);

        // G0 fail-closed RLS (V20260905_5): production applies the JWT tenant via
        // TenantRlsConnectionHandler (SET LOCAL per transaction); the fixture mirrors that contract.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        insertUser(activeUserId, "ACTIVE", "active");
        insertUser(inactiveUserId, "INACTIVE", "inactive");
        insertUser(suspendedEmployeeUserId, "ACTIVE", "suspended-employee");

        insertEmployee(activeEmployeeId, activeUserId, "E-ACTIVE", "ACTIVE");
        insertEmployee(UUID.randomUUID(), inactiveUserId, "E-INACTIVE-USER", "ACTIVE");
        insertEmployee(UUID.randomUUID(), suspendedEmployeeUserId, "E-SUSPENDED", "SUSPENDED");
    }

    @Test
    void employeeRepositoryFindsLinkedEmployeeByTenantAndUser() {
        var employee = employees.findByUserId(tenantId, activeUserId).orElseThrow();

        assertThat(employee.id()).isEqualTo(activeEmployeeId);
        assertThat(employee.userId()).isEqualTo(activeUserId);
    }

    @Test
    void activeLinkedUserAndEmployeeAreActionableAndResolveConcreteEmployeeIdentity() {
        var employee = actionability.requireActionableEmployee(tenantId, activeUserId);

        assertThat(employee.id()).isEqualTo(activeEmployeeId);
    }

    @Test
    void inactiveLinkedUserIsNotActionable() {
        assertThatThrownBy(() -> actionability.requireActionableEmployee(tenantId, inactiveUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not actionable");
    }

    @Test
    void nonActiveEmployeeIsNotActionableEvenWhenLinkedUserIsActive() {
        assertThatThrownBy(() -> actionability.requireActionableEmployee(tenantId, suspendedEmployeeUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Employee is not actionable");
    }

    @Test
    void userWithoutEmployeeLinkIsNotActionable() {
        var unlinkedUserId = UUID.randomUUID();
        insertUser(unlinkedUserId, "ACTIVE", "unlinked");

        assertThatThrownBy(() -> actionability.requireActionableEmployee(tenantId, unlinkedUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not linked to an employee");
    }

    private void insertUser(UUID userId, String status, String prefix) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, ?, ?, 'dummy', ?, ?)",
                userId, tenantId, prefix + "-" + userId.toString().substring(0, 8) + "@test", "Workflow " + prefix, status, now, now);
    }

    private void insertEmployee(UUID employeeId, UUID userId, String employeeNumber, String status) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Workflow', 'Employee', 'Workflow Employee',
                          'FULL_TIME', ?, ?, ?)
                """,
                employeeId, tenantId, userId, employeeNumber, status, now, now);
    }
}
