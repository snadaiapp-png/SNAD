package com.sanad.platform.hr.compliance.application;

import java.util.UUID;

/**
 * WS3-facing authorization port for governed override approval (WS3 Task 4).
 *
 * <p>Approval requires the {@code HRM.COMPLIANCE_OVERRIDE.APPROVE} capability
 * through the existing authorization boundary. Capability alone must not
 * bypass scoped authorization where canonical HRM scope is required: the WS4
 * adapter evaluates coarse capability AND canonical scope grants (default
 * deny). Implementations must throw a deterministic denial
 * (containing {@code HRM_SCOPE_DENIED}) when the principal is not authorized.</p>
 */
public interface ComplianceOverrideAuthorizationPort {

    /**
     * Verifies that {@code approverUserId} holds the governed approve
     * capability with a matching canonical scope for the override request.
     *
     * @throws IllegalStateException on denial (message contains HRM_SCOPE_DENIED)
     */
    void requireApprovalAuthorization(UUID tenantId, UUID approverUserId, UUID requestId);
}
