package com.sanad.platform.hr.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.compliance.domain.ComplianceEnforcementLevel;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideAuditEntry;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideEventEntry;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideRequest;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideStatus;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceOverrideRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 WS3 Task 4 — governed compliance override workflow with four-eyes
 * approval.
 *
 * <p>Contract (authoritative design + plan 03 Task 4):</p>
 * <ul>
 *   <li>Only {@code MANDATORY_WITH_EXCEPTION} rules with
 *       {@code exception_allowed = true} may enter the governed flow;
 *       everything else fails closed with {@code HRM_COMPLIANCE_BLOCKED}.</li>
 *   <li>Approval requires the {@code HRM.COMPLIANCE_OVERRIDE.APPROVE}
 *       capability through {@link ComplianceOverrideAuthorizationPort}
 *       (capability AND scoped authorization; no ADMIN shortcut).</li>
 *   <li>Four-eyes: {@code requester != approver}, enforced at the
 *       application layer AND by the existing DB CHECK constraint.</li>
 *   <li>State machine: PENDING_APPROVAL -&gt; APPROVED | REJECTED;
 *       APPROVED -&gt; EXECUTED | REVOKED | EXPIRED. Illegal transitions are
 *       rejected deterministically ({@code HRM_OVERRIDE_INVALID_TRANSITION});
 *       transitions are tenant-bound and race-safe (conditional UPDATE).</li>
 *   <li>An APPROVED override is NOT permanent legal authority:
 *       {@link #authorizes} re-validates the underlying rule (hardened /
 *       suspended / retired / version-changed / outside window) immediately
 *       before the business action, so a stale approval never bypasses a
 *       fresh compliance decision. The source rule is never mutated.</li>
 *   <li>Audit and event ports are invoked inside the same transaction as the
 *       state change; the WS4 adapters append durable evidence
 *       transactionally (no REQUIRES_NEW).</li>
 * </ul>
 *
 * <p>Versioned event names (HRM naming pattern, stable):</p>
 * <pre>
 *   HRM.COMPLIANCE_OVERRIDE.REQUESTED.v1
 *   HRM.COMPLIANCE_OVERRIDE.APPROVED.v1
 *   HRM.COMPLIANCE_OVERRIDE.REJECTED.v1
 *   HRM.COMPLIANCE_OVERRIDE.REVOKED.v1
 *   HRM.COMPLIANCE_OVERRIDE.EXECUTED.v1
 * </pre>
 */
@Service
public class ComplianceOverrideService {

    public static final String EVENT_REQUESTED = "HRM.COMPLIANCE_OVERRIDE.REQUESTED.v1";
    public static final String EVENT_APPROVED = "HRM.COMPLIANCE_OVERRIDE.APPROVED.v1";
    public static final String EVENT_REJECTED = "HRM.COMPLIANCE_OVERRIDE.REJECTED.v1";
    public static final String EVENT_REVOKED = "HRM.COMPLIANCE_OVERRIDE.REVOKED.v1";
    public static final String EVENT_EXECUTED = "HRM.COMPLIANCE_OVERRIDE.EXECUTED.v1";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbc;
    private final JdbcComplianceOverrideRepository repository;
    private final ComplianceOverrideAuthorizationPort authorizationPort;
    private final ComplianceAuditPort auditPort;
    private final ComplianceEventPort eventPort;

