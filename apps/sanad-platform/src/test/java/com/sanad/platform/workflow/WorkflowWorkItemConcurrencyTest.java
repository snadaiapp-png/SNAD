package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import com.sanad.platform.workflow.domain.WorkflowVersionConflictException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 / Task 7 — central WorkItems and atomic work pools (C3/L3/N3).
 *
 * <p>Proves the claim invariant under concurrency semantics: only one
 * candidate can claim the same item version, non-candidates and cross-tenant
 * actors fail closed, releases return the item to the pool with a new
 * version, and only the current claimant can complete.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowWorkItemConcurrencyTest {

    @Autowired
    private WorkflowWorkItemService workItemService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID instanceId;
    private UUID stepInstanceId;
    private UUID definitionId;
    private UUID employeeA;
    private UUID employeeB;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'WorkItem Concurrency', ?, 'ACTIVE', ?, ?)",
                tenantId, "wi-conc-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Concurrency User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wi-conc-" + userId.toString().substring(0, 8) + "@test", now, now);
        employeeA = createEmployee("E-A");
        employeeB = createEmployee("E-B");

        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-CONC', 'Concurrency Fixture', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId = UUID.randomUUID(), tenantId, definitionId, userId, now, now, now);
        UUID stepDefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'review', 'Review', 'HUMAN_TASK', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepDefId, tenantId, definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'step', 'PENDING', 0, ?, ?)
                """, stepInstanceId = UUID.randomUUID(), tenantId, instanceId, stepDefId, now, now);
    }

    @Test
    void onlyOneCandidateCanClaimTheSameVersion() {
        var item = createPoolItem();

        var first = workItemService.claim(tenantId, item.id(), employeeA, item.version());
        assertThat(first.claimedByEmployeeId()).isEqualTo(employeeA);
        assertThat(first.status()).isEqualTo(WorkflowWorkItem.Status.CLAIMED);

        assertThatThrownBy(() -> workItemService.claim(tenantId, item.id(), employeeB, item.version()))
                .isInstanceOf(WorkflowVersionConflictException.class);
    }

    @Test
    void nonCandidateCannotClaimPoolItem() {
        var item = createPoolItem();
        var outsider = createEmployee("E-OUT");

        assertThatThrownBy(() -> workItemService.claim(tenantId, item.id(), outsider, item.version()))
                .isInstanceOf(WorkflowVersionConflictException.class);
    }

    @Test
    void staleVersionCannotClaim() {
        var item = createPoolItem();
        workItemService.claim(tenantId, item.id(), employeeA, item.version());
        workItemService.release(tenantId, item.id(), employeeA, item.version() + 1);

        // item.version() was 0; the live item is now at version 2
        assertThatThrownBy(() -> workItemService.claim(tenantId, item.id(), employeeB, 0))
                .isInstanceOf(WorkflowVersionConflictException.class);

        var claimed = workItemService.claim(tenantId, item.id(), employeeB, 2);
        assertThat(claimed.claimedByEmployeeId()).isEqualTo(employeeB);
    }

    @Test
    void onlyCurrentClaimantCanComplete() {
        var item = createPoolItem();
        workItemService.claim(tenantId, item.id(), employeeA, item.version());

        assertThatThrownBy(() -> workItemService.complete(tenantId, item.id(), employeeB, item.version() + 1))
                .isInstanceOf(WorkflowVersionConflictException.class);

        var completed = workItemService.complete(tenantId, item.id(), employeeA, item.version() + 1);
        assertThat(completed.status()).isEqualTo(WorkflowWorkItem.Status.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void crossTenantClaimFailsClosed() {
        var item = createPoolItem();
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> workItemService.claim(otherTenant, item.id(), employeeA, item.version()))
                .isInstanceOf(WorkflowVersionConflictException.class);
    }

    @Test
    void myWorkAndPoolQueriesRespectTenantAndAssignment() {
        var item = createPoolItem();
        workItemService.claim(tenantId, item.id(), employeeA, item.version());

        var mine = workItemService.findMyWork(tenantId, employeeA, 50);
        assertThat(mine).extracting(WorkflowWorkItem::id).contains(item.id());
        var otherPool = workItemService.findPoolWork(tenantId, employeeA, 50);
        assertThat(otherPool).extracting(WorkflowWorkItem::id).doesNotContain(item.id());

        UUID otherTenant = UUID.randomUUID();
        assertThat(workItemService.findMyWork(otherTenant, employeeA, 50)).isEmpty();
    }

    // ===== fixture helpers =====

    private WorkflowWorkItem createPoolItem() {
        var item = WorkflowWorkItem.create(tenantId, instanceId, stepInstanceId,
                WorkflowWorkItem.Type.HUMAN_TASK, WorkflowWorkItem.AssignmentMode.WORK_POOL,
                null, "TEST", "CASE", UUID.randomUUID(),
                "Review contract", "fixture", 5, null, null);
        List<WorkflowWorkItemCandidate> candidates = List.of(
                WorkflowWorkItemCandidate.create(tenantId, item.id(), employeeA, "TEST_RULE"),
                WorkflowWorkItemCandidate.create(tenantId, item.id(), employeeB, "TEST_RULE"));
        return workItemService.create(item, candidates);
    }

    private UUID createEmployee(String number) {
        UUID employeeId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'Concurrency', 'Employee', 'Concurrency Employee',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, number + "-" + employeeId.toString().substring(0, 8), now, now);
        return employeeId;
    }
}
