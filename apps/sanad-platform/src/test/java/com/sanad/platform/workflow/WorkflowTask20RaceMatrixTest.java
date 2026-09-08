package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.api.WorkflowController;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import com.sanad.platform.workflow.domain.WorkflowVersionConflictException;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 4 / Task 20 — executable stale-version race matrix.
 *
 * <p>Each race starts two commands from the same authoritative version and
 * proves exactly one state transition wins. The loser must fail through the
 * platform's 409-equivalent optimistic-concurrency path. Join completion is
 * covered by {@link WorkflowParallelExecutionTest#concurrentJoinGrantsAdvanceExactlyOnce()}.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowTask20RaceMatrixTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowWorkItemService workItemService;
    @Autowired private WorkflowApprovalService approvalService;
    @Autowired private WorkflowIncidentService incidentService;
    @Autowired private WorkflowController controller;

    @Test
    void concurrentPoolClaim_exactlyOneWins() throws Exception {
        Fixture f = fixture("claim");
        WorkflowWorkItem item = WorkflowWorkItem.create(
                f.tenantId(), f.instanceId(), f.stepInstanceId(),
                WorkflowWorkItem.Type.HUMAN_TASK, WorkflowWorkItem.AssignmentMode.WORK_POOL,
                null, "TEST", "CASE", UUID.randomUUID(),
                "Task 20 claim race", "race fixture", 10, null, null);
        item = workItemService.create(item, List.of(
                WorkflowWorkItemCandidate.create(f.tenantId(), item.id(), f.employeeA(), "TASK20"),
                WorkflowWorkItemCandidate.create(f.tenantId(), item.id(), f.employeeB(), "TASK20")));

        long expectedVersion = item.version();
        Race race = race(
                () -> workItemService.claim(f.tenantId(), item.id(), f.employeeA(), expectedVersion),
                () -> workItemService.claim(f.tenantId(), item.id(), f.employeeB(), expectedVersion));

        Throwable loser = assertExactlyOneWinner(race);
        assertThat(loser).isInstanceOf(WorkflowVersionConflictException.class);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, claimed_by_employee_id, version FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                f.tenantId(), item.id());
        assertThat(row.get("status")).isEqualTo("CLAIMED");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
        assertThat(row.get("claimed_by_employee_id")).isIn(f.employeeA(), f.employeeB());
    }

    @Test
    void concurrentApproveAndReject_exactlyOneDecisionWins() throws Exception {
        Fixture f = fixture("approval");
        WorkflowApprovalRequest request = WorkflowApprovalRequest.create(
                f.tenantId(), f.instanceId(), null,
                f.userB(), "APPROVER", Instant.now().plus(1, ChronoUnit.DAYS), f.userA());
        request = approvalService.createApproval(request, f.userA());
        long expectedVersion = request.version();

        Race race = race(
                () -> approvalService.approve(f.tenantId(), request.id(), f.userB(), expectedVersion, "approve race"),
                () -> approvalService.reject(f.tenantId(), request.id(), f.userB(), expectedVersion, "reject race"));

        Throwable loser = assertExactlyOneWinner(race);
        assertThat(loser).isInstanceOf(OptimisticLockingFailureException.class);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, version FROM workflow_approval_requests WHERE tenant_id = ? AND id = ?",
                f.tenantId(), request.id());
        assertThat(row.get("status")).isIn("APPROVED", "REJECTED");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
        Integer decisions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE tenant_id = ? AND workflow_instance_id = ? AND action IN ('APPROVE','REJECT')",
                Integer.class, f.tenantId(), f.instanceId());
        assertThat(decisions).isEqualTo(1);
    }

    @Test
    void concurrentPublish_exactlyOnePublisherWinsAtExpectedVersion() throws Exception {
        Fixture f = fixture("publish");
        UUID definitionId = createPublishableDraft(f);
        WorkflowController target = AopTestUtils.getTargetObject(controller);
        Authentication auth = auth(f.tenantId(), f.userB());

        Race race = race(
                () -> target.publishDefinition(auth, definitionId, new WorkflowController.PublishDefinitionRequest(0)),
                () -> target.publishDefinition(auth, definitionId, new WorkflowController.PublishDefinitionRequest(0)));

        Throwable loser = assertExactlyOneWinner(race);
        assertThat(loser).isInstanceOf(OptimisticLockingFailureException.class);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT publication_state, engine_generation, version_lock FROM workflow_definitions WHERE tenant_id = ? AND id = ?",
                f.tenantId(), definitionId);
        assertThat(row.get("publication_state")).isEqualTo("PUBLISHED");
        assertThat(row.get("engine_generation")).isEqualTo("Y2");
        assertThat(((Number) row.get("version_lock")).longValue()).isEqualTo(1L);
    }

    @Test
    void concurrentReassign_exactlyOneAssigneeWins() throws Exception {
        Fixture f = fixture("reassign");
        WorkflowWorkItem item = WorkflowWorkItem.create(
                f.tenantId(), f.instanceId(), f.stepInstanceId(),
                WorkflowWorkItem.Type.HUMAN_TASK, WorkflowWorkItem.AssignmentMode.DIRECT,
                f.employeeA(), "TEST", "CASE", UUID.randomUUID(),
                "Task 20 reassign race", "race fixture", 10, null, null);
        item = workItemService.create(item, List.of());
        long expectedVersion = item.version();

        Race race = race(
                () -> workItemService.reassign(f.tenantId(), item.id(), f.employeeB(), f.employeeA(), expectedVersion, "operator B"),
                () -> workItemService.reassign(f.tenantId(), item.id(), f.employeeC(), f.employeeA(), expectedVersion, "operator C"));

        Throwable loser = assertExactlyOneWinner(race);
        assertThat(loser).isInstanceOf(WorkflowVersionConflictException.class);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT assignee_employee_id, version FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                f.tenantId(), item.id());
        assertThat(row.get("assignee_employee_id")).isIn(f.employeeB(), f.employeeC());
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    void concurrentIncidentResolve_exactlyOneResolutionWins() throws Exception {
        Fixture f = fixture("incident");
        WorkflowIncident incident = incidentService.open(
                f.tenantId(), f.instanceId(), f.stepInstanceId(),
                "TASK20_RACE", WorkflowIncident.Severity.HIGH, "RACE_TEST");

        Race race = race(
                () -> incidentService.resolve(f.tenantId(), incident.id(), f.userB(), incident.version(), "resolution B"),
                () -> incidentService.resolve(f.tenantId(), incident.id(), f.userC(), incident.version(), "resolution C"));

        Throwable loser = assertExactlyOneWinner(race);
        assertThat(loser).isInstanceOf(OptimisticLockingFailureException.class);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, resolution, version FROM workflow_incidents WHERE tenant_id = ? AND id = ?",
                f.tenantId(), incident.id());
        assertThat(row.get("status")).isEqualTo("RESOLVED");
        assertThat(row.get("resolution")).isIn("resolution B", "resolution C");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    void breakGlassEndpointsRequireDedicatedCapability() throws Exception {
        RequireCapability resume = WorkflowController.class
                .getMethod("breakGlassResume", Authentication.class, UUID.class, WorkflowController.BreakGlassRequest.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability cancel = WorkflowController.class
                .getMethod("breakGlassCancel", Authentication.class, UUID.class, WorkflowController.BreakGlassRequest.class)
                .getAnnotation(RequireCapability.class);

        assertThat(resume).isNotNull();
        assertThat(cancel).isNotNull();
        assertThat(resume.value()).isEqualTo("WORKFLOW.BREAK_GLASS");
        assertThat(cancel.value()).isEqualTo("WORKFLOW.BREAK_GLASS");
    }

    private Fixture fixture(String suffix) {
        UUID tenantId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();
        Instant instant = Instant.now();
        Timestamp now = Timestamp.from(instant);

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "Task20 " + suffix, "wf-t20-" + tenantId.toString().substring(0, 8), now, now);
        createUser(tenantId, userA, suffix + "-a", now);
        createUser(tenantId, userB, suffix + "-b", now);
        createUser(tenantId, userC, suffix + "-c", now);
        UUID employeeA = createEmployee(tenantId, userA, "A", now);
        UUID employeeB = createEmployee(tenantId, userB, "B", now);
        UUID employeeC = createEmployee(tenantId, userC, "C", now);

        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Task20 Parent', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId,
                "WF-T20-" + definitionId.toString().substring(0, 8), userA, now, now);

        UUID stepId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'task', 'Task', 'HUMAN_TASK', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepId, tenantId, definitionId, now, now);

        UUID instanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'task', ?, ?, 0, ?, ?)
                """, instanceId, tenantId, definitionId, userA, now, now, now);

        UUID stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'task', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenantId, instanceId, stepId, now, now);

        return new Fixture(tenantId, userA, userB, userC,
                employeeA, employeeB, employeeC, instanceId, stepInstanceId);
    }

    private UUID createPublishableDraft(Fixture f) {
        UUID definitionId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Task20 Publish Race', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'Y2', 'DRAFT', 1, ?, ?)
                """, definitionId, f.tenantId(), definitionId,
                "WF-PUB-" + definitionId.toString().substring(0, 8), f.userA(), now, now);
        UUID start = UUID.randomUUID();
        UUID end = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'start', 'Start', 'START', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, start, f.tenantId(), definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'end', 'End', 'END', 2, CAST('{}' AS jsonb), 0, ?, ?)
                """, end, f.tenantId(), definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'finish', 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), f.tenantId(), definitionId, start, end, now, now);
        return definitionId;
    }

    private void createUser(UUID tenantId, UUID userId, String prefix, Timestamp now) {
        jdbc.update("""
                INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)
                """, userId, tenantId,
                prefix + "-" + userId.toString().substring(0, 8) + "@test",
                "Task20 User", now, now);
    }

    private UUID createEmployee(UUID tenantId, UUID userId, String number, Timestamp now) {
        UUID employeeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Task20', 'Employee', ?, 'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, userId,
                "T20-" + number + "-" + employeeId.toString().substring(0, 8),
                "Task20 " + number, now, now);
        return employeeId;
    }

    private Authentication auth(UUID tenantId, UUID userId) {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    private Race race(Callable<?> left, Callable<?> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Throwable> a = pool.submit(() -> runRacer(left, ready, start));
        Future<Throwable> b = pool.submit(() -> runRacer(right, ready, start));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Throwable leftFailure = a.get(30, TimeUnit.SECONDS);
        Throwable rightFailure = b.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        return new Race(leftFailure, rightFailure);
    }

    private Throwable runRacer(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            action.call();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Throwable assertExactlyOneWinner(Race race) {
        long failures = List.of(race.leftFailure(), race.rightFailure()).stream()
                .filter(Objects::nonNull).count();
        assertThat(failures).as("exactly one concurrent command must lose").isEqualTo(1);
        return race.leftFailure() != null ? race.leftFailure() : race.rightFailure();
    }

    private record Race(Throwable leftFailure, Throwable rightFailure) {}

    private record Fixture(
            UUID tenantId, UUID userA, UUID userB, UUID userC,
            UUID employeeA, UUID employeeB, UUID employeeC,
            UUID instanceId, UUID stepInstanceId) {}
}
