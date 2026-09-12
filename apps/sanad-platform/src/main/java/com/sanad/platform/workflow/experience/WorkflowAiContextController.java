package com.sanad.platform.workflow.experience;

import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI context readiness surface (GATE R2.21 / AD-14). Produces bounded,
 * tenant-scoped, authorization-scoped, source-attributable context
 * descriptors (workflow context, journey summary, task context, approval
 * context, analytics explanation). HARD SIZE CAPS everywhere.
 *
 * <p>LIVE_AUTONOMOUS_AI=OFF: this endpoint never executes an agent, never
 * auto-approves, auto-rejects, auto-assigns, or auto-sends. It is a pure
 * read-only context projection over Journey-backed evidence.</p>
 */
@RestController
@RequestMapping("/api/v1/workflows/ai-context")
public class WorkflowAiContextController {

    private static final int MAX_JOURNEY_EVENTS = 50;
    private static final int MAX_TASKS = 25;
    private static final int MAX_STRING_LENGTH = 300;

    private final JdbcTemplate jdbc;

    public WorkflowAiContextController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/instance/{instanceId}")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> instanceContext(
            Authentication authentication, @PathVariable UUID instanceId) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        List<Map<String, Object>> instances = jdbc.queryForList("""
                SELECT i.id, i.status, i.current_step_key, i.created_at, i.updated_at,
                       i.definition_family_id, i.definition_version_id, d.name AS definition_name,
                       d.version AS definition_version, i.source_module,
                       i.source_entity_type, i.source_entity_id
                  FROM workflow_instances i
                  LEFT JOIN workflow_definitions d
                    ON d.tenant_id = i.tenant_id AND d.id = i.definition_version_id
                 WHERE i.tenant_id = ? AND i.id = ?
                """, tenantId, instanceId);
        if (instances.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> instance = capMap(instances.get(0));
        List<Map<String, Object>> journey = jdbc.queryForList("""
                SELECT event_type, actor_type, from_state, to_state, occurred_at,
                       event_key
                  FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                 ORDER BY id
                 LIMIT ?
                """, tenantId, instanceId, MAX_JOURNEY_EVENTS);
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT id, type, status, title, due_at, completed_at
                  FROM workflow_work_items
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                 ORDER BY created_at
                 LIMIT ?
                """, tenantId, instanceId, MAX_TASKS);
        List<Map<String, Object>> approvals = jdbc.queryForList("""
                SELECT id, status, decision, decided_at
                  FROM workflow_approval_requests
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                 ORDER BY created_at
                 LIMIT ?
                """, tenantId, instanceId, MAX_TASKS);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("descriptor", "WORKFLOW_INSTANCE_CONTEXT");
        context.put("sourceAttribution", Map.of(
                "authority", "workflow_journey (append-only evidence ledger)",
                "instanceId", instanceId.toString(),
                "tenantScoped", true));
        context.put("instance", instance);
        context.put("journeySummary", journey.stream().map(this::capMap).toList());
        context.put("taskContext", tasks.stream().map(this::capMap).toList());
        context.put("approvalContext", approvals.stream().map(this::capMap).toList());
        context.put("bounds", Map.of(
                "maxJourneyEvents", MAX_JOURNEY_EVENTS,
                "maxTasks", MAX_TASKS,
                "maxApprovalRequests", MAX_TASKS,
                "maxStringLength", MAX_STRING_LENGTH));
        context.put("autonomousExecution", "OFF");
        return ResponseEntity.ok(context);
    }

    /** Caps every value: strings truncated, no free-text growth. */
    private Map<String, Object> capMap(Map<String, Object> row) {
        Map<String, Object> capped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                value = s.length() <= MAX_STRING_LENGTH
                        ? s : s.substring(0, MAX_STRING_LENGTH);
            }
            capped.put(entry.getKey(), value);
        }
        return capped;
    }
}
