package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Break-glass emergency commands (design decision AH3).
 *
 * <p>Break-glass is an explicit privileged command, never a hidden bypass:
 * it requires the {@code WORKFLOW.BREAK_GLASS} capability (enforced at the
 * command boundary), a non-blank reason, and appends an OVERRIDE business
 * audit event recording the real actor, the affected object, the reason, and
 * the timestamp. It must never forge another employee's approval decision,
 * impersonate an approver, erase rejection evidence, mutate a published
 * version, cross a tenant boundary, or silently defeat B1 semantics — the
 * emergency surface is deliberately limited to unblocking and cancelling a
 * stuck instance, with every step audited.</p>
 */
@Service
public class WorkflowBreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowBreakGlassService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTransitionAuditRepository auditRepo;

    public WorkflowBreakGlassService(WorkflowInstanceRepository instanceRepo,
                                     WorkflowTransitionAuditRepository auditRepo) {
        this.instanceRepo = instanceRepo;
        this.auditRepo = auditRepo;
    }

    /**
     * Unblocks a FAILED or PAUSED instance back to RUNNING so it can be
     * retried through the normal, fully-authorized command paths.
     */
    @Transactional
    public WorkflowInstance emergencyResume(UUID tenantId, UUID instanceId, UUID actorUserId, String reason) {
        requireReason(reason);
        WorkflowInstance instance = load(tenantId, instanceId);
        WorkflowInstance resumed;
        try {
            resumed = switch (instance.status()) {
                case FAILED, PAUSED -> instance.resume();
                default -> throw new IllegalStateException(
                        "Break-glass resume is not defined for status " + instance.status());
            };
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Break-glass resume rejected: " + e.getMessage(), e);
        }
        WorkflowInstance saved = instanceRepo.save(resumed);
        audit(actorUserId, saved, "RESUME", reason);
        log.warn("BREAK-GLASS resume: tenant={} instance={} actor={} reason={}",
                tenantId, instanceId, actorUserId, reason);
        return saved;
    }

    /**
     * Cancels a stuck instance through the normal CANCELLED transition —
     * break-glass does not invent new terminal states.
     */
    @Transactional
    public WorkflowInstance emergencyCancel(UUID tenantId, UUID instanceId, UUID actorUserId, String reason) {
        requireReason(reason);
        WorkflowInstance instance = load(tenantId, instanceId);
        WorkflowInstance cancelled;
        try {
            cancelled = instance.cancel(actorUserId, "BREAK-GLASS: " + reason);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Break-glass cancel rejected: " + e.getMessage(), e);
        }
        WorkflowInstance saved = instanceRepo.save(cancelled);
        audit(actorUserId, saved, "CANCEL", reason);
        log.warn("BREAK-GLASS cancel: tenant={} instance={} actor={} reason={}",
                tenantId, instanceId, actorUserId, reason);
        return saved;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Break-glass reason is required");
        }
    }

    private WorkflowInstance load(UUID tenantId, UUID instanceId) {
        return instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowInstance not found in tenant: " + instanceId));
    }

    /** Appends the OVERRIDE audit row — append-only business evidence. */
    private void audit(UUID actorUserId, WorkflowInstance instance, String command, String reason) {
        auditRepo.save(WorkflowTransitionAudit.create(
                instance.tenantId(), instance.id(), null,
                actorUserId, WorkflowTransitionAudit.Action.OVERRIDE,
                null, instance.status().name(), null,
                "BREAK-GLASS " + command + ": " + reason));
    }
}
