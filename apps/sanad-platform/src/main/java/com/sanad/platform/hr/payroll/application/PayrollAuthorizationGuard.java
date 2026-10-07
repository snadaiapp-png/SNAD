package com.sanad.platform.hr.payroll.application;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Fail-closed payroll authorization boundary.
 *
 * <p>Authority is capability-based and tenant/user scoped through the central
 * CapabilityEvaluationService. Role names are never treated as authority.</p>
 */
@Component
public class PayrollAuthorizationGuard {

    public static final String VIEW = "HRM.PAYROLL.VIEW";
    public static final String CALCULATE = "HRM.PAYROLL.CALCULATE";
    public static final String REVIEW = "HRM.PAYROLL.REVIEW";
    public static final String APPROVE = "HRM.PAYROLL.APPROVE";
    public static final String EXPORT = "HRM.PAYROLL.EXPORT";

    private final CapabilityEvaluationService capabilityEvaluationService;

    public PayrollAuthorizationGuard(CapabilityEvaluationService capabilityEvaluationService) {
        this.capabilityEvaluationService =
                Objects.requireNonNull(capabilityEvaluationService, "capabilityEvaluationService");
    }

    public void requireTransition(HrAuthenticatedContext actor, PayrollLifecycle target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");

        String capability = switch (target) {
            case CALCULATED, CANCELLED -> CALCULATE;
            case REVIEWED -> REVIEW;
            case APPROVED -> APPROVE;
            case EXPORTED -> EXPORT;
            case DRAFT -> throw denied("HRM.PAYROLL.INVALID_TRANSITION");
        };

        require(actor, capability);
    }

    public void requireRecalculation(HrAuthenticatedContext actor) {
        require(actor, CALCULATE);
    }

    private void require(HrAuthenticatedContext actor, String capability) {
        AccessDecisionResponse decision = capabilityEvaluationService.evaluate(
                actor.tenantId(),
                actor.actorUserId(),
                capability,
                null);

        if (decision == null || !decision.allowed()) {
            throw denied(capability);
        }
    }

    private static AccessDeniedException denied(String capability) {
        return new AccessDeniedException(
                "HRM_PAYROLL_AUTHORIZATION_DENIED: required capability " + capability);
    }
}
