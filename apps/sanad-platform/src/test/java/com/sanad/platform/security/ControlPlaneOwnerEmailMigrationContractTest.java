package com.sanad.platform.security;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ControlPlaneOwnerEmailMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20260828_1__canonicalize_control_plane_owner_email.sql";
    private static final String OWNER_ID = "00000000-0000-0000-0000-000000000010";
    private static final String CONTROL_PLANE_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String CANONICAL_EMAIL = "snad.ai.app@gmail.com";

    @Test
    void canonicalOwnerMigrationTargetsTheDeterministicControlPlaneOwner() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "A forward-only migration must canonicalize the project-owner identity");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains(OWNER_ID), "Migration must target the deterministic owner user id");
            assertTrue(sql.contains(CONTROL_PLANE_TENANT_ID), "Migration must stay scoped to the control-plane tenant");
            assertTrue(sql.contains(CANONICAL_EMAIL), "Migration must adopt the approved owner email");
            assertTrue(sql.contains("platform_admin = true"), "Canonical owner must retain platform-admin authority");
            assertTrue(sql.contains("status = 'ACTIVE'"), "Canonical owner must remain active");
            assertFalse(sql.contains("SET email = 'admin@snad.ai'"),
                    "The forward migration must never restore the legacy owner email");
        }
    }

    @Test
    void controlPlaneIdentityDoesNotRequireCustomerSubscriptionToAuthenticate() throws Exception {
        UUID controlTenant = UUID.fromString(CONTROL_PLANE_TENANT_ID);
        SubscriptionResolutionService subscriptions = mock(SubscriptionResolutionService.class);
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard(CONTROL_PLANE_TENANT_ID);
        AuthService authService = authService(subscriptions, guard);
        User controlPlaneUser = new User(controlTenant, "scp-smoke@snad.invalid", "SCP Smoke", UserStatus.ACTIVE);

        Method gate = AuthService.class.getDeclaredMethod("requireLoginEligibleSubscription", User.class);
        gate.setAccessible(true);

        assertDoesNotThrow(() -> gate.invoke(authService, controlPlaneUser));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void customerTenantWithoutEffectiveSubscriptionStillFailsClosed() throws Exception {
        UUID customerTenant = UUID.fromString("00000000-0000-0000-0000-000000000099");
        SubscriptionResolutionService subscriptions = mock(SubscriptionResolutionService.class);
        when(subscriptions.findEffectiveSubscription(customerTenant)).thenReturn(Optional.empty());
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard(CONTROL_PLANE_TENANT_ID);
        AuthService authService = authService(subscriptions, guard);
        User customerUser = new User(customerTenant, "customer@snad.invalid", "Customer", UserStatus.ACTIVE);

        Method gate = AuthService.class.getDeclaredMethod("requireLoginEligibleSubscription", User.class);
        gate.setAccessible(true);

        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> gate.invoke(authService, customerUser));
        assertInstanceOf(AccountInactiveException.class, thrown.getCause());
        verify(subscriptions).findEffectiveSubscription(customerTenant);
    }

    private static AuthService authService(
            SubscriptionResolutionService subscriptions,
            ControlPlaneAccessGuard guard) {
        return new AuthService(
                null,
                null,
                null,
                null,
                null,
                new SecurityProperties(),
                null,
                null,
                null,
                subscriptions,
                guard);
    }
}
