package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.EscalationRepository;
import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link Escalation} lifecycle management.
 */
@Service
public class EscalationService {

    private final EscalationRepository escalationRepo;
    private final ManagementAuditRepository auditRepo;

    public EscalationService(
            EscalationRepository escalationRepo,
            ManagementAuditRepository auditRepo) {
        this.escalationRepo = escalationRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public Escalation create(Escalation escalation, UUID actorUserId) {
        var saved = escalationRepo.save(escalation);
        audit(actorUserId, saved, ManagementAuditEntry.Action.CREATE, null, saved.status().name());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Escalation> findById(UUID tenantId, UUID id) {
        return escalationRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Escalation> findByTenant(UUID tenantId, int limit) {
        return escalationRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<Escalation> findBySourceEntity(UUID tenantId, Escalation.SourceEntityType sourceType, UUID sourceId) {
        return escalationRepo.findBySourceEntity(tenantId, sourceType, sourceId);
    }

    @Transactional
    public Escalation acknowledge(UUID tenantId, UUID id, UUID actorUserId) {
        var e = load(tenantId, id);
        var oldStatus = e.status();
        var updated = escalationRepo.save(e.acknowledge());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Escalation resolve(UUID tenantId, UUID id, String resolution, UUID actorUserId) {
        var e = load(tenantId, id);
        var oldStatus = e.status();
        var updated = escalationRepo.save(e.resolve(resolution));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Escalation cancel(UUID tenantId, UUID id, UUID actorUserId) {
        var e = load(tenantId, id);
        var oldStatus = e.status();
        var updated = escalationRepo.save(e.cancel());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    private Escalation load(UUID tenantId, UUID id) {
        return escalationRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Escalation not found: " + id));
    }

    private void audit(UUID actorUserId, Escalation e,
                       ManagementAuditEntry.Action action, String fromState, String toState) {
        auditRepo.save(ManagementAuditEntry.create(
                e.tenantId(), actorUserId,
                ManagementAuditEntry.EntityType.ESCALATION, e.id(),
                action, fromState, toState, null, null
        ));
    }
}
