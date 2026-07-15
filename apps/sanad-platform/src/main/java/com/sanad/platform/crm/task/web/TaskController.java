package com.sanad.platform.crm.task.web;

import com.sanad.platform.crm.task.application.TaskUseCases;
import com.sanad.platform.crm.task.domain.TaskRepository.CreateTaskCommand;
import com.sanad.platform.crm.task.domain.TaskRepository.TaskRecord;
import com.sanad.platform.crm.task.domain.TaskRepository.UpdateTaskCommand;
import com.sanad.platform.crm.task.web.TaskModels.CancelTaskRequest;
import com.sanad.platform.crm.task.web.TaskModels.CompleteTaskRequest;
import com.sanad.platform.crm.task.web.TaskModels.CreateTaskRequest;
import com.sanad.platform.crm.task.web.TaskModels.UpdateTaskRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Tasks.
 * <p>
 * Mounted under {@code /api/v1/crm/tasks} to match the V1 contract used by
 * the frontend {@code lib/api/crm.ts}. Returns plain {@code Map<String,Object>}
 * with snake_case keys (consistent with other V1 endpoints).
 * <p>
 * Capabilities enforced via {@link RequireCapability}:
 *   - {@code CRM.TASK.READ} for GET endpoints
 *   - {@code CRM.TASK.WRITE} for POST/PATCH endpoints
 * <p>
 * Branch: feature/crm-tasks
 */
@RestController
@RequestMapping("/api/v1/crm/tasks")
public class TaskController {

    private final TaskUseCases tasks;

    public TaskController(TaskUseCases tasks) {
        this.tasks = tasks;
    }

    @RequireCapability("CRM.TASK.READ")
    @GetMapping
    public List<Map<String, Object>> listTasks(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID relatedId) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<TaskRecord> rows = tasks.list(tenantId, safeLimit, status, assigneeId, relatedId);
        return rows.stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.TASK.READ")
    @GetMapping("/{taskId}")
    public Map<String, Object> getTask(Authentication authentication, @PathVariable UUID taskId) {
        return toRow(tasks.getById(tenantId(authentication), taskId));
    }

    @RequireCapability("CRM.TASK.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(
            Authentication authentication,
            @Valid @RequestBody CreateTaskRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        CreateTaskCommand cmd = new CreateTaskCommand(
                request.title(),
                request.description(),
                request.relatedType(),
                request.relatedId(),
                request.assigneeUserId(),
                request.ownerUserId() != null ? request.ownerUserId() : actorId,
                request.priority(),
                request.startAt(),
                request.dueAt());

        TaskRecord created = tasks.create(tenantId, actorId, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.TASK.WRITE")
    @PatchMapping("/{taskId}")
    public Map<String, Object> updateTask(
            Authentication authentication,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TaskRecord current = tasks.getById(tenantId, taskId);

        UpdateTaskCommand cmd = new UpdateTaskCommand(
                request.title(),
                request.description(),
                request.assigneeUserId(),
                request.priority(),
                request.startAt(),
                request.dueAt());

        return toRow(tasks.update(tenantId, actorId, taskId, cmd, current.version()));
    }

    @RequireCapability("CRM.TASK.WRITE")
    @PatchMapping("/{taskId}/start")
    public Map<String, Object> startTask(Authentication authentication, @PathVariable UUID taskId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TaskRecord current = tasks.getById(tenantId, taskId);
        return toRow(tasks.start(tenantId, actorId, taskId, current.version()));
    }

    @RequireCapability("CRM.TASK.WRITE")
    @PatchMapping("/{taskId}/complete")
    public Map<String, Object> completeTask(
            Authentication authentication,
            @PathVariable UUID taskId,
            @RequestBody(required = false) CompleteTaskRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TaskRecord current = tasks.getById(tenantId, taskId);
        String result = request == null ? null : request.result();
        return toRow(tasks.complete(tenantId, actorId, taskId, result, current.version()));
    }

    @RequireCapability("CRM.TASK.WRITE")
    @PatchMapping("/{taskId}/cancel")
    public Map<String, Object> cancelTask(
            Authentication authentication,
            @PathVariable UUID taskId,
            @RequestBody(required = false) CancelTaskRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TaskRecord current = tasks.getById(tenantId, taskId);
        String reason = request == null ? null : request.reason();
        return toRow(tasks.cancel(tenantId, actorId, taskId, reason, current.version()));
    }

    private Map<String, Object> toRow(TaskRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("title", r.title());
        row.put("description", r.description());
        row.put("related_type", r.relatedType());
        row.put("related_id", r.relatedId());
        row.put("assignee_user_id", r.assigneeUserId());
        row.put("owner_user_id", r.ownerUserId());
        row.put("status", r.status());
        row.put("priority", r.priority());
        row.put("start_at", toIso(r.startAt()));
        row.put("due_at", toIso(r.dueAt()));
        row.put("completed_at", toIso(r.completedAt()));
        row.put("result", r.result());
        row.put("created_at", toIso(r.createdAt()));
        row.put("updated_at", toIso(r.updatedAt()));
        return row;
    }

    private static String toIso(OffsetDateTime v) {
        return v == null ? null : v.toInstant().toString();
    }

    private static String toIso(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
