package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 15 / T15-D2 — executable production-wiring proof for notification
 * intents. These tests drive the real Y2 start + graph transition path; they
 * never call notification enqueue as a substitute for the transition under
 * test.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import({SecurityPermitAllTestConfig.class,
        WorkflowTask15NotificationWiringIntegrationTest.RollbackConfig.class})
class WorkflowTask15NotificationWiringIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private WorkflowExecutionService executionService;
    @Autowired private WorkflowGraphExecutionService graphExecutionService;
    @Autowired private WorkflowNotificationService notificationService;
    @Autowired private RollbackProbe rollbackProbe;

    private record Recipient(UUID userId, UUID employeeId) {}

    private record Fixture(UUID tenantId, UUID actorUserId, UUID definitionId,
                           String targetStepType, List<Recipient> recipients) {}

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void committedDirectHumanTaskTransitionCreatesExactlyOneDurableIntent() {
        Fixture fx = fixture("human-direct", "HUMAN_TASK", false, 1);
        WorkflowInstance started = startWorkflow(fx);

        WorkflowInstance advanced = graphExecutionService.advance(
                fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());

        assertThat(advanced.currentStepKey()).isEqualTo("review");
        List<Map<String, Object>> intents = notificationRows(fx.tenantId(), started.id());
        assertThat(intents).hasSize(1);
        Map<String, Object> intent = intents.get(0);
        UUID workItemId = (UUID) intent.get("work_item_id");
        UUID recipient = fx.recipients().get(0).userId();
        assertThat(intent.get("event_type")).isEqualTo("TASK_ASSIGNED");
        assertThat(intent.get("recipient_user_id")).isEqualTo(recipient);
        assertThat(intent.get("channel")).isEqualTo("IN_APP");
        assertThat(intent.get("delivery_status")).isEqualTo("PENDING");
        assertThat(workItemId).isNotNull();
        assertThat(intent.get("deduplication_key"))
                .isEqualTo("TASK_ASSIGNED:" + workItemId + ":" + recipient);
    }

    @Test
    void committedDirectApprovalTransitionCreatesIntentAndApprovalRequest() {
        Fixture fx = fixture("approval-direct", "APPROVAL", false, 1);
        WorkflowInstance started = startWorkflow(fx);

        WorkflowInstance advanced = graphExecutionService.advance(
                fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());

        assertThat(advanced.currentStepKey()).isEqualTo("review");
        assertThat(notificationRows(fx.tenantId(), started.id())).hasSize(1);
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_approval_requests
                WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenantId(), started.id()))).isEqualTo(1);
    }

    @Test
    void committedPoolHumanTaskNotifiesEveryDistinctCandidateUser() {
        Fixture fx = fixture("human-pool", "HUMAN_TASK", true, 2);
        WorkflowInstance started = startWorkflow(fx);

        graphExecutionService.advance(fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());

        List<Map<String, Object>> intents = notificationRows(fx.tenantId(), started.id());
        assertThat(intents).hasSize(2);
        Set<UUID> recipients = new HashSet<>();
        Set<UUID> workItems = new HashSet<>();
        for (Map<String, Object> intent : intents) {
            recipients.add((UUID) intent.get("recipient_user_id"));
            workItems.add((UUID) intent.get("work_item_id"));
            assertThat(intent.get("channel")).isEqualTo("IN_APP");
        }
        assertThat(recipients).containsExactlyInAnyOrderElementsOf(
                fx.recipients().stream().map(Recipient::userId).toList());
        assertThat(workItems).hasSize(1);
    }

    @Test
    void committedPoolApprovalNotifiesCandidatesAndCreatesRequests() {
        Fixture fx = fixture("approval-pool", "APPROVAL", true, 2);
        WorkflowInstance started = startWorkflow(fx);

        graphExecutionService.advance(fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());

        assertThat(notificationRows(fx.tenantId(), started.id())).hasSize(2);
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_approval_requests
                WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenantId(), started.id()))).isEqualTo(2);
    }

    @Test
    void rolledBackHumanTaskTransitionLeavesNoWorkItemOrNotificationIntent() {
        Fixture fx = fixture("human-rollback", "HUMAN_TASK", false, 1);
        WorkflowInstance started = startWorkflow(fx);

        assertThatThrownBy(() -> rollbackProbe.advanceThenRollback(
                fx.tenantId(), started.id(), fx.actorUserId()))
                .isInstanceOf(ForcedRollbackException.class);

        assertThat(notificationRows(fx.tenantId(), started.id())).isEmpty();
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_work_items
                WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenantId(), started.id()))).isZero();
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT current_step_key FROM workflow_instances
                WHERE tenant_id = ? AND id = ?
                """, String.class, fx.tenantId(), started.id()))).isEqualTo("start");
    }

    @Test
    void rolledBackApprovalTransitionLeavesNoIntentOrApprovalRequest() {
        Fixture fx = fixture("approval-rollback", "APPROVAL", false, 1);
        WorkflowInstance started = startWorkflow(fx);

        assertThatThrownBy(() -> rollbackProbe.advanceThenRollback(
                fx.tenantId(), started.id(), fx.actorUserId()))
                .isInstanceOf(ForcedRollbackException.class);

        assertThat(notificationRows(fx.tenantId(), started.id())).isEmpty();
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_approval_requests
                WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenantId(), started.id()))).isZero();
    }

    @Test
    void providerFailureMarksIntentFailedWithoutRollingBackCommittedWorkflow() {
        Fixture fx = fixture("provider-failure", "HUMAN_TASK", false, 1);
        WorkflowInstance started = startWorkflow(fx);
        graphExecutionService.advance(fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());
        UUID intentId = (UUID) notificationRows(fx.tenantId(), started.id()).get(0).get("id");

        boolean delivered = notificationService.attemptDelivery(
                fx.tenantId(), intentId,
                (tenant, intent, eventType, recipient) -> {
                    throw new IllegalStateException("provider unavailable");
                });

        assertThat(delivered).isFalse();
        Map<String, Object> workflow = tenantTx(fx.tenantId(), () -> jdbc.queryForMap("""
                SELECT status, current_step_key FROM workflow_instances
                WHERE tenant_id = ? AND id = ?
                """, fx.tenantId(), started.id()));
        assertThat(workflow.get("status")).isEqualTo("RUNNING");
        assertThat(workflow.get("current_step_key")).isEqualTo("review");
        assertThat(tenantTx(fx.tenantId(), () -> jdbc.queryForObject("""
                SELECT delivery_status FROM workflow_notification_intents
                WHERE tenant_id = ? AND id = ?
                """, String.class, fx.tenantId(), intentId))).isEqualTo("FAILED");
    }

    @Test
    void runtimeIntentReplayReusesTheSameDurableIntent() {
        Fixture fx = fixture("runtime-replay", "HUMAN_TASK", false, 1);
        WorkflowInstance started = startWorkflow(fx);
        graphExecutionService.advance(fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());
        Map<String, Object> intent = notificationRows(fx.tenantId(), started.id()).get(0);
        UUID intentId = (UUID) intent.get("id");
        UUID workItemId = (UUID) intent.get("work_item_id");
        UUID recipient = (UUID) intent.get("recipient_user_id");
        String key = (String) intent.get("deduplication_key");

        UUID replay = notificationService.enqueue(
                fx.tenantId(), "TASK_ASSIGNED", started.id(), workItemId,
                recipient, "IN_APP", key);

        assertThat(replay).isEqualTo(intentId);
        assertThat(notificationRows(fx.tenantId(), started.id())).hasSize(1);
    }

    private WorkflowInstance startWorkflow(Fixture fx) {
        authenticate(fx.tenantId(), fx.actorUserId());
        WorkflowInstance instance = WorkflowInstance.startY2(
                fx.tenantId(), fx.definitionId(), fx.definitionId(), 1,
                "TEST", UUID.randomUUID(), "start", fx.actorUserId(), UUID.randomUUID(),
                "MANUAL", null, null, null, null);
        return executionService.startWorkflow(instance, fx.actorUserId());
    }

    private Fixture fixture(String tag, String targetStepType, boolean workPool, int recipientCount) {
        UUID tenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        List<Recipient> recipients = new ArrayList<>();
        for (int i = 0; i < recipientCount; i++) {
            recipients.add(new Recipient(UUID.randomUUID(), UUID.randomUUID()));
        }

        tenantTx(tenantId, () -> {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Task15 " + tag,
                    "t15-" + tag + "-" + tenantId.toString().substring(0, 8), now, now);
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                            + "VALUES (?, ?, ?, 'Task15 Actor', 'ACTIVE', 'dummy', ?, ?)",
                    actorUserId, tenantId,
                    "t15-actor-" + actorUserId.toString().substring(0, 8) + "@test", now, now);

            int index = 0;
            for (Recipient recipient : recipients) {
                jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                                + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                        recipient.userId(), tenantId,
                        "t15-recipient-" + recipient.userId().toString().substring(0, 8) + "@test",
                        "Task15 Recipient " + index, now, now);
                jdbc.update("""
                        INSERT INTO hr_employees (
                            id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                            employment_type, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'Task15', 'Recipient', ?,
                                  'FULL_TIME', 'ACTIVE', ?, ?)
                        """, recipient.employeeId(), tenantId, recipient.userId(),
                        "T15-" + tag + "-" + index, "Task15 Recipient " + index, now, now);
                if (workPool) {
                    grantCapability(tenantId, recipient.userId(), "WORKFLOW.TASK_EXECUTE");
                }
                index++;
            }

            jdbc.update("""
                    INSERT INTO workflow_definitions (
                        id, tenant_id, definition_family_id, code, name, module, version, status,
                        trigger_type, created_by, version_lock, engine_generation, publication_state,
                        schema_version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'GENERAL', 1, 'ACTIVE',
                              'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                    """, definitionId, tenantId, definitionId,
                    "WF15-" + tag.toUpperCase(), "Task15 " + tag, actorUserId, now, now);

            UUID startStep = createStep(tenantId, definitionId, "start", "START", 1,
                    "{}", null);
            String targetConfig = workPool
                    ? "{}"
                    : "{\"assigneeEmployeeId\":\"" + recipients.get(0).employeeId() + "\"}";
            UUID targetStep = createStep(tenantId, definitionId, "review", targetStepType, 2,
                    targetConfig, workPool ? "WORKFLOW.TASK_EXECUTE" : null);
            UUID endStep = createStep(tenantId, definitionId, "end", "END", 3,
                    "{}", null);
            createTransition(tenantId, definitionId, startStep, targetStep, "begin");
            createTransition(tenantId, definitionId, targetStep, endStep, "done");
            return null;
        });

        return new Fixture(tenantId, actorUserId, definitionId, targetStepType, List.copyOf(recipients));
    }

    private UUID createStep(UUID tenantId, UUID definitionId, String key, String type,
                            int sequence, String configuration, String requiredCapability) {
        UUID stepId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, required_capability, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 0, ?, ?)
                """, stepId, tenantId, definitionId, key, key, type, sequence,
                configuration, requiredCapability, now, now);
        return stepId;
    }

    private void createTransition(UUID tenantId, UUID definitionId, UUID fromStep,
                                  UUID toStep, String key) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, definitionId, fromStep, toStep, key, now, now);
    }

    /**
     * Idempotent RBAC fixture: one ADMIN role per tenant can accumulate the
     * capability and be assigned to multiple candidate users.
     */
    private void grantCapability(UUID tenantId, UUID userId, String capabilityCode) {
        List<UUID> roles = jdbc.queryForList(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'ADMIN'",
                UUID.class, tenantId);
        UUID roleId;
        if (roles.isEmpty()) {
            roleId = UUID.randomUUID();
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                            + "VALUES (?, ?, 'ADMIN', 'Administrator', 'ACTIVE', ?, ?)",
                    roleId, tenantId, now, now);
        } else {
            roleId = roles.get(0);
        }

        Integer roleCapabilityCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_capabilities rc
                JOIN access_capabilities ac ON ac.id = rc.capability_id
                WHERE rc.tenant_id = ? AND rc.role_id = ? AND ac.code = ?
                """, Integer.class, tenantId, roleId, capabilityCode);
        if (roleCapabilityCount != null && roleCapabilityCount == 0) {
            jdbc.update("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT ?, ?, ?, id, NOW() FROM access_capabilities WHERE code = ?
                    """, UUID.randomUUID(), tenantId, roleId, capabilityCode);
        }

        Integer assignmentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role_assignments
                WHERE tenant_id = ? AND user_id = ? AND role_id = ? AND status = 'ACTIVE'
                """, Integer.class, tenantId, userId, roleId);
        if (assignmentCount != null && assignmentCount == 0) {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("""
                    INSERT INTO user_role_assignments (
                        id, tenant_id, user_id, role_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, UUID.randomUUID(), tenantId, userId, roleId, now, now);
        }
    }

    private List<Map<String, Object>> notificationRows(UUID tenantId, UUID instanceId) {
        return tenantTx(tenantId, () -> jdbc.queryForList("""
                SELECT id, event_type, workflow_instance_id, work_item_id,
                       recipient_user_id, channel, deduplication_key, delivery_status
                FROM workflow_notification_intents
                WHERE tenant_id = ? AND workflow_instance_id = ?
                ORDER BY recipient_user_id
                """, tenantId, instanceId));
    }

    private void authenticate(UUID tenantId, UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private <T> T tenantTx(UUID tenantId, Supplier<T> supplier) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            return supplier.get();
        });
    }

    @TestConfiguration
    static class RollbackConfig {
        @Bean
        RollbackProbe rollbackProbe(WorkflowGraphExecutionService graphExecutionService) {
            return new RollbackProbe(graphExecutionService);
        }
    }

    static class RollbackProbe {
        private final WorkflowGraphExecutionService graphExecutionService;

        RollbackProbe(WorkflowGraphExecutionService graphExecutionService) {
            this.graphExecutionService = graphExecutionService;
        }

        @Transactional
        public void advanceThenRollback(UUID tenantId, UUID instanceId, UUID actorUserId) {
            graphExecutionService.advance(tenantId, instanceId, "SUCCESS", actorUserId);
            throw new ForcedRollbackException();
        }
    }

    static class ForcedRollbackException extends RuntimeException {
        ForcedRollbackException() {
            super("forced Task 15 rollback probe");
        }
    }
}
