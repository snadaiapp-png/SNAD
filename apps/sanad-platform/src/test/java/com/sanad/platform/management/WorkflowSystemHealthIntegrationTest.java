package com.sanad.platform.management;

import com.sanad.platform.management.application.WorkflowSystemHealthService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowSystemHealthIntegrationTest {

    @Autowired private WorkflowSystemHealthService workflowSystemHealthService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");
        tenantId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)", tenantId, "wsh-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", UUID.randomUUID(), tenantId, "wsh@test", now, now);
    }

    @Test
    void getWorkflowHealth_returnsComponentName() {
        var health = workflowSystemHealthService.getWorkflowHealth(tenantId);
        assertThat(health.get("componentName")).isEqualTo("WORKFLOW_ENGINE");
    }

    @Test
    void getWorkflowHealth_returnsStatusHealthyWhenEmpty() {
        var health = workflowSystemHealthService.getWorkflowHealth(tenantId);
        assertThat(health.get("status")).isEqualTo("HEALTHY");
        assertThat(health.get("activeDefinitions")).isEqualTo(0);
        assertThat(health.get("runningInstances")).isEqualTo(0);
        assertThat(health.get("pendingApprovals")).isEqualTo(0);
        assertThat(health.get("failedInstances")).isEqualTo(0);
    }

    @Test
    void getWorkflowHealth_returnsDegradedWhenFailedInstances() {
        // Insert a failed workflow instance
        var defId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        var userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", userId, tenantId, "wsh2@test", now, now);
        jdbc.update("INSERT INTO workflow_definitions (id,tenant_id,code,name,description,module,version,status,trigger_type,created_by,version_lock,version,created_at,updated_at) "
                + "VALUES (?, ?, 'WF-FAIL', 'Test', null, 'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 0, ?, ?)",
                defId, tenantId, userId, now, now);
        jdbc.update("INSERT INTO workflow_instances (id,tenant_id,workflow_definition_id,workflow_version,business_entity_type,business_entity_id,status,current_step_key,started_by,started_at,version,created_at,updated_at) "
                + "VALUES (?, ?, ?, 1, 'TEST', ?, 'FAILED', null, ?, ?, 0, ?, ?)",
                UUID.randomUUID(), tenantId, defId, UUID.randomUUID(), userId, now, now, now);

        var health = workflowSystemHealthService.getWorkflowHealth(tenantId);
        assertThat(health.get("status")).isEqualTo("DEGRADED");
        assertThat(health.get("failedInstances")).isEqualTo(1);
    }

    @Test
    void getWorkflowHealth_returnsDegradedWhenOverdueApprovals() {
        // Insert an overdue pending approval
        var defId = UUID.randomUUID();
        var instId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        var userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", userId, tenantId, "wsh3@test", now, now);
        jdbc.update("INSERT INTO workflow_definitions (id,tenant_id,code,name,description,module,version,status,trigger_type,created_by,version_lock,version,created_at,updated_at) "
                + "VALUES (?, ?, 'WF-OVERDUE', 'Test', null, 'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 0, ?, ?)",
                defId, tenantId, userId, now, now);
        jdbc.update("INSERT INTO workflow_instances (id,tenant_id,workflow_definition_id,workflow_version,business_entity_type,business_entity_id,status,current_step_key,started_by,started_at,version,created_at,updated_at) "
                + "VALUES (?, ?, ?, 1, 'TEST', ?, 'RUNNING', 'APPROVE', ?, ?, 0, ?, ?)",
                instId, tenantId, defId, UUID.randomUUID(), userId, now, now, now);
        // Insert overdue approval
        jdbc.update("INSERT INTO workflow_approval_requests (id,tenant_id,workflow_instance_id,workflow_step_instance_id,requested_from_user_id,requested_from_role,status,requested_at,due_at,version,created_at,updated_at) "
                + "VALUES (?, ?, ?, NULL, ?, 'APPROVER', 'PENDING', ?, ?, 0, ?, ?)",
                UUID.randomUUID(), tenantId, instId, userId, now,
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)), now, now);

        var health = workflowSystemHealthService.getWorkflowHealth(tenantId);
        assertThat(health.get("status")).isEqualTo("DEGRADED");
        assertThat(health.get("overdueApprovals")).isEqualTo(1);
    }

    @Test
    void getWorkflowHealth_isTenantScoped() {
        var otherTenant = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)", otherTenant, "other-" + otherTenant.toString().substring(0, 8), now, now);

        var health = workflowSystemHealthService.getWorkflowHealth(otherTenant);
        assertThat(health.get("status")).isEqualTo("HEALTHY");
        assertThat(health.get("activeDefinitions")).isEqualTo(0);
    }
}
