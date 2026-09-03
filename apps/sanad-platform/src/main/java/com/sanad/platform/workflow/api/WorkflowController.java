package com.sanad.platform.workflow.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowActionabilityService;
import com.sanad.platform.workflow.application.WorkflowBreakGlassService;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowSimulationService;
import com.sanad.platform.workflow.application.WorkflowMonitoringService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import org.springframework.http.HttpStatus;
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
    private final WorkflowSimulationService simulationService;
    private final WorkflowWorkItemService workItemService;
    private final WorkflowIncidentService incidentService;
    private final WorkflowActionabilityService actionabilityService;
    private final WorkflowBreakGlassService breakGlassService;

    public WorkflowController(
            WorkflowDefinitionService definitionService,
            WorkflowExecutionService executionService,
            WorkflowApprovalService approvalService,
            WorkflowMonitoringService monitoringService,
            WorkflowSimulationService simulationService,
            WorkflowWorkItemService workItemService,
            WorkflowIncidentService incidentService,
            WorkflowActionabilityService actionabilityService,
            WorkflowBreakGlassService breakGlassService) {
        this.definitionService = definitionService;
        this.executionService = executionService;
        this.approvalService = approvalService;
        this.monitoringService = monitoringService;
        this.simulationService = simulationService;
        this.workItemService = workItemService;
        this.incidentService = incidentService;
        this.actionabilityService = actionabilityService;
        this.breakGlassService = breakGlassService;
    }

    // ===== Exception Handling =====

    /**
     * Map IllegalStateException (SOD violations, state machine violations) to HTTP 409 CONFLICT
     * instead of HTTP 500. This ensures business-rule rejections are not treated as
     * internal server errors in production error sweeps.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            org.springframework.dao.OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "code", "STALE_VERSION",
                "message", e.getMessage() != null ? e.getMessage() : "Version conflict"
        ));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "message", e.getMessage() != null ? e.getMessage() : "Workflow state conflict"
        ));
    }

    /**
     * Map IllegalArgumentException (reference integrity violations, missing
     * entities) to HTTP 400 BAD_REQUEST so callers get a controlled 4xx
     * instead of a 500 from the global handler.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", e.getMessage() != null ? e.getMessage() : "Invalid request"
        ));
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
        var defs = definitionService.findByTenant(tenantId(auth), safeLimit(limit));
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

    /**
     * AN3 publish-gate preview: runs the structural validator against the
     * draft and reports stable, language-neutral error codes.
     */
    @PostMapping("/definitions/{id}/validate")
    @RequireCapability("WORKFLOW.VALIDATE")
    public ResponseEntity<Map<String, Object>> validateDefinition(
            Authentication auth, @PathVariable UUID id) {
        var validation = definitionService.validate(tenantId(auth), id);
        return ResponseEntity.ok(Map.of(
                "valid", validation.valid(),
                "errors", validation.errors().stream()
                        .map(e -> Map.of(
                                "code", (Object) e.code(),
                                "message", e.message() != null ? e.message() : "",
                                "stepId", e.stepId() != null ? e.stepId().toString() : ""))
                        .toList()));
    }

    /**
     * Side-effect-free simulation (AN3). System actions, notifications, and
     * sub-workflows are stubbed; the response is explicitly marked
     * non-production.
     */
    @PostMapping("/definitions/{id}/simulate")
    @RequireCapability("WORKFLOW.VALIDATE")
    public ResponseEntity<Map<String, Object>> simulateDefinition(
            Authentication auth, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> context) {
        var result = simulationService.simulate(tenantId(auth), id,
                context != null ? context : Map.of());
        return ResponseEntity.ok(Map.of(
                "valid", result.valid(),
                "simulated", result.simulated(),
                "visitedStepIds", result.visitedStepIds().stream().map(UUID::toString).toList(),
                "notes", result.notes()));
    }

    // ===== WorkItems (C3/L3) =====

    /**
     * Every work-item command revalidates the acting user's actionability
     * (ACTIVE linked employee/user) server-side before touching the item —
     * assignment never grants authorization (D3/N3).
     */
    private com.sanad.platform.hr.domain.HrEmployee requireActorEmployee(Authentication auth) {
        return actionabilityService.requireActionableEmployee(tenantId(auth), userId(auth));
    }

    @GetMapping("/work-items/mine")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<List<Map<String, Object>>> myWorkItems(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(workItemService
                .findMyWork(tenantId(auth), employee.id(), safeLimit(limit))
                .stream().map(this::toWorkItemMap).toList());
    }

    @GetMapping("/work-items/pool")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<List<Map<String, Object>>> poolWorkItems(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(workItemService
                .findPoolWork(tenantId(auth), employee.id(), safeLimit(limit))
                .stream().map(this::toWorkItemMap).toList());
    }

    public record WorkItemCommandRequest(long expectedVersion, String reason) {}
    public record WorkItemReassignRequest(UUID newAssigneeEmployeeId, long expectedVersion, String reason) {}

    @PostMapping("/work-items/{id}/claim")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<Map<String, Object>> claimWorkItem(
            Authentication auth, @PathVariable UUID id,
            @RequestBody WorkItemCommandRequest req) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(toWorkItemMap(workItemService.claim(
                tenantId(auth), id, employee.id(), req.expectedVersion())));
    }

    @PostMapping("/work-items/{id}/release")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<Map<String, Object>> releaseWorkItem(
            Authentication auth, @PathVariable UUID id,
            @RequestBody WorkItemCommandRequest req) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(toWorkItemMap(workItemService.release(
                tenantId(auth), id, employee.id(), req.expectedVersion())));
    }

    @PostMapping("/work-items/{id}/complete")
    @RequireCapability("WORKFLOW.TASK_EXECUTE")
    public ResponseEntity<Map<String, Object>> completeWorkItem(
            Authentication auth, @PathVariable UUID id,
            @RequestBody WorkItemCommandRequest req) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(toWorkItemMap(workItemService.complete(
                tenantId(auth), id, employee.id(), req.expectedVersion())));
    }

    @PostMapping("/work-items/{id}/reassign")
    @RequireCapability("WORKFLOW.REASSIGN")
    public ResponseEntity<Map<String, Object>> reassignWorkItem(
            Authentication auth, @PathVariable UUID id,
            @RequestBody WorkItemReassignRequest req) {
        var employee = requireActorEmployee(auth);
        return ResponseEntity.ok(toWorkItemMap(workItemService.reassign(
                tenantId(auth), id, req.newAssigneeEmployeeId(),
                employee.id(), req.expectedVersion(), req.reason())));
    }

    // ===== Publishing (I3) =====

    public record PublishDefinitionRequest(long expectedVersion) {}

    @PostMapping("/definitions/{id}/publish")
    @RequireCapability("WORKFLOW.PUBLISH")
    public ResponseEntity<Map<String, Object>> publishDefinition(
            Authentication auth, @PathVariable UUID id,
            @RequestBody PublishDefinitionRequest req) {
        var def = definitionService.findById(tenantId(auth), id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowDefinition not found: " + id));
        if (def.versionLock() != req.expectedVersion()) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowDefinition " + id + " was modified by another publisher");
        }
        return ResponseEntity.ok(toDefinitionMap(
                definitionService.publish(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/definitions/{id}/next-draft")
    @RequireCapability("WORKFLOW.DESIGN")
    public ResponseEntity<Map<String, Object>> nextDraft(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDefinitionMap(
                definitionService.createNextDraft(tenantId(auth), id, userId(auth))));
    }

    @GetMapping("/definitions/{id}/transitions")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listTransitions(
            Authentication auth, @PathVariable UUID id) {
        definitionService.findById(tenantId(auth), id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowDefinition not found: " + id));
        return ResponseEntity.ok(definitionService.findTransitions(tenantId(auth), id).stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", t.id());
                    map.put("fromStepId", t.fromStepId());
                    map.put("toStepId", t.toStepId());
                    map.put("transitionKey", t.transitionKey());
                    map.put("outcome", t.outcome() != null ? t.outcome() : "");
                    map.put("priority", t.priority());
                    return map;
                })
                .toList());
    }

    // ===== Transitions (H3/R3) =====

    public record CreateTransitionRequest(
            UUID fromStepId, UUID toStepId, String transitionKey,
            String outcome, String conditionAst, int priority, String metadata) {}

    @PostMapping("/definitions/{id}/transitions")
    @RequireCapability("WORKFLOW.DESIGN")
    public ResponseEntity<Map<String, Object>> createTransition(
            Authentication auth, @PathVariable UUID id,
            @RequestBody CreateTransitionRequest req) {
        var saved = definitionService.addTransition(
                tenantId(auth), id,
                req.fromStepId(), req.toStepId(),
                req.transitionKey(), req.outcome(),
                req.conditionAst(), req.priority(), req.metadata(),
                userId(auth));
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", saved.id());
        map.put("fromStepId", saved.fromStepId());
        map.put("toStepId", saved.toStepId());
        map.put("transitionKey", saved.transitionKey());
        map.put("outcome", saved.outcome() != null ? saved.outcome() : "");
        map.put("priority", saved.priority());
        return ResponseEntity.ok(map);
    }

    // ===== Break glass (AH3) =====

    public record BreakGlassRequest(String reason) {}

    @PostMapping("/instances/{id}/break-glass/resume")
    @RequireCapability("WORKFLOW.BREAK_GLASS")
    public ResponseEntity<Map<String, Object>> breakGlassResume(
            Authentication auth, @PathVariable UUID id,
            @RequestBody BreakGlassRequest req) {
        var instance = breakGlassService.emergencyResume(
                tenantId(auth), id, userId(auth), req.reason());
        return ResponseEntity.ok(toInstanceMap(instance));
    }

    @PostMapping("/instances/{id}/break-glass/cancel")
    @RequireCapability("WORKFLOW.BREAK_GLASS")
    public ResponseEntity<Map<String, Object>> breakGlassCancel(
            Authentication auth, @PathVariable UUID id,
            @RequestBody BreakGlassRequest req) {
        var instance = breakGlassService.emergencyCancel(
                tenantId(auth), id, userId(auth), req.reason());
        return ResponseEntity.ok(toInstanceMap(instance));
    }

    // ===== Incidents (AF3) =====

    @GetMapping("/incidents")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<List<Map<String, Object>>> openIncidents(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(incidentService.findOpen(tenantId(auth), safeLimit(limit))
                .stream().map(this::toIncidentMap).toList());
    }

    public record IncidentResolveRequest(String resolution) {}

    @PostMapping("/incidents/{id}/acknowledge")
    @RequireCapability("WORKFLOW.INCIDENT_MANAGE")
    public ResponseEntity<Map<String, Object>> acknowledgeIncident(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toIncidentMap(
                incidentService.acknowledge(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/incidents/{id}/resolve")
    @RequireCapability("WORKFLOW.INCIDENT_MANAGE")
    public ResponseEntity<Map<String, Object>> resolveIncident(
            Authentication auth, @PathVariable UUID id,
            @RequestBody IncidentResolveRequest req) {
        return ResponseEntity.ok(toIncidentMap(
                incidentService.resolve(tenantId(auth), id, userId(auth), req.resolution())));
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

    // ===== Steps =====

    @PostMapping("/definitions/{id}/steps")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> addStep(
            Authentication auth, @PathVariable UUID id, @RequestBody CreateStepRequest req) {
        var tenant = tenantId(auth);
        var actor = userId(auth);
        // Validate definition exists and belongs to authenticated tenant
        definitionService.findById(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowDefinition not found: " + id));
        var step = WorkflowStep.create(
                tenant, id, req.stepKey(), req.name(),
                WorkflowStep.StepType.valueOf(req.stepType()),
                req.sequenceOrder(), req.configuration(),
                req.slaHours(), req.requiredCapability(), req.requiredRole()
        );
        var saved = definitionService.addStep(step, actor);
        return ResponseEntity.ok(toStepMap(saved));
    }

    @GetMapping("/definitions/{id}/steps")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listSteps(
            Authentication auth, @PathVariable UUID id) {
        var tenant = tenantId(auth);
        // Validate definition belongs to tenant
        definitionService.findById(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowDefinition not found: " + id));
        var steps = definitionService.findSteps(id);
        return ResponseEntity.ok(steps.stream().map(this::toStepMap).toList());
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
        // Start eligibility is generation-aware (Z3/AA3):
        // LEGACY definitions use the legacy ACTIVE lifecycle,
        // Y2 definitions use the PUBLISHED publication state.
        boolean startEligible = switch (def.engineGeneration()) {
            case LEGACY -> def.status() == WorkflowDefinition.Status.ACTIVE;
            case Y2 -> def.publicationState() == WorkflowDefinition.PublicationState.PUBLISHED;
        };
        if (!startEligible) {
            throw new IllegalStateException(
                    "WorkflowDefinition " + def.id() + " is not a valid start target "
                            + "(status=" + def.status()
                            + ", publicationState=" + def.publicationState() + ")");
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
        var instances = executionService.findByTenant(tenantId(auth), safeLimit(limit));
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

    @GetMapping("/instances/{id}/steps")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listStepInstances(
            Authentication auth, @PathVariable UUID id) {
        var tenant = tenantId(auth);
        // Validate instance belongs to tenant
        executionService.findById(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowInstance not found: " + id));
        var stepInstances = executionService.findStepInstances(tenant, id);
        return ResponseEntity.ok(stepInstances.stream().map(this::toStepInstanceMap).toList());
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

    @PostMapping("/instances/{id}/advance")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> advanceInstance(
            Authentication auth, @PathVariable UUID id,
            @RequestBody AdvanceInstanceRequest req) {
        return ResponseEntity.ok(toInstanceMap(
                executionService.advanceToNextStep(
                        tenantId(auth), id, req.nextStepKey(), userId(auth))));
    }

    @PostMapping("/instances/{id}/complete")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> completeInstance(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInstanceMap(
                executionService.complete(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/instances/{id}/fail")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> failInstance(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var reason = body.getOrDefault("reason", "Failed via REST API");
        return ResponseEntity.ok(toInstanceMap(
                executionService.fail(tenantId(auth), id, reason, userId(auth))));
    }

    // ===== Approvals =====

    @GetMapping("/approvals")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listPendingApprovals(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var approvals = approvalService.findPendingForTenant(tenantId(auth), safeLimit(limit));
        return ResponseEntity.ok(approvals.stream().map(this::toApprovalMap).toList());
    }

    @GetMapping("/approvals/pending")
    @RequireCapability("WORKFLOW.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listPendingApprovalsForUser(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var approvals = approvalService.findPendingForUser(tenantId(auth), userId(auth), safeLimit(limit));
        return ResponseEntity.ok(approvals.stream().map(this::toApprovalMap).toList());
    }

    @PostMapping("/instances/{instanceId}/approvals")
    @RequireCapability("WORKFLOW.WRITE")
    public ResponseEntity<Map<String, Object>> createApproval(
            Authentication auth, @PathVariable UUID instanceId,
            @RequestBody CreateApprovalRequest req) {
        var tenant = tenantId(auth);
        var actor = userId(auth);
        // Validate instance exists and belongs to tenant
        executionService.findById(tenant, instanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowInstance not found: " + instanceId));
        var approval = WorkflowApprovalRequest.create(
                tenant, instanceId, req.workflowStepInstanceId(),
                req.requestedFromUserId(), req.requestedFromRole(),
                req.dueAt(), actor
        );
        var saved = approvalService.createApproval(approval, actor);
        return ResponseEntity.ok(toApprovalMap(saved));
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

    public record CreateStepRequest(
            String stepKey, String name, String stepType,
            int sequenceOrder, String configuration,
            Integer slaHours, String requiredCapability, String requiredRole
    ) {}

    public record AdvanceInstanceRequest(String nextStepKey) {}

    public record CreateApprovalRequest(
            UUID workflowStepInstanceId,
            UUID requestedFromUserId,
            String requestedFromRole,
            java.time.Instant dueAt
    ) {}

    // ===== Response helpers =====

    private Map<String, Object> toDefinitionMap(WorkflowDefinition d) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", d.id());
        map.put("code", d.code());
        map.put("name", d.name());
        map.put("status", d.status().name());
        map.put("triggerType", d.triggerType().name());
        map.put("module", d.module() != null ? d.module() : "");
        map.put("version", d.version());
        map.put("versionLock", d.versionLock());
        map.put("createdBy", d.createdBy());
        map.put("definitionFamilyId", d.definitionFamilyId() != null ? d.definitionFamilyId().toString() : "");
        map.put("engineGeneration", d.engineGeneration() != null ? d.engineGeneration().name() : "");
        map.put("publicationState", d.publicationState() != null ? d.publicationState().name() : "");
        return map;
    }

    private Map<String, Object> toInstanceMap(WorkflowInstance i) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", i.id());
        map.put("workflowDefinitionId", i.workflowDefinitionId());
        map.put("workflowVersion", i.workflowVersion());
        map.put("businessEntityType", i.businessEntityType());
        map.put("businessEntityId", i.businessEntityId());
        map.put("status", i.status().name());
        map.put("currentStepKey", i.currentStepKey() != null ? i.currentStepKey() : "");
        map.put("startedBy", i.startedBy());
        map.put("version", i.version());
        map.put("engineGeneration", i.engineGeneration() != null ? i.engineGeneration().name() : "");
        map.put("definitionFamilyId", i.definitionFamilyId() != null ? i.definitionFamilyId().toString() : "");
        map.put("definitionVersionId", i.definitionVersionId() != null ? i.definitionVersionId().toString() : "");
        return map;
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

    private Map<String, Object> toStepMap(WorkflowStep s) {
        return Map.ofEntries(
                Map.entry("id", s.id()),
                Map.entry("workflowDefinitionId", s.workflowDefinitionId()),
                Map.entry("stepKey", s.stepKey()),
                Map.entry("name", s.name()),
                Map.entry("stepType", s.stepType().name()),
                Map.entry("sequenceOrder", s.sequenceOrder()),
                Map.entry("configuration", s.configuration() != null ? s.configuration() : ""),
                Map.entry("slaHours", s.slaHours() != null ? s.slaHours() : 0),
                Map.entry("requiredCapability", s.requiredCapability() != null ? s.requiredCapability() : ""),
                Map.entry("requiredRole", s.requiredRole() != null ? s.requiredRole() : ""),
                Map.entry("version", s.version())
        );
    }

    private Map<String, Object> toStepInstanceMap(WorkflowStepInstance si) {
        return Map.ofEntries(
                Map.entry("id", si.id()),
                Map.entry("workflowInstanceId", si.workflowInstanceId()),
                Map.entry("workflowStepId", si.workflowStepId()),
                Map.entry("stepKey", si.stepKey()),
                Map.entry("status", si.status().name()),
                Map.entry("assignedUserId", si.assignedUserId() != null ? si.assignedUserId() : ""),
                Map.entry("assignedRole", si.assignedRole() != null ? si.assignedRole() : ""),
                Map.entry("startedAt", si.startedAt() != null ? si.startedAt().toString() : ""),
                Map.entry("completedAt", si.completedAt() != null ? si.completedAt().toString() : ""),
                Map.entry("dueAt", si.dueAt() != null ? si.dueAt().toString() : ""),
                Map.entry("attemptCount", si.attemptCount()),
                Map.entry("result", si.result() != null ? si.result() : ""),
                Map.entry("version", si.version())
        );
    }
    private Map<String, Object> toWorkItemMap(WorkflowWorkItem w) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", w.id());
        map.put("workflowInstanceId", w.workflowInstanceId());
        map.put("workflowStepInstanceId", w.workflowStepInstanceId());
        map.put("type", w.type().name());
        map.put("status", w.status().name());
        map.put("assigneeEmployeeId", w.assigneeEmployeeId() != null ? w.assigneeEmployeeId().toString() : "");
        map.put("claimedByEmployeeId", w.claimedByEmployeeId() != null ? w.claimedByEmployeeId().toString() : "");
        map.put("assignmentMode", w.assignmentMode().name());
        map.put("title", w.title());
        map.put("priority", w.priority());
        map.put("dueAt", w.dueAt() != null ? w.dueAt().toString() : "");
        map.put("version", w.version());
        return map;
    }

    private Map<String, Object> toIncidentMap(WorkflowIncident i) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", i.id());
        map.put("workflowInstanceId", i.workflowInstanceId() != null ? i.workflowInstanceId().toString() : "");
        map.put("source", i.source());
        map.put("severity", i.severity().name());
        map.put("failureCategory", i.failureCategory() != null ? i.failureCategory() : "");
        map.put("status", i.status().name());
        map.put("resolution", i.resolution() != null ? i.resolution() : "");
        map.put("createdAt", i.createdAt().toString());
        return map;
    }

    /**
     * Clamp the client-supplied list limit. Negative values fall back to the
     * default page size (PostgreSQL rejects a negative LIMIT with
     * "LIMIT must not be negative"), and the upper bound caps unbounded scans.
     */
    static int safeLimit(int limit) {
        if (limit < 0) return 50;
        return Math.min(limit, 200);
    }
}
