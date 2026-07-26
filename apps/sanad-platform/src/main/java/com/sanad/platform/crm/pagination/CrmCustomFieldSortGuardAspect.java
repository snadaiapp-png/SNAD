package com.sanad.platform.crm.pagination;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejects a custom-field boolean status sort before SQL construction.
 *
 * <p>The shared PageRequest vocabulary contains {@code status}, but the custom
 * field storage column is boolean. This endpoint therefore supports its typed
 * timestamp/name sorts and fails closed for status rather than relying on an
 * unsafe cross-type PostgreSQL comparison.</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrmCustomFieldSortGuardAspect {

    private final CapabilityAuthorizationAspect authorization;

    public CrmCustomFieldSortGuardAspect(CapabilityAuthorizationAspect authorization) {
        this.authorization = authorization;
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listCustomFields(..))")
    public Object rejectUnsupportedBooleanStatusSort(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = request(joinPoint);
        if (request == null || !"status".equals(request.getParameter("sort"))) {
            return joinPoint.proceed();
        }

        authorize(joinPoint);
        throw new CrmContractException(
                CrmErrorCode.VALIDATION_ERROR,
                "Sort field 'status' is not supported for custom fields; use updatedAt, createdAt, displayName, or name.");
    }

    private void authorize(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            throw new IllegalStateException("CRM custom-field sort guard requires a method signature");
        }
        RequireCapability policy = signature.getMethod().getAnnotation(RequireCapability.class);
        if (policy == null) {
            throw new IllegalStateException("CRM custom-field list endpoint lacks RequireCapability");
        }
        authorization.checkCapability(joinPoint, policy);
    }

    private HttpServletRequest request(ProceedingJoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof HttpServletRequest value) return value;
        }
        return null;
    }
}
