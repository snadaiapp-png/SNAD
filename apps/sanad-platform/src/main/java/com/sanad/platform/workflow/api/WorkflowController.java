package com.sanad.platform.workflow.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowMonitoringService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Workflow Engine REST API — definitions, instances, approvals, monitoring.
 *
 * <p>All endpoints are tenant-scoped via {@link SecurityContextUtils#tenantId(Authentication)}
 * and require a {@link RequireCapability WORKFLOW.*} capability.
 *
 * <p>Base path: {@code /api/v1/workflows}
 *
 * <p>Responses use {@link Map}&lt;String,Object&gt; for simplicity, following
 * the same convention as {@code ManagementDecisionController}.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowExecutionService executionService;
    private final WorkflowApprovalService approvalService;
    private final WorkflowMonitoringService monitoringService;

    public WorkflowController(
            WorkflowDefinitionService definitionService,
            WorkflowExecutionService executionService,
            WorkflowApprovalService approvalService,
            WorkflowMonitoringService monitoringService) {
        this.definitionService = definitionService;
        this.executionService = executionService;
        this.approvalService = approvalService;
        this.monitoringService = monitoringService;
    }

    // ===== Definitions =====

    @PostMapping("/definitions")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> createDefinition(
            Authentication auth, @RequestBody CreateDefinitionRequest req) {
        var def = WorkflowDefinition.create(
                tenantId(auth), req.code(), req.name(), req.description(),
                req.module(),
                req.triggerType() != null
                        ? WorkflowDefinition.TriggerType.valueOf(req.triggerType())
                        : WorkflowDefinition.TriggerType.MANUAL,
                userId(auth)
        );
        var saved = definitionService.create(def, userId(auth));
        return ResponseEntity.ok(toDefinitionMap(saved));
    }

    @GetMapping("/definitions")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listDefinitions(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var defs = definitionService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(defs.stream().map(this::toDefinitionMap).toList());
    }

    @GetMapping("/definitions/{id}")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> getDefinition(
            Authentication auth, @PathVariable UUID id) {
        return definitionService.findById(tenantId(auth), id)
                .map(d -> ResponseEntity.ok(toDefinitionMap(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/definitions/{id}/activate")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> activateDefinition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDefinitionMap(
                definitionService.activate(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/definitions/{id}/deactivate")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> deactivateDefinition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDefinitionMap(
                definitionService.deactivate(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/definitions/{id}/archive")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> archiveDefinition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDefinitionMap(
                definitionService.archive(tenantId(auth), id, userId(auth))));
    }

    // ===== Instances =====

    @PostMapping("/instances")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> startWorkflow(
            Authentication auth, @RequestBody StartWorkflowRequest req) {
        var tenant = tenantId(auth);
        var actor = userId(auth);
        // Resolve the workflow definition and its first step.
        var def = definitionService.findById(tenant, req.workflowDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowDefinition not found: " + req.workflowDefinitionId()));
        if (def.status() != WorkflowDefinition.Status.ACTIVE) {
            throw new IllegalStateException(
                    "WorkflowDefinition " + def.id() + " is not ACTIVE (status=" + def.status() + ")");
        }
        var steps = definitionService.findSteps(def.id());
        if (steps.isEmpty()) {
            throw new IllegalStateException(
                    "WorkflowDefinition " + def.id() + " has no steps");
        }
        var firstStep = steps.stream()
                .min(Comparator.comparingInt(WorkflowStep::sequenceOrder))
                .orElseThrow();
        var instance = WorkflowInstance.start(
                tenant, def.id(), def.version(),
                req.businessEntityType(), req.businessEntityId(),
                firstStep.stepKey(), actor, req.correlationId()
        );
        var saved = executionService.startWorkflow(instance, actor);
        return ResponseEntity.ok(toInstanceMap(saved));
    }

    @GetMapping("/instances")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listInstances(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var instances = executionService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(instances.stream().map(this::toInstanceMap).toList());
    }

    @GetMapping("/instances/{id}")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> getInstance(
            Authentication auth, @PathVariable UUID id) {
        return executionService.findById(tenantId(auth), id)
                .map(i -> ResponseEntity.ok(toInstanceMap(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/instances/{id}/pause")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> pauseInstance(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInstanceMap(
                executionService.pause(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/instances/{id}/resume")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> resumeInstance(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInstanceMap(
                executionService.resume(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/instances/{id}/cancel")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> cancelInstance(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(toInstanceMap(
                executionService.cancel(tenantId(auth), id, userId(auth), reason)));
    }

    // ===== Approvals =====

    @GetMapping("/approvals")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listPendingApprovals(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var approvals = approvalService.findPendingForTenant(tenantId(auth), limit);
        return ResponseEntity.ok(approvals.stream().map(this::toApprovalMap).toList());
    }

    @GetMapping("/approvals/pending")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listPendingApprovalsForUser(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var approvals = approvalService.findPendingForUser(tenantId(auth), userId(auth), limit);
        return ResponseEntity.ok(approvals.stream().map(this::toApprovalMap).toList());
    }

    @PostMapping("/approvals/{id}/approve")
    @RequireCapability("WORKFLOW.APPROVE")
    public ResponseEntity<Map<String, Object>> approveRequest(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var comments = body.getOrDefault("comments", "");
        return ResponseEntity.ok(toApprovalMap(
                approvalService.approve(tenantId(auth), id, userId(auth), comments)));
    }

    @PostMapping("/approvals/{id}/reject")
    @RequireCapability("WORKFLOW.APPROVE")
    public ResponseEntity<Map<String, Object>> rejectRequest(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var comments = body.getOrDefault("comments", "");
        return ResponseEntity.ok(toApprovalMap(
                approvalService.reject(tenantId(auth), id, userId(auth), comments)));
    }

    // ===== Monitoring =====

    @GetMapping("/monitoring/health")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<Map<String, Object>> monitoringHealth(
            Authentication auth) {
        var tenant = tenantId(auth);
        int overdueSteps = monitoringService.checkOverdueSteps(tenant);
        int overdueApprovals = monitoringService.checkOverdueApprovals(tenant);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "tenantId", tenant,
                "overdueSteps", overdueSteps,
                "overdueApprovals", overdueApprovals,
                "totalBreaches", overdueSteps + overdueApprovals
        ));
    }

    @PostMapping("/monitoring/check-sla")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> triggerSlaCheck(
            Authentication auth) {
        var tenant = tenantId(auth);
        int steps = monitoringService.checkOverdueSteps(tenant);
        int approvals = monitoringService.checkOverdueApprovals(tenant);
        return ResponseEntity.ok(Map.of(
                "tenantId", tenant,
                "overdueSteps", steps,
                "overdueApprovals", approvals,
                "totalBreaches", steps + approvals
        ));
    }

    // ===== Request DTOs =====

    public record CreateDefinitionRequest(
            String code, String name, String description,
            String module, String triggerType
    ) {}

    public record StartWorkflowRequest(
            UUID workflowDefinitionId,
            String businessEntityType,
            UUID businessEntityId,
            UUID correlationId
    ) {}

    // ===== Response helpers =====

    private Map<String, Object> toDefinitionMap(WorkflowDefinition d) {
        return Map.of(
                "id", d.id(),
                "code", d.code(),
                "name", d.name(),
                "status", d.status().name(),
                "triggerType", d.triggerType().name(),
                "module", d.module() != null ? d.module() : "",
                "version", d.version(),
                "versionLock", d.versionLock(),
                "createdBy", d.createdBy()
        );
    }

    private Map<String, Object> toInstanceMap(WorkflowInstance i) {
        return Map.of(
                "id", i.id(),
                "workflowDefinitionId", i.workflowDefinitionId(),
                "workflowVersion", i.workflowVersion(),
                "businessEntityType", i.businessEntityType(),
                "businessEntityId", i.businessEntityId(),
                "status", i.status().name(),
                "currentStepKey", i.currentStepKey() != null ? i.currentStepKey() : "",
                "startedBy", i.startedBy(),
                "version", i.version()
        );
    }

    private Map<String, Object> toApprovalMap(WorkflowApprovalRequest a) {
        return Map.of(
                "id", a.id(),
                "workflowInstanceId", a.workflowInstanceId(),
                "workflowStepInstanceId", a.workflowStepInstanceId(),
                "requestedFromUserId", a.requestedFromUserId(),
                "status", a.status().name(),
                "decision", a.decision() != null ? a.decision() : "",
                "comments", a.comments() != null ? a.comments() : "",
                "version", a.version()
        );
    }
}
