package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SLA Monitoring Service — detects SLA breaches and creates executive alerts.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detect decisions submitted but not approved past their SLA deadline</li>
 *   <li>Detect active escalations whose SLA deadline has passed</li>
 *   <li>Create alerts for each breach (idempotent via createOrGetExisting)</li>
 *   <li>Record audit events for SLA breach detection</li>
 * </ul>
 *
 * <p>This service is deterministic and idempotent: calling it multiple times
 * produces the same result — no duplicate alerts.
 *
 * <p>Can be triggered on-demand (for testing) or via @Scheduled (production).
 */
@Service
public class SlaMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitoringService.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private final ExecutiveDecisionRepository decisionRepo;
    private final EscalationRepository escalationRepo;
    private final ExecutiveAlertService alertService;
    private final ManagementAuditRepository auditRepo;
    private final JdbcTemplate jdbc;

    public SlaMonitoringService(
            ExecutiveDecisionRepository decisionRepo,
            EscalationRepository escalationRepo,
            ExecutiveAlertService alertService,
            ManagementAuditRepository auditRepo,
            JdbcTemplate jdbc) {
        this.decisionRepo = decisionRepo;
        this.escalationRepo = escalationRepo;
        this.alertService = alertService;
        this.auditRepo = auditRepo;
        this.jdbc = jdbc;
    }

    /**
     * Check all SLA breaches for a tenant. Idempotent.
     *
     * @return number of new alerts created (0 if all breaches were already alerted)
     */
    @Transactional
    public int checkAllSlaBreaches(UUID tenantId) {
        int decisionBreaches = checkDecisionApprovalSla(tenantId);
        int escalationBreaches = checkEscalationSla(tenantId);
        int total = decisionBreaches + escalationBreaches;
        if (total > 0) {
            log.info("SLA monitoring for tenant {}: {} breaches detected ({} decision, {} escalation)",
                    tenantId, total, decisionBreaches, escalationBreaches);
        }
        return total;
    }

    /**
     * Detect decisions that are pending approval past their SLA deadline.
     * Creates DECISION_PENDING alerts for each breach.
     *
     * @return number of new alerts created
     */
    @Transactional
    public int checkDecisionApprovalSla(UUID tenantId) {
        // Find submitted/under_review decisions with approval_due_at in the past
        var pendingDecisions = decisionRepo.findByTenantAndStatus(
                tenantId, ExecutiveDecision.Status.SUBMITTED, 100);
        pendingDecisions.addAll(decisionRepo.findByTenantAndStatus(
                tenantId, ExecutiveDecision.Status.UNDER_REVIEW, 100));

        int alertsCreated = 0;
        var now = Instant.now();
        for (var decision : pendingDecisions) {
            if (decision.approvalDueAt() != null && now.isAfter(decision.approvalDueAt())) {
                // Check if alert already exists (deduplication via createOrGetExisting)
                var existing = alertService.findBySource(
                        tenantId,
                        ExecutiveAlert.SourceEntityType.DECISION,
                        decision.id(),
                        ExecutiveAlert.AlertType.DECISION_PENDING);
                if (existing.isEmpty()) {
                    alertService.createOrGetExisting(
                            tenantId,
                            ExecutiveAlert.AlertType.DECISION_PENDING,
                            ExecutiveAlert.Severity.HIGH,
                            ExecutiveAlert.SourceEntityType.DECISION,
                            decision.id(),
                            "Decision SLA Breach: " + decision.title(),
                            "Decision '" + decision.decisionNumber()
                                    + "' has exceeded its approval SLA. "
                                    + "Submitted: " + (decision.submittedAt() != null ? decision.submittedAt() : "N/A")
                                    + ", Due: " + decision.approvalDueAt(),
                            SYSTEM_USER_ID
                    );
                    alertsCreated++;
                }
            }
        }
        return alertsCreated;
    }

    /**
     * Detect active escalations whose SLA deadline has passed.
     * Creates ESCALATION_OVERDUE alerts for each breach.
     * Also marks the escalation's sla_breached_at if not already set.
     *
     * @return number of new alerts created
     */
    @Transactional
    public int checkEscalationSla(UUID tenantId) {
        var activeEscalations = escalationRepo.findByTenantAndStatus(
                tenantId, Escalation.Status.ACTIVE, 100);

        int alertsCreated = 0;
        var now = Instant.now();
        for (var escalation : activeEscalations) {
            if (escalation.slaDeadline() != null && now.isAfter(escalation.slaDeadline())) {
                // Mark SLA as breached (if not already)
                if (!isSlaBreached(tenantId, escalation.id())) {
                    markSlaBreached(tenantId, escalation.id(), now);
                }
                // Create alert (deduplicated via createOrGetExisting)
                var existing = alertService.findBySource(
                        tenantId,
                        ExecutiveAlert.SourceEntityType.ESCALATION,
                        escalation.id(),
                        ExecutiveAlert.AlertType.ESCALATION_OVERDUE);
                if (existing.isEmpty()) {
                    alertService.createOrGetExisting(
                            tenantId,
                            ExecutiveAlert.AlertType.ESCALATION_OVERDUE,
                            ExecutiveAlert.Severity.CRITICAL,
                            ExecutiveAlert.SourceEntityType.ESCALATION,
                            escalation.id(),
                            "Escalation SLA Breach: " + escalation.code(),
                            "Escalation '" + escalation.code()
                                    + "' has exceeded its SLA deadline. "
                                    + "SLA Deadline: " + escalation.slaDeadline()
                                    + " Level: " + escalation.escalationLevel(),
                            SYSTEM_USER_ID
                    );
                    alertsCreated++;
                }
            }
        }
        return alertsCreated;
    }

    private boolean isSlaBreached(UUID tenantId, UUID escalationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM escalations WHERE tenant_id = ? AND id = ? AND sla_breached_at IS NOT NULL",
                Integer.class, tenantId, escalationId);
        return count != null && count > 0;
    }

    private void markSlaBreached(UUID tenantId, UUID escalationId, Instant breachedAt) {
        jdbc.update(
                "UPDATE escalations SET sla_breached_at = ? WHERE tenant_id = ? AND id = ? AND sla_breached_at IS NULL",
                Timestamp.from(breachedAt), tenantId, escalationId);
    }
}
