package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.ExecutiveDecision;
import com.sanad.platform.management.domain.ExecutiveDecisionRepository;
import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link ExecutiveDecision} lifecycle management.
 *
 * <p>Enforces segregation of duties: the user who APPROVES a decision
 * cannot be the same user who CREATED it. This is a governance control.
 *
 * <p>Every state change is recorded in the audit trail.
 */
@Service
public class ExecutiveDecisionService {

    private final ExecutiveDecisionRepository decisionRepo;
    private final ManagementAuditRepository auditRepo;

    public ExecutiveDecisionService(
            ExecutiveDecisionRepository decisionRepo,
            ManagementAuditRepository auditRepo) {
        this.decisionRepo = decisionRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public ExecutiveDecision create(ExecutiveDecision decision, UUID actorUserId) {
        var saved = decisionRepo.save(decision);
        audit(actorUserId, saved, ManagementAuditEntry.Action.CREATE, null, saved.status().name());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ExecutiveDecision> findById(UUID tenantId, UUID id) {
        return decisionRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<ExecutiveDecision> findByTenant(UUID tenantId, int limit) {
        return decisionRepo.findByTenant(tenantId, limit);
    }

    @Transactional
    public ExecutiveDecision submit(UUID tenantId, UUID id, UUID actorUserId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.submit());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision startReview(UUID tenantId, UUID id, UUID actorUserId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.startReview());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision approve(UUID tenantId, UUID id, UUID approverId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.approve(approverId));
        audit(approverId, updated, ManagementAuditEntry.Action.APPROVE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision reject(UUID tenantId, UUID id, UUID rejecterId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.reject(rejecterId));
        audit(rejecterId, updated, ManagementAuditEntry.Action.REJECT, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision startExecuting(UUID tenantId, UUID id, UUID actorUserId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.startExecuting());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision complete(UUID tenantId, UUID id, String actualOutcome, UUID actorUserId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.complete(actualOutcome));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public ExecutiveDecision cancel(UUID tenantId, UUID id, UUID actorUserId) {
        var d = load(tenantId, id);
        var oldStatus = d.status();
        var updated = decisionRepo.save(d.cancel());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public void delete(UUID tenantId, UUID id, UUID actorUserId) {
        var d = load(tenantId, id);
        decisionRepo.deleteById(tenantId, id);
        audit(actorUserId, d, ManagementAuditEntry.Action.DELETE, d.status().name(), null);
    }

    private ExecutiveDecision load(UUID tenantId, UUID id) {
        return decisionRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found: " + id));
    }

    private void audit(UUID actorUserId, ExecutiveDecision d,
                       ManagementAuditEntry.Action action, String fromState, String toState) {
        auditRepo.save(ManagementAuditEntry.create(
                d.tenantId(), actorUserId,
                ManagementAuditEntry.EntityType.DECISION, d.id(),
                action, fromState, toState, null, null
        ));
    }
}
