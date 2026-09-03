package com.sanad.platform.workflow.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowY2CommandService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/** Additive Y2 command API; legacy `/instances/{id}/advance` remains unchanged. */
@RestController
@RequestMapping("/api/v1/workflows/y2")
public class WorkflowY2CommandController {

    private final WorkflowY2CommandService commands;

    public WorkflowY2CommandController(WorkflowY2CommandService commands) {
        this.commands = commands;
    }

    public record AdvanceRequest(String outcome) {}

    @PostMapping("/instances/{id}/advance")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<Map<String, Object>> advance(
            Authentication auth, @PathVariable UUID id, @RequestBody AdvanceRequest request) {
        return ResponseEntity.ok(toInstanceMap(commands.advance(
                tenantId(auth), id, request.outcome(), userId(auth))));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "message", error.getMessage() != null ? error.getMessage() : "Workflow state conflict"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", error.getMessage() != null ? error.getMessage() : "Invalid workflow command"));
    }

    private Map<String, Object> toInstanceMap(WorkflowInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", instance.id());
        map.put("workflowDefinitionId", instance.workflowDefinitionId());
        map.put("workflowVersion", instance.workflowVersion());
        map.put("status", instance.status().name());
        map.put("currentStepKey", instance.currentStepKey() != null ? instance.currentStepKey() : "");
        map.put("version", instance.version());
        map.put("engineGeneration", instance.engineGeneration().name());
        map.put("definitionFamilyId", instance.definitionFamilyId() != null ? instance.definitionFamilyId().toString() : "");
        map.put("definitionVersionId", instance.definitionVersionId() != null ? instance.definitionVersionId().toString() : "");
        return map;
    }
}
