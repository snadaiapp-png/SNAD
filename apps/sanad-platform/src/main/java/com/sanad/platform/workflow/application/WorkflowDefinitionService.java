package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
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

    public WorkflowDefinitionService(
            WorkflowDefinitionRepository defRepo,
            WorkflowTransitionAuditRepository auditRepo) {
        this.defRepo = defRepo;
        this.auditRepo = auditRepo;
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

    @Transactional
    public WorkflowStep addStep(WorkflowStep step, UUID actorUserId) {
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
