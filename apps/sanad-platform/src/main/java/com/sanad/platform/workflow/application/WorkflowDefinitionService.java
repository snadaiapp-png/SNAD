package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionValidation;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link WorkflowDefinition} lifecycle management.
 *
 * <p>State machine: DRAFT → ACTIVE → INACTIVE → ARCHIVED
 *
 * <p>Controllers should never call repositories directly — they go through
 * this service. Every lifecycle transition is logged via SLF4J.
 *
 * <p><strong>Audit note:</strong> the {@code workflow_transition_audit} table
 * has a NOT NULL {@code workflow_instance_id} column with an FK to
 * {@code workflow_instances(id)}, so definition-level events cannot be written
 * there. Instead, they are logged via SLF4J with structured fields
 * (tenantId, definitionId, action, fromState, toState, actorUserId) so they
 * can be correlated with instance-level audit rows when an instance is later
 * created from the definition.
 */
@Service
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowTransitionAuditRepository auditRepo;
    private final WorkflowDefinitionValidator validator;

    public WorkflowDefinitionService(
            WorkflowDefinitionRepository defRepo,
            WorkflowTransitionAuditRepository auditRepo,
            WorkflowDefinitionValidator validator) {
        this.defRepo = defRepo;
        this.auditRepo = auditRepo;
        this.validator = validator;
    }

    @Transactional
    public WorkflowDefinition create(WorkflowDefinition def, UUID actorUserId) {
        var saved = defRepo.save(def);
        // Save any steps that were attached to the definition
        logDefEvent(actorUserId, saved, WorkflowTransitionAudit.Action.CREATE,
                null, saved.status().name());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> findById(UUID tenantId, UUID id) {
        return defRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> findByTenant(UUID tenantId, int limit) {
        return defRepo.findByTenant(tenantId, limit);
    }

    @Transactional
    public WorkflowDefinition activate(UUID tenantId, UUID id, UUID actorUserId) {
        var def = load(tenantId, id);
        var oldStatus = def.status().name();
        var updated = defRepo.save(def.activate());
        logDefEvent(actorUserId, updated, WorkflowTransitionAudit.Action.ACTIVATE,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowDefinition deactivate(UUID tenantId, UUID id, UUID actorUserId) {
        var def = load(tenantId, id);
        var oldStatus = def.status().name();
        var updated = defRepo.save(def.deactivate());
        logDefEvent(actorUserId, updated, WorkflowTransitionAudit.Action.DEACTIVATE,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowDefinition archive(UUID tenantId, UUID id, UUID actorUserId) {
        var def = load(tenantId, id);
        var oldStatus = def.status().name();
        var updated = defRepo.save(def.archive());
        logDefEvent(actorUserId, updated, WorkflowTransitionAudit.Action.ARCHIVE,
                oldStatus, updated.status().name());
        return updated;
    }

    /**
     * Publishes a DRAFT as an immutable Y2 version (I3). The AN3 publish
     * gate runs the structural validator first; a failing validation blocks
     * publication with HTTP 422 semantics. The published checksum is the
     * tamper-detection reference for audit and runtime loading.
     */
    @Transactional
    public WorkflowDefinition publish(UUID tenantId, UUID id, UUID actorUserId) {
        var def = load(tenantId, id);
        var validation = validator.validate(tenantId, id);
        if (!validation.valid()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Workflow definition validation failed: " + validation.errors().get(0).code());
        }
        var updated = defRepo.save(def.publish(actorUserId, checksum(def)));
        logDefEvent(actorUserId, updated, WorkflowTransitionAudit.Action.ACTIVATE,
                def.publicationState().name(), updated.publicationState().name());
        return updated;
    }

    /**
     * Deterministic tamper-detection checksum over the published graph
     * (steps + transitions), stable for identical definition content.
     */
    private String checksum(WorkflowDefinition def) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var payload = new StringBuilder();
            defRepo.findSteps(def.id()).stream()
                    .sorted(java.util.Comparator.comparing(WorkflowStep::stepKey))
                    .forEach(s -> payload.append(s.stepKey()).append(':')
                            .append(s.stepType()).append(':')
                            .append(s.sequenceOrder()).append(';'));
            defRepo.findTransitions(def.id()).stream()
                    .sorted(java.util.Comparator.comparing(WorkflowTransition::transitionKey))
                    .forEach(t -> payload.append(t.transitionKey()).append("->")
                            .append(t.toStepId()).append(':')
                            .append(t.outcome()).append(';'));
            var hash = digest.digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Creates the next DRAFT version in the same definition family from a
     * published source version. The source version stays PUBLISHED and keeps
     * serving the instances pinned to it.
     */
    @Transactional
    public WorkflowDefinition createNextDraft(UUID tenantId, UUID sourceDefinitionId, UUID actorUserId) {
        var source = load(tenantId, sourceDefinitionId);
        var draft = defRepo.save(source.nextDraft(actorUserId));
        log.info("WorkflowDefinition next draft: tenant={} family={} sourceVersion={} draftId={} draftVersion={} actor={}",
                tenantId, source.definitionFamilyId(), source.version(), draft.id(), draft.version(), actorUserId);
        return draft;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> findVersions(UUID tenantId, UUID definitionFamilyId) {
        return defRepo.findVersions(tenantId, definitionFamilyId);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionValidation validate(UUID tenantId, UUID definitionId) {
        return validator.validate(tenantId, definitionId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowTransition> findTransitions(UUID tenantId, UUID definitionId) {
        load(tenantId, definitionId);
        return defRepo.findTransitions(definitionId);
    }

    /**
     * Creates a graph transition between two steps of a DRAFT definition
     * (H3/R3). Both steps must belong to the same concrete definition version.
     */
    @Transactional
    public WorkflowTransition addTransition(
            UUID tenantId, UUID definitionId,
            UUID fromStepId, UUID toStepId,
            String transitionKey, String outcome,
            String conditionAst, int priority, String metadata,
            UUID actorUserId) {
        var def = load(tenantId, definitionId);
        if (def.publicationState() != WorkflowDefinition.PublicationState.DRAFT) {
            throw new IllegalStateException(
                    "Transitions can only be added to DRAFT definitions; current state: "
                            + def.publicationState());
        }
        // Validate steps belong to this definition version.
        var stepIds = defRepo.findSteps(definitionId).stream()
                .map(WorkflowStep::id).collect(java.util.stream.Collectors.toSet());
        if (!stepIds.contains(fromStepId)) {
            throw new IllegalArgumentException("fromStepId does not belong to this definition");
        }
        if (!stepIds.contains(toStepId)) {
            throw new IllegalArgumentException("toStepId does not belong to this definition");
        }
        var transition = WorkflowTransition.create(
                tenantId, definitionId, fromStepId, toStepId,
                transitionKey, outcome, conditionAst, priority, metadata);
        var saved = defRepo.saveTransition(transition);
        log.info("Transition added: tenant={} definition={} key={} from={} to={} actor={}",
                tenantId, definitionId, transitionKey, fromStepId, toStepId, actorUserId);
        return saved;
    }

    @Transactional
    public WorkflowStep addStep(WorkflowStep step, UUID actorUserId) {
        var def = load(step.tenantId(), step.workflowDefinitionId());
        if (def.publicationState() != WorkflowDefinition.PublicationState.DRAFT) {
            throw new IllegalStateException(
                    "Steps can only be added to DRAFT definitions; current state: "
                            + def.publicationState());
        }
        var saved = defRepo.saveStep(step);
        log.info("WorkflowStep added: tenant={} definitionId={} stepKey={} actor={}",
                saved.tenantId(), saved.workflowDefinitionId(), saved.stepKey(), actorUserId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WorkflowStep> findSteps(UUID workflowDefinitionId) {
        return defRepo.findSteps(workflowDefinitionId);
    }

    private WorkflowDefinition load(UUID tenantId, UUID id) {
        return defRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowDefinition not found: " + id));
    }

    /**
     * Log a definition-level lifecycle event. See class JavaDoc for why
     * definition-level events are not written to {@code workflow_transition_audit}.
     */
    private void logDefEvent(UUID actorUserId, WorkflowDefinition def,
                            WorkflowTransitionAudit.Action action,
                            String fromState, String toState) {
        log.info(
                "WorkflowDefinition event: action={} tenant={} definitionId={} code={} version={} fromState={} toState={} actor={}",
                action.name(), def.tenantId(), def.id(), def.code(), def.version(),
                fromState, toState, actorUserId);
    }
}
