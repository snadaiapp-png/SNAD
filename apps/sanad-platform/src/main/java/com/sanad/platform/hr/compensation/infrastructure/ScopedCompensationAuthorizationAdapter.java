package com.sanad.platform.hr.compensation.infrastructure;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compensation.application.CompensationAuthorizationPort;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.security.scope.ScopedAuthorizationRequest;
import com.sanad.platform.security.scope.ScopedAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Scoped-authorization adapter for compensation commands/reads (WS6 Task 3).
 *
 * <p>Binds the INDEPENDENT compensation capabilities to the scoped
 * authorization service. Compensation visibility is never implied by generic
 * employee visibility; backend authorization remains authoritative.</p>
 */
@Component
public class ScopedCompensationAuthorizationAdapter implements CompensationAuthorizationPort {

    public static final String CAPABILITY_COMPENSATION_MANAGE = "HRM.COMPENSATION.MANAGE";
    public static final String CAPABILITY_COMPENSATION_VIEW = "HRM.COMPENSATION.VIEW";
    public static final String RESOURCE_TYPE = "HR_COMPENSATION_PACKAGE";

    private final ScopedAuthorizationService scopedAuthorizationService;

    @Autowired
    public ScopedCompensationAuthorizationAdapter(ScopedAuthorizationService scopedAuthorizationService) {
        this.scopedAuthorizationService = Objects.requireNonNull(scopedAuthorizationService,
                "scopedAuthorizationService");
    }

    @Override
    public void requireManage(HrCommandContext ctx, UUID packageId) {
        require(ctx, packageId, CAPABILITY_COMPENSATION_MANAGE);
    }

    @Override
    public void requireView(HrCommandContext ctx, UUID employmentId) {
        require(ctx, employmentId, CAPABILITY_COMPENSATION_VIEW);
    }

    private void require(HrCommandContext ctx, UUID resourceId, String capability) {
        HrAuthorizationResourceContext resource = new HrAuthorizationResourceContext(
                ctx.tenantId(), RESOURCE_TYPE, resourceId, null, ctx.employmentId(), null,
                null, null, null, "COMPENSATION", null);
        ScopedAuthorizationRequest request = new ScopedAuthorizationRequest(
                ctx.tenantId(), ctx.actorUserId(), capability, resource, Instant.now());
        var decision = scopedAuthorizationService.authorize(request);
        if (decision == null || !decision.allowed()) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability
                    + " denied for the requested compensation scope");
        }
    }
}
