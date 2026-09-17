package com.sanad.platform.hr.onboarding.infrastructure;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.onboarding.application.OnboardingAuthorizationPort;
import com.sanad.platform.hr.onboarding.domain.OnboardingCapabilities;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.security.scope.ScopedAuthorizationRequest;
import com.sanad.platform.security.scope.ScopedAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Scoped, deny-by-default onboarding authorization adapter. */
@Component
public class ScopedOnboardingAuthorizationAdapter implements OnboardingAuthorizationPort {

    private static final String RESOURCE_TYPE = "HR_ONBOARDING";

    private final ScopedAuthorizationService scopedAuthorizationService;

    @Autowired
    public ScopedOnboardingAuthorizationAdapter(ScopedAuthorizationService scopedAuthorizationService) {
        this.scopedAuthorizationService = Objects.requireNonNull(scopedAuthorizationService, "scopedAuthorizationService");
    }

    @Override
    public void requirePlanManage(HrCommandContext ctx, UUID resourceId) {
        require(ctx, resourceId, OnboardingCapabilities.PLAN_MANAGE);
    }

    @Override
    public void requireTaskComplete(HrCommandContext ctx, UUID taskId, UUID assigneeUserId) {
        Objects.requireNonNull(ctx, "ctx");
        if (assigneeUserId != null && assigneeUserId.equals(ctx.actorUserId())) {
            return;
        }
        require(ctx, taskId, OnboardingCapabilities.TASK_COMPLETE);
    }

    @Override
    public void requireTaskWaive(HrCommandContext ctx, UUID taskId) {
        require(ctx, taskId, OnboardingCapabilities.TASK_WAIVE);
    }

    private void require(HrCommandContext ctx, UUID resourceId, String capability) {
        Objects.requireNonNull(ctx, "ctx");
        HrAuthorizationResourceContext resource = new HrAuthorizationResourceContext(
                ctx.tenantId(), RESOURCE_TYPE, resourceId, null, ctx.employmentId(),
                null, null, null, null, "OPERATIONAL", null);
        ScopedAuthorizationRequest request = new ScopedAuthorizationRequest(
                ctx.tenantId(), ctx.actorUserId(), capability, resource, Instant.now());
        var decision = scopedAuthorizationService.authorize(request);
        if (decision == null || !decision.allowed()) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability);
        }
    }
}