    public ComplianceOverrideService(
            DataSource dataSource,
            ComplianceOverrideAuthorizationPort authorizationPort,
            ComplianceAuditPort auditPort,
            ComplianceEventPort eventPort) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.eventPort = Objects.requireNonNull(eventPort, "eventPort");
        PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.jdbc = new JdbcTemplate(dataSource);
        this.repository = new JdbcComplianceOverrideRepository(jdbc);
    }

    // ==================== REQUEST ====================

    /**
     * Requests a governed exception for a {@code MANDATORY_WITH_EXCEPTION}
     * rule whose metadata explicitly permits the legal exception path.
     * HARD rules, non-exception rules and inactive/uncertified pack rules
     * fail closed with {@code HRM_COMPLIANCE_BLOCKED}.
     */
    public UUID requestOverride(
            HrCommandContext context,
            UUID complianceRuleId,
            ComplianceResource resource,
            String justification,
            String evidenceReference,
            JsonNode requestedValueRedacted,
            JsonNode compliantValueRedacted,
            LocalDate validFrom,
            LocalDate validUntil) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(complianceRuleId, "complianceRuleId");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(validFrom, "validFrom");
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("HRM_COMPLIANCE_BLOCKED: justification is required for an override request");
        }
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("HRM_COMPLIANCE_BLOCKED: valid_until must not precede valid_from");
        }

        UUID tenantId = context.tenantId();
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);

            JdbcComplianceOverrideRepository.OverrideRuleState rule = repository
                    .loadRuleState(complianceRuleId)
                    .orElseThrow(() -> new IllegalStateException(
                            "HRM_COMPLIANCE_BLOCKED: no effective compliance rule for the requested override"));
            if (!"ACTIVE".equals(rule.ruleStatus())) {
                throw new IllegalStateException(
                        "HRM_COMPLIANCE_BLOCKED: rule is not ACTIVE (status=" + rule.ruleStatus() + ")");
            }
            if (!packLegallyEffective(rule)) {
                throw new IllegalStateException(
                        "HRM_COMPLIANCE_BLOCKED: country pack is not legally effective (status=" + rule.packStatus() + ")");
            }
            if (rule.enforcementLevel() == null
                    || ComplianceEnforcementLevel.valueOf(rule.enforcementLevel()) != ComplianceEnforcementLevel.MANDATORY_WITH_EXCEPTION
                    || !rule.exceptionAllowed()) {
                throw new IllegalStateException(
                        "HRM_COMPLIANCE_BLOCKED: only MANDATORY_WITH_EXCEPTION rules with exception_allowed=true "
                                + "may enter the governed override flow");
            }

            UUID requestId = repository.insertRequest(
                    tenantId, complianceRuleId, resource.resourceType(), resource.resourceId(),
                    requestedValueRedacted, compliantValueRedacted,
                    Objects.requireNonNull(context.actorUserId(), "actorUserId"),
                    justification, evidenceReference, validFrom, validUntil);

            auditPort.recordOverrideAction(new ComplianceOverrideAuditEntry(
                    tenantId, requestId, "REQUESTED", context.actorUserId(), "SUCCESS", "OVERRIDE_REQUESTED"));
            eventPort.recordOverrideEvent(new ComplianceOverrideEventEntry(
                    tenantId, requestId, EVENT_REQUESTED, context.actorUserId(), context.correlationId(), null,
                    eventPayload(tenantId, requestId, rule, resource, validFrom, validUntil)));
            return requestId;
        });
    }

    // ==================== APPROVE / REJECT / REVOKE ====================

    public ComplianceOverrideRequest approve(UUID tenantId, UUID requestId, UUID approverUserId, String comment) {
        Objects.requireNonNull(approverUserId, "approverUserId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            ComplianceOverrideRequest current = requireRequest(tenantId, requestId);
            requirePending(current);
            authorizationPort.requireApprovalAuthorization(tenantId, approverUserId, requestId);
            if (approverUserId.equals(current.requesterUserId())) {
                throw new IllegalStateException(
                        "HRM_OVERRIDE_SELF_APPROVAL_DENIED: requester and approver must be different users (four-eyes)");
            }
            int updated = repository.approveIfPending(tenantId, requestId, approverUserId, comment);
            if (updated != 1) {
                // Lost a race with another approval/transition — re-read to expose the current state.
                ComplianceOverrideRequest moved = requireRequest(tenantId, requestId);
                throw new IllegalStateException(
                        "HRM_OVERRIDE_INVALID_TRANSITION: request is " + moved.status() + ", expected PENDING_APPROVAL");
            }
            ComplianceOverrideRequest approved = requireRequest(tenantId, requestId);
            auditPort.recordOverrideAction(new ComplianceOverrideAuditEntry(
                    tenantId, requestId, "APPROVED", approverUserId, "SUCCESS", "OVERRIDE_APPROVED"));
            eventPort.recordOverrideEvent(new ComplianceOverrideEventEntry(
                    tenantId, requestId, EVENT_APPROVED, approverUserId, null, null,
                    lifecyclePayload(tenantId, requestId, approved)));
            return approved;
        });
    }

    public ComplianceOverrideRequest reject(UUID tenantId, UUID requestId, UUID actingUserId, String comment) {
        Objects.requireNonNull(actingUserId, "actingUserId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            ComplianceOverrideRequest current = requireRequest(tenantId, requestId);
            requirePending(current);
            repository.rejectIfPending(tenantId, requestId, comment);
            ComplianceOverrideRequest rejected = requireRequest(tenantId, requestId);
            auditPort.recordOverrideAction(new ComplianceOverrideAuditEntry(
                    tenantId, requestId, "REJECTED", actingUserId, "SUCCESS", "OVERRIDE_REJECTED"));
            eventPort.recordOverrideEvent(new ComplianceOverrideEventEntry(
                    tenantId, requestId, EVENT_REJECTED, actingUserId, null, null,
                    lifecyclePayload(tenantId, requestId, rejected)));
            return rejected;
        });
    }

    public ComplianceOverrideRequest revoke(UUID tenantId, UUID requestId, UUID actingUserId, String comment) {
        Objects.requireNonNull(actingUserId, "actingUserId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            ComplianceOverrideRequest current = requireRequest(tenantId, requestId);
            if (current.status() != ComplianceOverrideStatus.APPROVED) {
                throw new IllegalStateException(
                        "HRM_OVERRIDE_INVALID_TRANSITION: " + current.status() + " -> REVOKED is not allowed");
            }
            repository.revokeIfApproved(tenantId, requestId, comment);
            ComplianceOverrideRequest revoked = requireRequest(tenantId, requestId);
            auditPort.recordOverrideAction(new ComplianceOverrideAuditEntry(
                    tenantId, requestId, "REVOKED", actingUserId, "SUCCESS", "OVERRIDE_REVOKED"));
            eventPort.recordOverrideEvent(new ComplianceOverrideEventEntry(
                    tenantId, requestId, EVENT_REVOKED, actingUserId, null, null,
                    lifecyclePayload(tenantId, requestId, revoked)));
            return revoked;
        });
    }

    /**
     * Consumes an APPROVED override for execution. Defense in depth: the
     * underlying rule is revalidated here as well (in addition to
     * {@link #authorizes} which the caller must run immediately before the
     * business action) — a hardened/suspended/retired rule invalidates the
     * prior approval.
     */
    public ComplianceOverrideRequest markExecuted(UUID tenantId, UUID requestId, UUID actingUserId, String auditReference) {
        Objects.requireNonNull(actingUserId, "actingUserId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            ComplianceOverrideRequest current = requireRequest(tenantId, requestId);
            if (current.status() != ComplianceOverrideStatus.APPROVED) {
                throw new IllegalStateException(
                        "HRM_OVERRIDE_INVALID_TRANSITION: " + current.status() + " -> EXECUTED is not allowed");
            }
            if (!ruleStillExceptionCapable(current, LocalDate.now())) {
                throw new IllegalStateException(
                        "HRM_COMPLIANCE_BLOCKED: underlying rule no longer permits the governed exception");
            }
            repository.executeIfApproved(tenantId, requestId, auditReference);
            ComplianceOverrideRequest executed = requireRequest(tenantId, requestId);
            auditPort.recordOverrideAction(new ComplianceOverrideAuditEntry(
                    tenantId, requestId, "EXECUTED", actingUserId, "SUCCESS", "OVERRIDE_EXECUTED"));
            eventPort.recordOverrideEvent(new ComplianceOverrideEventEntry(
                    tenantId, requestId, EVENT_EXECUTED, actingUserId, null, null,
                    lifecyclePayload(tenantId, requestId, executed)));
            return executed;
        });
    }

    // ==================== REVALIDATION GATE ====================

    /**
     * Immediately before executing the business action: verifies the
     * override is APPROVED and still binds the EXACT tenant, rule, resource
     * type/id and validity window, and re-evaluates the underlying rule
     * state. If the source rule became HARD, expired, was suspended/retired,
     * changed incompatibly, or no longer permits the exception, the old
     * approval does NOT bypass it — the caller must obtain a fresh compliance
     * decision. The source rule is never mutated to represent an override.
     */
    public boolean authorizes(
            UUID tenantId,
            UUID requestId,
            UUID complianceRuleId,
            String resourceType,
            UUID resourceId,
            LocalDate actionDate) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actionDate, "actionDate");
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            bindTenant(tenantId);

            // Lazy APPROVED -> EXPIRED transition for past-validity requests.
            repository.expireIfPastValidity(tenantId, requestId, LocalDate.now());

            ComplianceOverrideRequest request = repository.findById(tenantId, requestId).orElse(null);
            if (request == null) {
                return false; // invisible (other tenant / unknown id) — fail closed
            }
            if (request.status() != ComplianceOverrideStatus.APPROVED) {
                return false;
            }
            if (!request.complianceRuleId().equals(complianceRuleId)) {
                return false;
            }
            if (!request.resourceType().equals(resourceType)) {
                return false;
            }
            if (!Objects.equals(request.resourceId(), resourceId)) {
                return false;
            }
            if (actionDate.isBefore(request.validFrom())) {
                return false;
            }
            if (request.validUntil() != null && actionDate.isAfter(request.validUntil())) {
                return false;
            }
            return ruleStillExceptionCapable(request, actionDate);
        }));
    }

    // ==================== INTERNALS ====================

    private boolean ruleStillExceptionCapable(ComplianceOverrideRequest request, LocalDate actionDate) {
        JdbcComplianceOverrideRepository.OverrideRuleState rule = repository
                .loadRuleState(request.complianceRuleId())
                .orElse(null);
        if (rule == null || !packLegallyEffective(rule) || !"ACTIVE".equals(rule.ruleStatus())) {
            return false;
        }
        if (rule.enforcementLevel() == null
                || ComplianceEnforcementLevel.valueOf(rule.enforcementLevel()) != ComplianceEnforcementLevel.MANDATORY_WITH_EXCEPTION
                || !rule.exceptionAllowed()) {
            return false;
        }
        if (rule.ruleEffectiveFrom() != null && actionDate.isBefore(rule.ruleEffectiveFrom())) {
            return false;
        }
        return rule.ruleEffectiveTo() == null || !actionDate.isAfter(rule.ruleEffectiveTo());
    }

    private boolean packLegallyEffective(JdbcComplianceOverrideRepository.OverrideRuleState rule) {
        return ("ACTIVE".equals(rule.packStatus()) || "CERTIFIED".equals(rule.packStatus()))
                && rule.packLegallyReviewed();
    }

    private ComplianceOverrideRequest requireRequest(UUID tenantId, UUID requestId) {
        return repository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_OVERRIDE_NOT_FOUND: no override request " + requestId + " in tenant " + tenantId));
    }

    private void requirePending(ComplianceOverrideRequest request) {
        if (request.status() != ComplianceOverrideStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "HRM_OVERRIDE_INVALID_TRANSITION: request is " + request.status() + ", expected PENDING_APPROVAL");
        }
    }

    /** Transaction-scoped tenant GUC for fail-closed RLS (is_local = true). */
    private void bindTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    private JsonNode lifecyclePayload(UUID tenantId, UUID requestId, ComplianceOverrideRequest request) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("tenantId", tenantId.toString());
        payload.put("requestId", requestId.toString());
        payload.put("ruleId", request.complianceRuleId().toString());
        payload.put("resourceType", request.resourceType());
        if (request.resourceId() != null) {
            payload.put("resourceId", request.resourceId().toString());
        }
        payload.put("status", request.status().name());
        if (request.validFrom() != null) {
            payload.put("validFrom", request.validFrom().toString());
        }
        if (request.validUntil() != null) {
            payload.put("validUntil", request.validUntil().toString());
        }
        return payload;
    }

    private JsonNode eventPayload(
            UUID tenantId, UUID requestId,
            JdbcComplianceOverrideRepository.OverrideRuleState rule,
            ComplianceResource resource, LocalDate validFrom, LocalDate validUntil) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("tenantId", tenantId.toString());
        payload.put("requestId", requestId.toString());
        payload.put("ruleId", rule.ruleId().toString());
        payload.put("ruleCode", rule.ruleCode());
        payload.put("ruleVersion", rule.ruleVersion());
        payload.put("resourceType", resource.resourceType());
        if (resource.resourceId() != null) {
            payload.put("resourceId", resource.resourceId().toString());
        }
        if (validFrom != null) {
            payload.put("validFrom", validFrom.toString());
        }
        if (validUntil != null) {
            payload.put("validUntil", validUntil.toString());
        }
        return payload;
    }
}
