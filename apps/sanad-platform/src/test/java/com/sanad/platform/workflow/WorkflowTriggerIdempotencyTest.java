package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.application.WorkflowTriggerService;
import com.sanad.platform.workflow.application.WorkflowTriggerService.TriggerConsumeResult;
import com.sanad.platform.workflow.domain.WorkflowEventEnvelope;
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

/**
 * Wave 2 / Task 15 — reliable triggers and notifications (J3/X3/K3).
 *
 * <p>Proves duplicate domain-event delivery reuses the original instance
 * (exactly one instance per event), notification intents deduplicate by key,
 * and a provider failure records FAILED without touching workflow state.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowTriggerIdempotencyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkflowTriggerService triggerService;

    @Autowired
    private WorkflowNotificationService notifications;

    private UUID tenantId;
    private UUID definitionId;
    private UUID startUserId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        startUserId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Trigger Idempotency', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-trg-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Trigger User', 'ACTIVE', 'dummy', ?, ?)",
                startUserId, tenantId, "wf-trg-" + startUserId.toString().substring(0, 8) + "@test", now, now);
        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-TRG', 'Triggered', 'GENERAL', 1, 'ACTIVE',
                          'EVENT', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, startUserId, now, now);
    }

    @Test
    void duplicateDomainEventStartsOnlyOneInstance() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        var event = new WorkflowEventEnvelope(eventId, "crm.contract.signed", tenantId,
                "CONTRACT", aggregateId, Instant.now(), UUID.randomUUID(), null, 1, "{}");
        String triggerKey = "contract-signed";

        TriggerConsumeResult first = triggerService.consumeDomainEvent(event, triggerKey,
                definitionId, definitionId, 1, "start", startUserId);
        TriggerConsumeResult second = triggerService.consumeDomainEvent(event, triggerKey,
                definitionId, definitionId, 1, "start", startUserId);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.instanceId()).isEqualTo(first.instanceId());

        Integer instanceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND trigger_id = ?",
                Integer.class, tenantId, eventId);
        assertThat(instanceCount).isEqualTo(1);

        Integer inboxRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_event_inbox WHERE tenant_id = ? AND event_id = ?",
                Integer.class, tenantId, eventId);
        assertThat(inboxRows).isEqualTo(1);
    }

    @Test
    void sameEventDifferentTriggerKeyStartsSeparately() {
        UUID eventId = UUID.randomUUID();
        var event = new WorkflowEventEnvelope(eventId, "crm.deal.won", tenantId,
                "DEAL", UUID.randomUUID(), Instant.now(), null, null, 1, "{}");

        TriggerConsumeResult a = triggerService.consumeDomainEvent(event, "trigger-a",
                definitionId, definitionId, 1, "start", startUserId);
        TriggerConsumeResult b = triggerService.consumeDomainEvent(event, "trigger-b",
                definitionId, definitionId, 1, "start", startUserId);

        assertThat(a.duplicate()).isFalse();
        assertThat(b.duplicate()).isFalse();
        assertThat(b.instanceId()).isNotEqualTo(a.instanceId());
    }

    @Test
    void notificationIntentsDeduplicateAndFailuresDoNotTouchWorkflow() {
        UUID instanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, engine_generation,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                          CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, definitionId, startUserId, now, now);

        String dedupKey = "task-assigned-" + UUID.randomUUID();
        UUID first = notifications.enqueue(tenantId, "TASK_ASSIGNED", instanceId, null,
                startUserId, "IN_APP", dedupKey);
        UUID replay = notifications.enqueue(tenantId, "TASK_ASSIGNED", instanceId, null,
                startUserId, "IN_APP", dedupKey);
        assertThat(replay).isEqualTo(first);

        // Failing provider: delivery records FAILED; the workflow row is untouched.
        var failingProvider = new WorkflowNotificationService.DeliveryProvider() {
            @Override public void deliver(UUID t, UUID intentId, String eventType, UUID recipient) {
                throw new IllegalStateException("smtp unavailable");
            }
        };
        boolean delivered = notifications.attemptDelivery(tenantId, first, failingProvider);
        assertThat(delivered).isFalse();
        String deliveryStatus = jdbc.queryForObject(
                "SELECT delivery_status FROM workflow_notification_intents WHERE id = ?",
                String.class, first);
        assertThat(deliveryStatus).isEqualTo("FAILED");

        var workflowRow = jdbc.queryForMap(
                "SELECT status, version FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                tenantId, instanceId);
        assertThat(workflowRow.get("status")).isEqualTo("RUNNING");
        assertThat(((Number) workflowRow.get("version")).longValue()).isZero();
    }
}
