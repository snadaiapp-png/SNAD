package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;
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
}
