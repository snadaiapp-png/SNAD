package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.EscalationRepository;
import com.sanad.platform.management.domain.Issue;
import com.sanad.platform.management.domain.IssueRepository;
import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link Issue} lifecycle management.
 *
 * <p>When an issue reaches CRITICAL severity, an automatic escalation is created.
 * This implements the cross-domain escalation rule:
 * "Critical Issue → Escalation"
 */
@Service
public class IssueService {

    private final IssueRepository issueRepo;
    private final EscalationRepository escalationRepo;
    private final ManagementAuditRepository auditRepo;

    public IssueService(
            IssueRepository issueRepo,
            EscalationRepository escalationRepo,
            ManagementAuditRepository auditRepo) {
        this.issueRepo = issueRepo;
        this.escalationRepo = escalationRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public Issue create(Issue issue, UUID actorUserId) {
        var saved = issueRepo.save(issue);
        audit(actorUserId, saved, ManagementAuditEntry.Action.CREATE, null, saved.status().name());
        if (saved.severity() == Issue.Severity.CRITICAL) {
            createAutoEscalation(saved, actorUserId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Issue> findById(UUID tenantId, UUID id) {
        return issueRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Issue> findByTenant(UUID tenantId, int limit) {
        return issueRepo.findByTenant(tenantId, limit);
    }

    @Transactional
    public Issue triage(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.triage());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue startProgress(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.startProgress());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue block(UUID tenantId, UUID id, String reason, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.block(reason));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue unblock(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.unblock());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue resolve(UUID tenantId, UUID id, String resolution, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.resolve(resolution));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue close(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.close());
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    @Transactional
    public Issue reopen(UUID tenantId, UUID id, String reason, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status();
        var updated = issueRepo.save(i.reopen(reason));
        audit(actorUserId, updated, ManagementAuditEntry.Action.STATE_CHANGE, oldStatus.name(), updated.status().name());
        return updated;
    }

    private void createAutoEscalation(Issue issue, UUID createdBy) {
        var escalation = Escalation.create(
                issue.tenantId(),
                "ESC-ISSUE-" + issue.code(),
                Escalation.SourceEntityType.ISSUE,
                issue.id(),
                "Auto-escalation: Issue " + issue.code() + " has CRITICAL severity",
                Escalation.Severity.CRITICAL,
                1,
                issue.ownerUserId(),
                null,
                createdBy
        );
        escalationRepo.save(escalation);
    }

    private Issue load(UUID tenantId, UUID id) {
        return issueRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + id));
    }

    private void audit(UUID actorUserId, Issue i,
                       ManagementAuditEntry.Action action, String fromState, String toState) {
        auditRepo.save(ManagementAuditEntry.create(
                i.tenantId(), actorUserId,
                ManagementAuditEntry.EntityType.ISSUE, i.id(),
                action, fromState, toState, null, null
        ));
    }
}
