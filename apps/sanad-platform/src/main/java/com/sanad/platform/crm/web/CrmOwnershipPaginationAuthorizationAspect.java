package com.sanad.platform.crm.web;

import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import com.sanad.platform.security.authorization.RequireCapability;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures capability authorization executes before the pagination aspect may
 * return a database-backed page without invoking the original controller body.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class CrmOwnershipPaginationAuthorizationAspect {

    private final CapabilityAuthorizationAspect authorization;

    public CrmOwnershipPaginationAuthorizationAspect(
            CapabilityAuthorizationAspect authorization) {
        this.authorization = authorization;
    }

    @Before("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listTeams(..))"
            + " || execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listQueues(..))"
            + " || execution(* com.sanad.platform.crm.web.CrmOwnershipTransferController.listTransfers(..))"
            + " || execution(* com.sanad.platform.crm.web.CrmOwnershipAssignmentController.listRules(..))")
    public void authorize(JoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            throw new IllegalStateException("Ownership pagination requires a method signature");
        }
        RequireCapability policy = signature.getMethod().getAnnotation(RequireCapability.class);
        if (policy == null) {
            throw new IllegalStateException("Ownership pagination endpoint lacks RequireCapability");
        }
        authorization.checkCapability(joinPoint, policy);
    }
}
