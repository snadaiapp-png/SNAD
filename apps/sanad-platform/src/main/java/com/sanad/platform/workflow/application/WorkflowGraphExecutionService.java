package com.sanad.platform.workflow.application;

import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowAssignmentRule;
import com.sanad.platform.workflow.domain.WorkflowBranchToken;
import com.sanad.platform.workflow.domain.WorkflowBranchTokenRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative Y2 graph transition command (design decisions H3/R3/AA3).
 *
 * <p>Routes exclusively by the persisted instance generation and the pinned
 * definition version: a Y2 instance never consults the legacy linear runtime,
 * and a LEGACY instance can never be advanced through this service. Outcome
 * selection matches the current step's outgoing transitions by outcome token
 * with deterministic priority ordering; zero or ambiguous matches raise a
 * graph-resolution incident instead of guessing.</p>
 */
@Service
public class WorkflowGraphExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGraphExecutionService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowWorkItemService workItemService;
    private final WorkflowAssignmentResolver assignmentResolver;
    private final WorkflowBranchTokenRepository branchTokenRepo;
    private final WorkflowApprovalRequestRepository approvalRepo;
    private final WorkflowSystemActionService systemActionService;
    private final WorkflowSystemActionAdapterRegistry adapterRegistry;
    private final HrEmployeeRepository employeeRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public WorkflowGraphExecutionService(
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository definitionRepo,
            WorkflowWorkItemService workItemService,
            WorkflowAssignmentResolver assignmentResolver,
            WorkflowBranchTokenRepository branchTokenRepo,
            WorkflowApprovalRequestRepository approvalRepo,
            WorkflowSystemActionService systemActionService,
            WorkflowSystemActionAdapterRegistry adapterRegistry,
            HrEmployeeRepository employeeRepo,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.definitionRepo = definitionRepo;
        this.workItemService = workItemService;
        this.assignmentResolver = assignmentResolver;
        this.branchTokenRepo = branchTokenRepo;
        this.approvalRepo = approvalRepo;
        this.systemActionService = systemActionService;
        this.adapterRegistry = adapterRegistry;
        this.employeeRepo = employeeRepo;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Outcome of an explicit SYSTEM_ACTION step execution command. */
    public record SystemActionOutcome(WorkflowInstance instance, UUID incidentId, String failureCategory) {}

    @Transactional
    public WorkflowInstance advance(UUID tenantId, UUID instanceId, String outcome, UUID actorUserId) {
        var instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found: " + instanceId));
        if (instance.engineGeneration() != WorkflowInstance.EngineGeneration.Y2) {
            throw new IllegalStateException(
                    "LEGACY instances must advance through the legacy runtime, not the Y2 graph");
        }
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new IllegalStateException("Cannot advance instance in status " + instance.status());
        }

        WorkflowStepInstance current = stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(si -> si.stepKey().equals(instance.currentStepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current step instance for key " + instance.currentStepKey()));

        List<WorkflowTransition> candidates = definitionRepo
                .findTransitions(instance.definitionVersionId()).stream()
                .filter(t -> t.fromStepId().equals(current.workflowStepId()))
                .filter(t -> outcomeMatches(t, outcome))
                .sorted(Comparator.comparingInt(WorkflowTransition::priority).reversed())
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Graph resolution incident: expected exactly one transition for outcome " + outcome
                            + " from step " + current.stepKey() + ", found " + candidates.size()
                            + " (instance=" + instanceId + ")");
        }
        WorkflowTransition selected = candidates.get(0);

        WorkflowStep nextStep = definitionRepo.findSteps(instance.definitionVersionId()).stream()
                .filter(s -> s.id().equals(selected.toStepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transition target step missing: " + selected.toStepId()));

        // Complete the current step instance (PENDING -> IN_PROGRESS -> COMPLETED
        // mirrors the legacy lifecycle semantics), then move the pointer.
        WorkflowStepInstance started = current.status() == WorkflowStepInstance.Status.PENDING
                ? stepInstanceRepo.save(current.start())
                : current;
        stepInstanceRepo.save(started.complete("Advanced via " + selected.transitionKey()));

        WorkflowInstance updated = instance.advanceToStep(nextStep.stepKey());

        if (nextStep.stepType() == WorkflowStep.StepType.PARALLEL_FORK) {
            Instant forkDue = slaDueAt(nextStep);
            stepInstanceRepo.save(WorkflowStepInstance.create(
                    tenantId, instanceId, nextStep.id(), nextStep.stepKey(), forkDue, null, null));
            return openForkBranches(tenantId, updated, nextStep);
        }

        if (nextStep.stepType() == WorkflowStep.StepType.PARALLEL_JOIN) {
            return arriveAtJoin(tenantId, updated, nextStep, null);
        }

        if (nextStep.stepType() == WorkflowStep.StepType.CALL_WORKFLOW) {
            return startChildWorkflow(tenantId, updated, nextStep);
        }

        if (nextStep.stepType() == WorkflowStep.StepType.END) {
            // Two sequential optimistic saves: the pointer move, then the
            // completion. Stacking both bumps into one record would skip a
            // version and fail the optimistic lock.
            var advanced = instanceRepo.save(updated);
            var saved = instanceRepo.save(advanced.complete());
            log.info("Y2 instance completed: tenant={} instance={} via {}",
                    tenantId, instanceId, selected.transitionKey());
            return saved;
        }

        Instant dueAt = nextStep.slaHours() != null
                ? Instant.now().plus(Duration.ofHours(nextStep.slaHours()))
                : null;
        WorkflowStepInstance nextStepInstance = stepInstanceRepo.save(WorkflowStepInstance.create(
                tenantId, instanceId, nextStep.id(), nextStep.stepKey(), dueAt,
                null, nextStep.requiredRole()));

        if (nextStep.stepType() == WorkflowStep.StepType.HUMAN_TASK
                || nextStep.stepType() == WorkflowStep.StepType.APPROVAL) {
            activateWorkItem(tenantId, updated, nextStepInstance, nextStep, actorUserId);
        }

        var saved = instanceRepo.save(updated);
        log.info("Y2 instance advanced: tenant={} instance={} step={} outcome={}",
                tenantId, instanceId, nextStep.stepKey(), outcome);
        return saved;
    }

    // ===== Explicit SYSTEM_ACTION execution command (P10 release contract) =====

    /**
     * Executes the instance's current step when it is a pending SYSTEM_ACTION
     * step. Success completes the step and advances the graph; a failed
     * action marks the step FAILED and leaves the instance RUNNING at that
     * step with the incident already persisted by the system-action service
     * — callers surface the incident id as a controlled 409, never a 500.
     * Idempotent per (instance, step) via the system-action attempt key.
     */
    @Transactional
    public SystemActionOutcome runCurrentSystemAction(UUID tenantId, UUID instanceId, UUID actorUserId) {
        var instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found: " + instanceId));
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            return new SystemActionOutcome(instance, null, null);
        }
        var current = stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(si -> si.stepKey().equals(instance.currentStepKey()))
                .findFirst().orElse(null);
        if (current == null || current.status() != WorkflowStepInstance.Status.PENDING) {
            return new SystemActionOutcome(instance, null, null);
        }
        var step = definitionRepo.findSteps(instance.definitionVersionId()).stream()
                .filter(s -> s.id().equals(current.workflowStepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Current step definition missing: " + current.workflowStepId()));
        if (step.stepType() != WorkflowStep.StepType.SYSTEM_ACTION) {
            return new SystemActionOutcome(instance, null, null);
        }

        var config = readConfiguration(step);
        var adapter = adapterRegistry.require(config.path("adapter").asText(null));
        var input = new java.util.HashMap<String, Object>();
        var inputNode = config.path("input");
        if (inputNode.isObject()) {
            inputNode.fields().forEachRemaining(e -> input.put(e.getKey(), e.getValue().isTextual()
                    ? e.getValue().asText() : objectMapper.convertValue(e.getValue(), Object.class)));
        }

        stepInstanceRepo.save(current.start());
        String idempotencyKey = "instance:" + instanceId + ":step:" + current.id();
        var result = systemActionService.execute(tenantId, instanceId, current.id(), adapter,
                input, instance.correlationId(), null, idempotencyKey, null);
        if (result.success()) {
            var finished = reloadStepInstance(instanceId, current.id()).complete(
                    "System action executed: " + result.externalReference());
            stepInstanceRepo.save(finished);
            var advanced = advance(tenantId, instanceId, null, actorUserId);
            return new SystemActionOutcome(advanced, null, null);
        }
        var failed = reloadStepInstance(instanceId, current.id())
                .fail("System action failed: " + result.failureCategory());
        stepInstanceRepo.save(failed);
        log.warn("Y2 system action failed: tenant={} instance={} step={} category={} incident={}",
                tenantId, instanceId, step.stepKey(), result.failureCategory(), result.incidentId());
        var reloaded = instanceRepo.findById(tenantId, instanceId).orElse(instance);
        return new SystemActionOutcome(reloaded, result.incidentId(), result.failureCategory());
    }

    private WorkflowStepInstance reloadStepInstance(UUID instanceId, UUID stepInstanceId) {
        return stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(si -> si.id().equals(stepInstanceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Step instance disappeared: " + stepInstanceId));
    }

    // ===== Controlled parallelism (R3) =====

    /**
     * Opens one durable branch token per fork outgoing edge and creates each
     * branch's first step instance. Branch chains advance through their own
     * transitions; human branches are claimed and completed through their
     * WorkItems like any other step.
     */
    private WorkflowInstance openForkBranches(UUID tenantId, WorkflowInstance instance, WorkflowStep forkStep) {
        List<WorkflowTransition> branches = definitionRepo
                .findTransitions(instance.definitionVersionId()).stream()
                .filter(t -> t.fromStepId().equals(forkStep.id()))
                .sorted(java.util.Comparator.comparingInt(WorkflowTransition::priority).reversed())
                .toList();
        if (branches.size() < 2) {
            throw new IllegalStateException("PARALLEL_FORK '" + forkStep.stepKey()
                    + "' needs at least two outgoing branch transitions");
        }
        WorkflowStepInstance forkInstance = stepInstanceRepo.findByInstance(instance.id()).stream()
                .filter(si -> si.stepKey().equals(forkStep.stepKey()))
                .findFirst()
                .orElseThrow();
        for (WorkflowTransition branch : branches) {
            branchTokenRepo.insert(WorkflowBranchToken.create(tenantId, instance.id(),
                    forkInstance.id(), branch.transitionKey(), branch.toStepId()));
        }
        // Branch step instances and their routing are minted when each branch's
        // first advance is commanded — the fork pointer only mints tokens.
        return instanceRepo.save(instance);
    }

    /**
     * Atomic join grant (R3): completes one arriving branch token with a
     * conditional UPDATE (race-safe, idempotent per token), then grants the
     * join advance through a single conditional increment on the join step
     * instance — exactly one arrival can observe the expected branch count,
     * so a concurrent race can never advance the graph twice. Returns true
     * for the single winning arrival.
     */
    public boolean grantJoinIfComplete(UUID tenantId, UUID workflowInstanceId,
                                       UUID joinStepInstanceId, UUID joinStepId,
                                       int expectedBranches, String arrivingBranchKey) {
        if (arrivingBranchKey != null) {
            List<WorkflowBranchToken> tokens = branchTokenRepo.findByJoin(
                    tenantId, workflowInstanceId, joinStepId);
            for (WorkflowBranchToken token : tokens) {
                if (!arrivingBranchKey.equals(token.branchKey())) continue;
                int marked = jdbc.update("""
                        UPDATE workflow_branch_tokens SET status = 'COMPLETED', version = version + 1, updated_at = NOW()
                        WHERE id = ? AND tenant_id = ? AND status = 'RUNNING'
                        """, token.id(), tenantId);
                if (marked == 0) {
                    // already completed by a concurrent arrival — fine
                }
            }
        }
        long completed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_branch_tokens
                WHERE tenant_id = ? AND workflow_instance_id = ? AND join_step_id = ?
                  AND status = 'COMPLETED'
                """, Long.class, tenantId, workflowInstanceId, joinStepId);
        if (completed < expectedBranches) {
            return false;
        }
        int claimed = jdbc.update("""
                UPDATE workflow_step_instances
                SET status = 'IN_PROGRESS', attempt_count = attempt_count + 1, updated_at = NOW()
                WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
                """, joinStepInstanceId, tenantId);
        return claimed == 1;
    }

    /**
     * Registers one branch arrival at the join and grants join completion via
     * the atomic grant. Kept for the graph flow; races are proven in
     * WorkflowParallelExecutionTest through {@link #grantJoinIfComplete}.
     */
    private WorkflowInstance arriveAtJoin(UUID tenantId, WorkflowInstance instance,
                                          WorkflowStep joinStep, String arrivingBranchKey) {
        WorkflowStepInstance joinInstance = stepInstanceRepo.findByInstance(instance.id()).stream()
                .filter(si -> si.stepKey().equals(joinStep.stepKey()))
                .findFirst()
                .orElseGet(() -> stepInstanceRepo.save(WorkflowStepInstance.create(
                        tenantId, instance.id(), joinStep.id(), joinStep.stepKey(), null, null, null)));

        if (arrivingBranchKey != null) {
            List<WorkflowBranchToken> forkTokens = branchTokenRepo.findByJoin(
                    tenantId, instance.id(), joinStep.id());
            forkTokens.stream()
                    .filter(t -> arrivingBranchKey.equals(t.branchKey()))
                    .filter(t -> t.status() == WorkflowBranchToken.Status.RUNNING)
                    .findFirst()
                    .ifPresent(token -> branchTokenRepo.save(new WorkflowBranchToken(
                            token.id(), token.tenantId(), token.workflowInstanceId(),
                            token.forkStepInstanceId(), token.branchKey(),
                            WorkflowBranchToken.Status.COMPLETED, token.joinStepId(),
                            token.version(), token.createdAt(), Instant.now())));
        }

        List<WorkflowBranchToken> all = branchTokenRepo.findByJoin(tenantId, instance.id(), joinStep.id());
        int expected = Math.max(all.size(), 1);
        long completed = all.stream().filter(t -> t.status() == WorkflowBranchToken.Status.COMPLETED).count();
        if (completed < expected) {
            return instance;
        }

        // Atomic grant: only one arrival's UPDATE may move the join out of PENDING.
        int claimed = jdbc.update("""
                UPDATE workflow_step_instances
                SET status = 'IN_PROGRESS', attempt_count = attempt_count + 1, updated_at = NOW()
                WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
                """, joinInstance.id(), tenantId);
        if (claimed == 0) {
            return instance; // another arrival already completed the join
        }
        stepInstanceRepo.save(stepInstanceRepo.findByInstance(instance.id()).stream()
                .filter(si -> si.id().equals(joinInstance.id()))
                .findFirst().orElseThrow()
                .complete("Join satisfied by " + completed + " branches"));

        List<WorkflowTransition> outgoing = definitionRepo
                .findTransitions(instance.definitionVersionId()).stream()
                .filter(t -> t.fromStepId().equals(joinStep.id()))
                .sorted(java.util.Comparator.comparingInt(WorkflowTransition::priority).reversed())
                .toList();
        if (outgoing.size() != 1) {
            throw new IllegalStateException("Graph resolution incident: join '" + joinStep.stepKey()
                    + "' must have exactly one outgoing transition, found " + outgoing.size());
        }
        WorkflowStep next = definitionRepo.findSteps(instance.definitionVersionId()).stream()
                .filter(st -> st.id().equals(outgoing.get(0).toStepId()))
                .findFirst()
                .orElseThrow();
        WorkflowInstance advanced = instance.advanceToStep(next.stepKey());
        if (next.stepType() == WorkflowStep.StepType.END) {
            var a = instanceRepo.save(advanced);
            return instanceRepo.save(a.complete());
        }
        return instanceRepo.save(advanced);
    }

    // ===== Sub-workflows (W3) =====

    /**
     * Starts a version-resolved child instance. Cycle prevention walks the
     * parent chain: a child whose definition family already appears among its
     * ancestors is rejected before any instance is created.
     */
    private WorkflowInstance startChildWorkflow(UUID tenantId, WorkflowInstance parent, WorkflowStep callStep) {
        com.fasterxml.jackson.databind.JsonNode config = callConfiguration(callStep);
        UUID targetFamily = UUID.fromString(config.path("definitionFamilyId").asText());
        boolean pinned = "PINNED".equalsIgnoreCase(config.path("versionMode").asText());
        UUID pinnedVersionId = config.hasNonNull("definitionVersionId")
                ? UUID.fromString(config.path("definitionVersionId").asText())
                : null;

        WorkflowDefinition childDefinition;
        if (pinned) {
            childDefinition = definitionRepo.findById(tenantId, pinnedVersionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pinned child definition not found: " + pinnedVersionId));
        } else {
            childDefinition = definitionRepo.findPublishedByFamily(tenantId, targetFamily)
                    .orElseThrow(() -> new IllegalStateException(
                            "No published child version for family " + targetFamily));
        }
        assertNoWorkflowCycle(tenantId, parent, childDefinition.definitionFamilyId());

        WorkflowInstance child = WorkflowInstance.startY2(tenantId,
                childDefinition.definitionFamilyId(), childDefinition.id(), childDefinition.version(),
                "CALL_WORKFLOW", parent.id(), firstStepKey(childDefinition),
                parent.startedBy(), parent.correlationId(),
                "CALL_WORKFLOW", parent.id(), null, parent.id(),
                parent.id());
        child = instanceRepo.save(child);
        WorkflowStep start = definitionRepo.findSteps(childDefinition.id()).stream()
                .filter(st -> st.stepType() == WorkflowStep.StepType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Child definition has no START step"));
        stepInstanceRepo.save(WorkflowStepInstance.create(tenantId, child.id(), start.id(),
                start.stepKey(), slaDueAt(start), null, null));
        log.info("Child workflow started: tenant={} parent={} child={} childDefinition={}",
                tenantId, parent.id(), child.id(), childDefinition.id());
        // WAIT_FOR_COMPLETION: the parent's CALL_WORKFLOW step stays PENDING;
        // child completion resumes the parent through the trigger/worker flow.
        return parent;
    }

    private void assertNoWorkflowCycle(UUID tenantId, WorkflowInstance parent, UUID childFamilyId) {
        if (parent.definitionFamilyId() != null && parent.definitionFamilyId().equals(childFamilyId)) {
            throw new IllegalStateException("Sub-workflow cycle detected: family " + childFamilyId
                    + " cannot call itself (instance=" + parent.id() + ")");
        }
        UUID cursor = parent.parentInstanceId();
        int depth = 0;
        while (cursor != null && depth < 16) {
            final UUID ancestorId = cursor;
            WorkflowInstance ancestor = instanceRepo.findById(tenantId, ancestorId)
                    .orElseThrow(() -> new IllegalStateException("Ancestor instance missing: " + ancestorId));
            if (ancestor.definitionFamilyId() != null
                    && ancestor.definitionFamilyId().equals(childFamilyId)) {
                throw new IllegalStateException("Sub-workflow cycle detected: family " + childFamilyId
                        + " already appears in the parent chain");
            }
            cursor = ancestor.parentInstanceId();
            depth++;
        }
    }

    private String firstStepKey(WorkflowDefinition definition) {
        return definitionRepo.findSteps(definition.id()).stream()
                .filter(st -> st.stepType() == WorkflowStep.StepType.START)
                .findFirst()
                .map(WorkflowStep::stepKey)
                .orElseThrow(() -> new IllegalStateException("Child definition has no START step"));
    }

    private com.fasterxml.jackson.databind.JsonNode callConfiguration(WorkflowStep step) {
        try {
            return objectMapper.readTree(step.configuration() == null ? "{}" : step.configuration());
        } catch (Exception e) {
            throw new IllegalStateException("CALL_WORKFLOW configuration is not valid JSON", e);
        }
    }

    private Instant slaDueAt(WorkflowStep step) {
        return step.slaHours() != null ? Instant.now().plus(Duration.ofHours(step.slaHours())) : null;
    }

    private boolean outcomeMatches(WorkflowTransition transition, String outcome) {
        if (outcome == null) {
            return true;
        }
        if (outcome.equals(transition.outcome())) {
            return true;
        }
        // Unkeyed transitions fall back to matching the transition key.
        return "SUCCESS".equalsIgnoreCase(transition.outcome())
                && outcome.equalsIgnoreCase(transition.transitionKey());
    }

    private void activateWorkItem(UUID tenantId, WorkflowInstance instance,
                                  WorkflowStepInstance stepInstance, WorkflowStep step,
                                  UUID actorUserId) {
        boolean hasCapability = step.requiredCapability() != null && !step.requiredCapability().isBlank();
        boolean hasRole = step.requiredRole() != null && !step.requiredRole().isBlank();
        WorkflowWorkItem.AssignmentMode mode =
                hasCapability || hasRole
                        ? WorkflowWorkItem.AssignmentMode.WORK_POOL
                        : WorkflowWorkItem.AssignmentMode.DIRECT;

        List<WorkflowWorkItemCandidate> candidates = List.of();
        List<UUID> approvalCandidateEmployees = List.of();
        UUID assignee = null;
        if (mode == WorkflowWorkItem.AssignmentMode.WORK_POOL) {
            WorkflowAssignmentRule rule = hasCapability
                    ? new WorkflowAssignmentRule.Permission(step.requiredCapability())
                    : new WorkflowAssignmentRule.Role(step.requiredRole());
            ResolvedAssignment resolved = assignmentResolver.resolve(tenantId, rule,
                    new WorkflowAssignmentContext(null));
            candidates = resolved.employeeIds().stream()
                    .map(employeeId -> WorkflowWorkItemCandidate.create(
                            tenantId, null, employeeId, resolved.resolutionSource()))
                    .toList();
            approvalCandidateEmployees = resolved.employeeIds();
        } else {
            // DIRECT assignment is explicit: the definition pins the assignee
            // through configuration.assigneeEmployeeId (or assignment.employeeId).
            // Unresolvable DIRECT assignment fails closed (ISE -> 409).
            assignee = resolveDirectAssignee(tenantId, step);
            approvalCandidateEmployees = List.of(assignee);
        }

        var item = WorkflowWorkItem.create(tenantId, instance.id(), stepInstance.id(),
                step.stepType() == WorkflowStep.StepType.APPROVAL
                        ? WorkflowWorkItem.Type.APPROVAL : WorkflowWorkItem.Type.HUMAN_TASK,
                mode, assignee, "WORKFLOW", "INSTANCE", instance.id(),
                step.name(), null, 0, null, null);
        // Bind candidates to the concrete WorkItem id now that it exists.
        var created = workItemService.create(item, candidates.stream()
                .map(c -> new WorkflowWorkItemCandidate(c.tenantId(), item.id(), c.employeeId(),
                        c.resolutionSource(), c.resolvedAt(), c.snapshotMetadata()))
                .toList());

        if (step.stepType() == WorkflowStep.StepType.APPROVAL) {
            createApprovalRequests(tenantId, instance, stepInstance, step, actorUserId,
                    approvalCandidateEmployees);
        }
    }

    /**
     * Resolves the pinned DIRECT assignee employee from the step's
     * configuration ({@code assigneeEmployeeId} or {@code assignment.employeeId})
     * through the canonical assignment resolver — fail-closed on missing,
     * malformed, or non-ACTIVE assignees.
     */
    private UUID resolveDirectAssignee(UUID tenantId, WorkflowStep step) {
        var config = readConfiguration(step);
        String raw = config.hasNonNull("assigneeEmployeeId")
                ? config.get("assigneeEmployeeId").asText()
                : config.path("assignment").path("employeeId").asText(null);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("DIRECT step '" + step.stepKey()
                    + "' requires configuration.assigneeEmployeeId");
        }
        UUID employeeId;
        try {
            employeeId = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DIRECT step '" + step.stepKey()
                    + "' has a malformed assigneeEmployeeId");
        }
        ResolvedAssignment resolved = assignmentResolver.resolve(tenantId,
                new WorkflowAssignmentRule.Employee(employeeId),
                new WorkflowAssignmentContext(null));
        return resolved.employeeIds().get(0);
    }

    /**
     * Creates one approval request per candidate approver user for an
     * activated APPROVAL step. Policy comes from the step configuration
     * ({@code approvalPolicy}: ANY_ONE default, ALL supported); self-approval
     * stays DENY by default. Requests are addressed to users (approvals act
     * on users) with the employee evidence preserved.
     */
    private void createApprovalRequests(UUID tenantId, WorkflowInstance instance,
                                        WorkflowStepInstance stepInstance, WorkflowStep step,
                                        UUID actorUserId, List<UUID> candidateEmployeeIds) {
        var config = readConfiguration(step);
        var aggregation = "ALL".equalsIgnoreCase(config.path("approvalPolicy").asText("ANY_ONE"))
                ? WorkflowApprovalPolicy.Aggregation.ALL
                : WorkflowApprovalPolicy.Aggregation.ANY_ONE;
        var policy = new WorkflowApprovalPolicy(aggregation, WorkflowApprovalPolicy.SelfApproval.DENY);
        for (UUID employeeId : candidateEmployeeIds.stream().distinct().toList()) {
            var employee = employeeRepo.findById(tenantId, employeeId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Approval candidate employee missing: " + employeeId));
            if (employee.userId() == null) {
                continue;
            }
            var request = WorkflowApprovalRequest.create(tenantId, instance.id(), stepInstance.id(),
                    employee.userId(), null, null, actorUserId, employeeId, policy);
            approvalRepo.save(request);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readConfiguration(WorkflowStep step) {
        try {
            return objectMapper.readTree(step.configuration() == null ? "{}" : step.configuration());
        } catch (Exception e) {
            throw new IllegalStateException("Step '" + step.stepKey() + "' configuration is not valid JSON", e);
        }
    }
}
