package com.sanad.platform.workflow.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * IN_APP channel provider (GATE R2.6). Delivery = materializing a row in
 * the per-user notification feed ({@code workflow_user_notifications}).
 * Tenant isolation comes from RLS; recipient identity is carried on the
 * row and re-verified by the read API (service-level, derived from the
 * authenticated principal). Idempotent via the feed dedup key: repeated
 * worker execution never duplicates a user-visible notification.
 */
@Component
public class InAppNotificationProvider implements WorkflowChannelProvider {

    private final JdbcTemplate jdbc;

    public InAppNotificationProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WorkflowChannel channel() {
        return WorkflowChannel.IN_APP;
    }

    @Override
    public String providerType() {
        return "workflow-inapp";
    }

    @Override
    @Transactional
    public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
        if (request.recipientUserId() == null) {
            // IN_APP without a user recipient cannot be materialized.
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        String dedupKey = request.deduplicationKey() != null
                ? request.deduplicationKey()
                : "intent:" + request.intentId();
        String title = request.title() != null ? request.title() : request.eventType();
        jdbc.update("""
                INSERT INTO workflow_user_notifications (
                    id, tenant_id, recipient_user_id, event_type, workflow_instance_id,
                    work_item_id, external_action_id, title, body, deep_link,
                    priority, source_ref, dedup_key, intent_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, dedup_key) DO NOTHING
                """,
                UUID.randomUUID(), request.tenantId(), request.recipientUserId(),
                request.eventType(), request.workflowInstanceId(), request.workItemId(),
                request.externalActionId(), trim(title, 300), trim(request.body(), 2000),
                trim(request.deepLink(), 500), request.priority() == null
                        ? "NORMAL" : request.priority(),
                "intent:" + request.intentId(), dedupKey, request.intentId());
        return WorkflowDeliveryResult.success("inapp:" + request.intentId());
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
