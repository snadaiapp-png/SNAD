package com.sanad.platform.workflow.notification;

import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanad.platform.security.SecurityContextUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IN_APP notification feed API (GATE R2.6) + notification policy/webhook
 * administration (GATE R2.3/R2.10). Tenant isolation via RLS + capability
 * checks; recipient-identity scoping is derived from the authenticated
 * principal — never from a client parameter.
 */
@RestController
@RequestMapping("/api/v1/workflows/notifications")
public class WorkflowNotificationController {

    private final JdbcTemplate jdbc;
    private final WorkflowNotificationPolicyService policyService;
    private final WorkflowWebhookEndpointService webhookEndpoints;

    public WorkflowNotificationController(JdbcTemplate jdbc,
                                          WorkflowNotificationPolicyService policyService,
                                          WorkflowWebhookEndpointService webhookEndpoints) {
        this.jdbc = jdbc;
        this.policyService = policyService;
        this.webhookEndpoints = webhookEndpoints;
    }

    /** Per-user feed: newest first, bounded page (recipient identity enforced). */
    @GetMapping
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> myNotifications(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50")
            int limit) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        int bounded = Math.max(1, Math.min(limit, 200));
        return ResponseEntity.ok(jdbc.queryForList("""
                SELECT id, event_type, workflow_instance_id, work_item_id,
                       external_action_id, title, body, deep_link, priority,
                       read_at, created_at
                  FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ?
                 ORDER BY created_at DESC
                 LIMIT ?
                """, tenantId, userId, bounded));
    }

    @GetMapping("/unread-count")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        Long unread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, Long.class, tenantId, userId);
        return ResponseEntity.ok(Map.of("unread", unread == null ? 0L : unread));
    }

    /** Marks ONE notification read (identity-checked in the UPDATE predicate). */
    @PostMapping("/{id}/read")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> markRead(
            Authentication authentication, @PathVariable UUID id) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        int updated = jdbc.update("""
                UPDATE workflow_user_notifications
                   SET read_at = NOW()
                 WHERE tenant_id = ? AND id = ? AND recipient_user_id = ?
                   AND read_at IS NULL
                """, tenantId, id, userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/read-all")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        int updated = jdbc.update("""
                UPDATE workflow_user_notifications
                   SET read_at = NOW()
                 WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, tenantId, userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    // ===== administration: policies =====

    @GetMapping("/policies")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<List<Map<String, Object>>> policies(
            Authentication authentication) {
        return ResponseEntity.ok(
                policyService.list(SecurityContextUtils.tenantId(authentication)));
    }

    @PostMapping("/policies")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<WorkflowNotificationPolicyService.NotificationPolicy>
            createPolicy(Authentication authentication,
                         @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        WorkflowNotificationPolicyService.NotificationPolicy policy =
                policyService.create(tenantId,
                        (String) body.get("name"),
                        (String) body.getOrDefault("channel", "IN_APP"),
                        (String) body.get("eventType"),
                        (String) body.get("recipientSource"),
                        (String) body.get("templateKey"),
                        (String) body.get("locale"),
                        (String) body.get("timing"),
                        body.get("offsetSeconds") == null ? null
                                : ((Number) body.get("offsetSeconds")).intValue(),
                        (String) body.get("retryPolicy"),
                        (String) body.get("dedupStrategy"),
                        (String) body.get("escalation"),
                        (String) body.get("fallbackChannel"),
                        (String) body.get("priority"));
        return ResponseEntity.ok(policy);
    }

    // ===== administration: webhook endpoints =====

    @GetMapping("/webhook-endpoints")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<List<Map<String, Object>>> webhookEndpoints(
            Authentication authentication) {
        return ResponseEntity.ok(
                webhookEndpoints.list(SecurityContextUtils.tenantId(authentication)));
    }

    @PostMapping("/webhook-endpoints")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> createWebhookEndpoint(
            Authentication authentication, @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        @SuppressWarnings("unchecked")
        List<String> eventTypes = (List<String>) body.get("eventTypes");
        WorkflowWebhookEndpointService.CreatedEndpoint created =
                webhookEndpoints.create(tenantId,
                        (String) body.get("name"),
                        (String) body.get("url"),
                        (String) body.get("description"),
                        eventTypes, userId);
        // The plain secret is returned EXACTLY ONCE (same token model as the
        // external action portal); only its SHA-256 hash is persisted.
        return ResponseEntity.ok(Map.of(
                "id", created.id().toString(),
                "url", created.url(),
                "secret", created.plainSecret()));
    }

    @PostMapping("/webhook-endpoints/{id}/status")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> setWebhookEndpointStatus(
            Authentication authentication, @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        boolean updated = webhookEndpoints.setStatus(tenantId, id,
                (String) body.get("status"));
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
