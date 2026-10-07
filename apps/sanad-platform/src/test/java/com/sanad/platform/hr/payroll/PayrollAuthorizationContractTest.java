package com.sanad.platform.hr.payroll;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.payroll.application.PayrollLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G4-T6 RED-first authorization contract.
 *
 * <p>Payroll lifecycle authority is explicit capability authority. No role-name
 * shortcut and no implicit allow is permitted. REVIEW, APPROVE and EXPORT must
 * fail closed unless the caller holds the exact tenant-scoped capability.</p>
 */
class PayrollAuthorizationContractTest {

    private static final String GUARD_CLASS =
            "com.sanad.platform.hr.payroll.application.PayrollAuthorizationGuard";

    @Test
    void reviewFailsClosedWithoutExplicitReviewCapability() {
        assertDenied(PayrollLifecycle.REVIEWED, "HRM.PAYROLL.REVIEW");
    }

    @Test
    void approvalFailsClosedWithoutExplicitApproveCapability() {
        assertDenied(PayrollLifecycle.APPROVED, "HRM.PAYROLL.APPROVE");
    }

    @Test
    void exportFailsClosedWithoutExplicitExportCapability() {
        assertDenied(PayrollLifecycle.EXPORTED, "HRM.PAYROLL.EXPORT");
    }

    @Test
    void nullAuthorizationDecisionFailsClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HrAuthenticatedContext actor = actor(tenantId, userId);
        CapabilityEvaluationService evaluator = mock(CapabilityEvaluationService.class);

        Object guard = newGuard(evaluator);

        assertThatThrownBy(() -> invokeRequireTransition(guard, actor, PayrollLifecycle.APPROVED))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("HRM.PAYROLL.APPROVE");

        verify(evaluator).evaluate(tenantId, userId, "HRM.PAYROLL.APPROVE", null);
    }

    @Test
    void exactCapabilityAllowsGovernedTransition() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HrAuthenticatedContext actor = actor(tenantId, userId);
        CapabilityEvaluationService evaluator = mock(CapabilityEvaluationService.class);
        when(evaluator.evaluate(tenantId, userId, "HRM.PAYROLL.REVIEW", null))
                .thenReturn(decision(tenantId, userId, "HRM.PAYROLL.REVIEW", true, "ROLE_CAPABILITY_MATCH"));

        Object guard = newGuard(evaluator);

        assertThatCode(() -> invokeRequireTransition(guard, actor, PayrollLifecycle.REVIEWED))
                .doesNotThrowAnyException();

        verify(evaluator).evaluate(tenantId, userId, "HRM.PAYROLL.REVIEW", null);
    }

    private static void assertDenied(PayrollLifecycle target, String requiredCapability) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HrAuthenticatedContext actor = actor(tenantId, userId);
        CapabilityEvaluationService evaluator = mock(CapabilityEvaluationService.class);
        when(evaluator.evaluate(tenantId, userId, requiredCapability, null))
                .thenReturn(decision(tenantId, userId, requiredCapability, false, "NO_MATCHING_ACTIVE_ROLE"));

        Object guard = newGuard(evaluator);

        assertThatThrownBy(() -> invokeRequireTransition(guard, actor, target))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(requiredCapability);

        verify(evaluator).evaluate(tenantId, userId, requiredCapability, null);
    }

    private static Object newGuard(CapabilityEvaluationService evaluator) {
        Class<?> guardType = loadGuardType();
        assertThat(guardType)
                .as("G4-T6 requires a dedicated payroll authorization guard before lifecycle mutations")
                .isNotNull();
        try {
            Constructor<?> constructor = guardType.getDeclaredConstructor(CapabilityEvaluationService.class);
            constructor.setAccessible(true);
            return constructor.newInstance(evaluator);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "G4-T6 authorization guard must accept CapabilityEvaluationService explicitly", e);
        }
    }

    private static Class<?> loadGuardType() {
        try {
            return Class.forName(GUARD_CLASS);
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    private static void invokeRequireTransition(
            Object guard,
            HrAuthenticatedContext actor,
            PayrollLifecycle target) {
        try {
            Method method = guard.getClass().getDeclaredMethod(
                    "requireTransition", HrAuthenticatedContext.class, PayrollLifecycle.class);
            method.setAccessible(true);
            method.invoke(guard, actor, target);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "G4-T6 authorization guard must expose requireTransition(actor, target)", e);
        }
    }

    private static HrAuthenticatedContext actor(UUID tenantId, UUID userId) {
        return new HrAuthenticatedContext(
                tenantId,
                userId,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private static AccessDecisionResponse decision(
            UUID tenantId,
            UUID userId,
            String capability,
            boolean allowed,
            String reason) {
        return new AccessDecisionResponse(
                tenantId,
                userId,
                null,
                capability,
                allowed,
                reason,
                null,
                null);
    }
}
