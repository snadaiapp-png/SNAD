package com.sanad.platform.workflow.experience;

import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowJourneyService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer feedback foundation (GATE R2.18 / AD-12). Feedback is
 * tenant-scoped evidence linked to the process/source entity; submissions
 * by authenticated users are capability-gated; portal (token) submissions
 * land through the external portal path. No public enumeration; no fake
 * customer records; feedback is input only — no autonomous business
 * decision consumes it.
 */
@RestController
@RequestMapping("/api/v1/workflows/feedback")
public class WorkflowCustomerFeedbackController {

    private final JdbcTemplate jdbc;
    private final WorkflowJourneyService journeyService;

    public WorkflowCustomerFeedbackController(JdbcTemplate jdbc,
                                              WorkflowJourneyService journeyService) {
        this.jdbc = jdbc;
        this.journeyService = journeyService;
    }

    /** Records feedback authored by an authenticated tenant user. */
    @PostMapping
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> record(
            Authentication authentication, @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID id = UUID.randomUUID();
        UUID instanceId = parseUuid(body.get("workflowInstanceId"));
        Integer rating = body.get("rating") == null ? null
                : ((Number) body.get("rating")).intValue();
        if (rating == null || rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "RATING_REQUIRED_1_TO_5"));
        }
        String comment = body.get("comment") == null ? null
                : String.valueOf(body.get("comment"));
        jdbc.update("""
                INSERT INTO workflow_customer_feedback (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    external_action_id, source_module, source_entity_type,
                    source_entity_id, participant_id, rating, comment,
                    submitted_by_type, submitted_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USER', ?, NOW())
                """, id, tenantId, instanceId,
                parseUuid(body.get("workflowStepInstanceId")),
                parseUuid(body.get("externalActionId")),
                (String) body.get("sourceModule"),
                (String) body.get("sourceEntityType"),
                parseUuid(body.get("sourceEntityId")),
                parseUuid(body.get("participantId")),
                rating, comment, userId);
        if (instanceId != null) {
            journeyService.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                    "EXTERNAL_RESPONSE", instanceId, null, null,
                    parseUuid(body.get("externalActionId")), null, null, null, null,
                    null, null, null, "USER", userId, null, null, null, "FEEDBACK",
                    "Customer feedback recorded (" + rating + "/5)",
                    id, id, "feedback:" + id,
                    Map.of("rating", rating)));
        }
        return ResponseEntity.ok(Map.of("id", id.toString(), "rating", rating));
    }

    /** Tenant-scoped listing (bounded page; no cross-tenant read). */
    @GetMapping
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> list(
            Authentication authentication,
            @RequestParam(required = false) UUID instanceId,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        int bounded = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows = instanceId == null
                ? jdbc.queryForList("""
                        SELECT id, workflow_instance_id, source_entity_type,
                               source_entity_id, rating, comment, submitted_by_type,
                               created_at
                          FROM workflow_customer_feedback
                         WHERE tenant_id = ?
                         ORDER BY created_at DESC LIMIT ?
                        """, tenantId, bounded)
                : jdbc.queryForList("""
                        SELECT id, workflow_instance_id, source_entity_type,
                               source_entity_id, rating, comment, submitted_by_type,
                               created_at
                          FROM workflow_customer_feedback
                         WHERE tenant_id = ? AND workflow_instance_id = ?
                         ORDER BY created_at DESC LIMIT ?
                        """, tenantId, instanceId, bounded);
        return ResponseEntity.ok(rows);
    }

    private static UUID parseUuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID reference: " + value);
        }
    }
}
