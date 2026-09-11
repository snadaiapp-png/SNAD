package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    /**
     * W.3 TEST-HYGIENE — shared seeder owns fixture registration and the
     * deterministic @AfterEach sweep removes every owned fixture from the
     * shared PostgreSQL Direct test database (no manual superuser cleanup).
     */
    private WorkflowNotificationWiringFixtures fixtures;

    private record Recipient(UUID userId, UUID employeeId) {}

    private record Fixture(UUID tenantId, UUID actorUserId, UUID definitionId,
                           String targetStepType, List<Recipient> recipients) {}

    @BeforeEach
    void createFixtures() {
        fixtures = new WorkflowNotificationWiringFixtures(jdbc, transactionManager);
    }

    @AfterEach
    void clearSecurityContextAndSweepFixtures() {
        SecurityContextHolder.clearContext();
        fixtures.sweepCreated();
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
        WorkflowNotificationWiringFixtures.Fixture seeded =
                fixtures.fixture(tag, targetStepType, workPool, recipientCount);
        List<Recipient> recipients = seeded.recipients().stream()
                .map(r -> new Recipient(r.userId(), r.employeeId()))
                .toList();
        return new Fixture(seeded.tenantId(), seeded.actorUserId(), seeded.definitionId(),
                seeded.targetStepType(), recipients);
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
