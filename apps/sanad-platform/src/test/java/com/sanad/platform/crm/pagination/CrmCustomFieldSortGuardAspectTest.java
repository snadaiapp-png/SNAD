package com.sanad.platform.crm.pagination;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.web.CrmContractController;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmCustomFieldSortGuardAspectTest {

    @Test
    void rejectsBooleanStatusSortAfterCapabilityAuthorization() throws Throwable {
        CapabilityAuthorizationAspect authorization = mock(CapabilityAuthorizationAspect.class);
        doNothing().when(authorization).checkCapability(any(), any());
        CrmCustomFieldSortGuardAspect guard = new CrmCustomFieldSortGuardAspect(authorization);
        ProceedingJoinPoint joinPoint = joinPoint("status");

        assertThatThrownBy(() -> guard.rejectUnsupportedBooleanStatusSort(joinPoint))
                .isInstanceOf(CrmContractException.class)
                .satisfies(error -> assertThat(((CrmContractException) error).code())
                        .isEqualTo(CrmErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("not supported for custom fields");

        verify(authorization).checkCapability(any(), any());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void allowsSupportedSortToReachKeysetAspect() throws Throwable {
        CapabilityAuthorizationAspect authorization = mock(CapabilityAuthorizationAspect.class);
        CrmCustomFieldSortGuardAspect guard = new CrmCustomFieldSortGuardAspect(authorization);
        ProceedingJoinPoint joinPoint = joinPoint("updatedAt");
        Object expected = new Object();
        when(joinPoint.proceed()).thenReturn(expected);

        assertThat(guard.rejectUnsupportedBooleanStatusSort(joinPoint)).isSameAs(expected);
        verify(joinPoint).proceed();
        verify(authorization, never()).checkCapability(any(), any());
    }

    private ProceedingJoinPoint joinPoint(String sort) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("sort", sort);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = CrmContractController.class.getMethod(
                "listCustomFields",
                Authentication.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{null, null, null, null, sort, null, request});
        return joinPoint;
    }
}
