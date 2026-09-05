package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.compliance.application.ComplianceOverrideAuthorizationPort;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.security.scope.ScopedAuthorizationDecision;
import com.sanad.platform.security.scope.ScopedAuthorizationRequest;
import com.sanad.platform.security.scope.ScopedAuthorizationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WS4 adapter bridging the WS3-facing {@link ComplianceOverrideAuthorizationPort}
 * to the existing canonical authorization boundary (WS4 Task 4).
 *
 * <p>Approval requires the {@code HRM.COMPLIANCE_OVERRIDE.APPROVE} capability
 * evaluated together with canonical scope grants (default deny) — capability
 * alone never bypasses scoped authorization. No ADMIN/System Manager
 * shortcut exists here: a HARD rule is unreachable through the governed
 * flow anyway, and approval authorization is exactly capability+scope.</p>
 */
@Service
public class ScopedComplianceOverrideAuthorizationAdapter implements ComplianceOverrideAuthorizationPort {

    public static final String APPROVE_CAPABILITY = "HRM.COMPLIANCE_OVERRIDE.APPROVE";

    private final ScopedAuthorizationService scopedAuthorizationService;

    public ScopedComplianceOverrideAuthorizationAdapter(ScopedAuthorizationService scopedAuthorizationService) {
        this.scopedAuthorizationService = Objects.requireNonNull(scopedAuthorizationService, "scopedAuthorizationService");
    }

    @Override
    public void requireApprovalAuthorization(UUID tenantId, UUID approverUserId, UUID requestId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(approverUserId, "approverUserId");
        Objects.requireNonNull(requestId, "requestId");

        HrAuthorizationResourceContext resource = new HrAuthorizationResourceContext(
                tenantId, "COMPLIANCE_OVERRIDE_REQUEST", requestId,
                null, null, null, null, null, null, "OPERATIONAL", null);
        ScopedAuthorizationDecision decision = scopedAuthorizationService.authorize(new ScopedAuthorizationRequest(
                tenantId, approverUserId, APPROVE_CAPABILITY, resource, Instant.now()));
        if (decision == null || !decision.allowed()) {
            throw new IllegalStateException(
                    "HRM_SCOPE_DENIED: approver lacks " + APPROVE_CAPABILITY
                            + " capability/scope (reason="
                            + (decision == null ? "NO_DECISION" : decision.reason()) + ")");
        }
    }
}
