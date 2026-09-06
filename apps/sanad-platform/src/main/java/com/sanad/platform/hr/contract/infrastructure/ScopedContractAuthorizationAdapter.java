package com.sanad.platform.hr.contract.infrastructure;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.contract.application.ContractAuthorizationPort;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.security.scope.ScopedAuthorizationRequest;
import com.sanad.platform.security.scope.ScopedAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Scoped-authorization adapter for contract commands (WS6 Task 2).
 *
 * <p>Binds the independent contract capabilities to the scoped authorization
 * service. The backend authorization (coarse capability + canonical resource
 * scope) remains authoritative; UI checks are never trusted.</p>
 */
@Component
public class ScopedContractAuthorizationAdapter implements ContractAuthorizationPort {

    public static final String CAPABILITY_CONTRACT_MANAGE = "HRM.CONTRACT.MANAGE";
    public static final String CAPABILITY_CONTRACT_VIEW = "HRM.CONTRACT.VIEW";
    public static final String RESOURCE_TYPE = "HR_EMPLOYMENT_CONTRACT";

    private final ScopedAuthorizationService scopedAuthorizationService;

    @Autowired
    public ScopedContractAuthorizationAdapter(ScopedAuthorizationService scopedAuthorizationService) {
        this.scopedAuthorizationService = Objects.requireNonNull(scopedAuthorizationService,
                "scopedAuthorizationService");
    }

    @Override
    public void requireManage(HrCommandContext ctx, UUID contractId) {
        require(ctx, contractId, CAPABILITY_CONTRACT_MANAGE);
    }

    @Override
    public void requireView(HrCommandContext ctx, UUID contractId) {
        require(ctx, contractId, CAPABILITY_CONTRACT_VIEW);
    }

    private void require(HrCommandContext ctx, UUID contractId, String capability) {
        HrAuthorizationResourceContext resource = new HrAuthorizationResourceContext(
                ctx.tenantId(), RESOURCE_TYPE, contractId, null, ctx.employmentId(), null,
                null, null, null, "OPERATIONAL", null);
        ScopedAuthorizationRequest request = new ScopedAuthorizationRequest(
                ctx.tenantId(), ctx.actorUserId(), capability, resource, Instant.now());
        var decision = scopedAuthorizationService.authorize(request);
        if (decision == null || !decision.allowed()) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability
                    + " denied for the requested contract scope");
        }
    }
}
