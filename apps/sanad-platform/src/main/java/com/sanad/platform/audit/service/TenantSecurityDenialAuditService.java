package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.security.denial.SecurityDenialCategory;
import com.sanad.platform.security.denial.SecurityDenialContext;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stage 05A.2.8 §6 — Tenant-level security denial audit.
 *
 * <p>Records post-authentication security denials (capability denied,
 * tenant selector mismatch, rotation required, etc.) to
 * {@code audit_events} using the verified TenantContext.</p>
 *
 * <p>Uses REQUIRES_NEW so the audit survives any business
 * transaction rollback.</p>
 *
 * <p>Stage 05A.2.9.1 §8 — Added {@link #recordDenial(SecurityDenialContext,
 * UUID, UUID, String)} so the {@link
 * com.sanad.platform.security.denial.SecurityDenialCoordinator} can record
 * a tenant-verified denial (TENANT_SELECTOR_MISMATCH, ROTATION_REQUIRED,
 * CAPABILITY_DENIED) with explicit IDs when the request's TenantContext
 * is not yet established (e.g. before TenantContextFilter runs) or has
 * already been cleared.</p>
 */
@Component
public class TenantSecurityDenialAuditService {

    private static final Logger log = LoggerFactory.getLogger(TenantSecurityDenialAuditService.class);

    private final AuditService auditService;
    private final TenantContextProvider contextProvider;

    public TenantSecurityDenialAuditService(AuditService auditService,
                                              TenantContextProvider contextProvider) {
        this.auditService = auditService;
        this.contextProvider = contextProvider;
    }

    /**
     * Legacy entry point used by {@link com.sanad.platform.shared.api.GlobalExceptionHandler}
     * for capability denials. Reads tenant/user IDs from the current
     * TenantContext.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(
            String action,
            String resourceType,
            String operation,
            String errorCode,
            String failureReason,
            Integer httpStatus) {

        TenantContext ctx = contextProvider.currentContext().orElse(null);
        if (ctx == null || ctx.tenantId() == null) {
            log.warn("Tenant denial audit skipped: no verified TenantContext for action={}", action);
            return;
        }

        auditService.recordDenied(AuditContext.builder(action, resourceType, operation)
                .actorType(AuditActorType.USER)
                .actorUserId(ctx.userId())
                .outcome(AuditOutcome.DENIED)
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .failureReason(failureReason)
                .build());
    }

    /**
     * Stage 05A.2.9.1 §8 — Coordinator entry point.
     *
     * <p>Records a tenant-verified denial ({@link SecurityDenialCategory#TENANT_SELECTOR_MISMATCH},
     * {@link SecurityDenialCategory#ROTATION_REQUIRED}, or
     * {@link SecurityDenialCategory#CAPABILITY_DENIED}) to
     * {@code audit_events} with explicit tenant/user IDs. The category
     * name is stored in the {@code error_code} column so test queries can
     * assert the exact classification.</p>
     *
     * <p>This overload does NOT consult the current TenantContext — the
     * coordinator supplies the IDs directly from the verified JWT claims.
     * This is required because the TenantContext may not be set yet
     * (TENANT_SELECTOR_MISMATCH fires before TenantContextFilter runs)
     * or may already have been cleared.</p>
     *
     * @param context   the typed denial context (carries category + httpStatus)
     * @param tenantId  the verified tenant ID from the JWT claim
     * @param userId    the verified user ID from the JWT claim
     * @param requestId the central request ID (from MDC, never re-generated)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(
            SecurityDenialContext context,
            UUID tenantId,
            UUID userId,
            String requestId) {

        if (tenantId == null || userId == null) {
            log.warn("Tenant denial audit skipped: missing tenantId/userId for category={}",
                    context.category());
            return;
        }

        SecurityDenialCategory category = context.category();
        String action = switch (category) {
            case TENANT_SELECTOR_MISMATCH -> "TENANT_SELECTOR_MISMATCH";
            case ROTATION_REQUIRED -> "ROTATION_REQUIRED";
            case CAPABILITY_DENIED -> "CAPABILITY_DENIED";
            default -> "SECURITY_DENIAL";
        };

        // Stage 05A.2.9.1 §10 — errorCode stores the canonical SANAD error
        // code (e.g. SANAD-SEC-001, SANAD-TEN-002, SANAD-ROT-001) so the
        // HTTP response code and the audit row error_code match. The
        // category name is stored in failureReason for classification.
        auditService.recordDenied(AuditContext.builder(action, "Security", "ACCESS")
                .actorType(AuditActorType.USER)
                .actorUserId(userId)
                .outcome(AuditOutcome.DENIED)
                .httpStatus(context.httpStatus())
                .errorCode(context.errorCode())
                .failureReason("Security denial category: " + category.name())
                .build());
    }
}
