package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.EscalationRepository;
import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import com.sanad.platform.management.domain.Risk;
import com.sanad.platform.management.domain.RiskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link Risk} lifecycle management.
 *
 * <p>When a risk reaches CRITICAL severity, an automatic escalation is created.
 * This implements the cross-domain escalation rule:
 * "Critical Risk → Escalation"
 */
@Service
public class RiskService {

    private final RiskRepository riskRepo;
    private final EscalationRepository escalationRepo;
    private final ManagementAuditRepository auditRepo;

    public RiskService(
            RiskRepository riskRepo,
            EscalationRepository escalationRepo,
            ManagementAuditRepository auditRepo) {
        this.riskRepo = riskRepo;
        this.escalationRepo = escalationRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public Risk create(Risk risk, UUID actorUserId) {
        var saved = riskRepo.save(risk);
        audit(actorUserId, saved, ManagementAuditEntry.Action.CREATE, null, saved.status().name());
        // Auto-escalate if CRITICAL
        if (saved.severity() == Risk.Severity.CRITICAL) {
            createAutoEscalation(saved, actorUserId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Risk> findById(UUID tenantId, UUID id) {
        return riskRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Risk> findByTenant(UUID tenantId, int limit) {
        return riskRepo.findByTenant(tenantId, limit);
    }

    @Transactional
    public Risk reassess(UUID tenantId, UUID id, int probability, int impact, UUID actorUserId) {
        var risk = load(tenantId, id);
        var oldSeverity = risk.severity();
        var oldStatus = risk.status();
        var updated = riskRepo.save(risk.reassess(probability, impact));
        audit(actorUserId, updated, ManagementAuditEntry.Action.UPDATE, oldStatus.name(), updated.status().name());
        // Auto-escalate if severity escalated to CRITICAL
        if (oldSeverity != Risk.Severity.CRITICAL && updated.severity() == Risk.Severity.CRITICAL) {
            createAutoEscalation(updated, actorUserId);
        }
        return updated;
    }

    @Transactional
    public Risk startMitigation(UUID tenantId, UUID id, String mitigation,
                                 String contingency, String treatmentStrategy, UUID actorUserId) {
        var risk = load(tenantId, id);
        var oldStatus = risk.status();
        var updated = riskRepo.save(risk.startMitigation(mitigation, contingency, treatmentStrategy));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Risk monitor(UUID tenantId, UUID id, UUID actorUserId) {
        var risk = load(tenantId, id);
        var oldStatus = risk.status();
        var updated = riskRepo.save(risk.monitor());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Risk accept(UUID tenantId, UUID id, String residualRisk, UUID actorUserId) {
        var risk = load(tenantId, id);
        var oldStatus = risk.status();
        var updated = riskRepo.save(risk.accept(residualRisk));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Risk close(UUID tenantId, UUID id, UUID actorUserId) {
        var risk = load(tenantId, id);
        var oldStatus = risk.status();
        var updated = riskRepo.save(risk.close());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    private void createAutoEscalation(Risk risk, UUID createdBy) {
        var escalation = Escalation.create(
                risk.tenantId(),
                "ESC-RISK-" + risk.code(),
                Escalation.SourceEntityType.RISK,
                risk.id(),
                "Auto-escalation: Risk " + risk.code() + " reached CRITICAL severity (score=" + risk.riskScore() + ")",
                Escalation.Severity.CRITICAL,
                1,  // escalation level 1
                risk.ownerUserId(),
                null,  // no SLA deadline set automatically
                createdBy
        );
        escalationRepo.save(escalation);
    }

    private Risk load(UUID tenantId, UUID id) {
        return riskRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Risk not found: " + id));
    }

    private void audit(UUID actorUserId, Risk r,
                       ManagementAuditEntry.Action action, String fromState, String toState) {
        auditRepo.save(ManagementAuditEntry.create(
                r.tenantId(), actorUserId,
                ManagementAuditEntry.EntityType.RISK, r.id(),
                action, fromState, toState, null, null
        ));
    }
}
