package com.sanad.platform.security.service;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.security.ratelimit.LoginRateLimiter;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceCredentialReconciliationTest {

    @Test
    void reconcilesExistingActiveCredentialAndRevokesAllSessionsAndResetTokens() {
        Fixture fixture = new Fixture();
        UUID tenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        User user = new User(tenantId, "g2.employee@example.test", "G2 Employee", UserStatus.ACTIVE);
        user.setPasswordHash("old-hash");
        user.setMustChangePassword(true);
        user.setSessionVersion(7L);

        when(fixture.users.findByTenantIdAndId(tenantId, targetUserId)).thenReturn(Optional.of(user));
        when(fixture.passwordEncoder.encode("governed-secret")).thenReturn("new-hash");
        when(fixture.refreshTokens.revokeAllActive(tenantId, targetUserId)).thenReturn(2);
        when(fixture.resetTokens.revokeAllActive(tenantId, targetUserId)).thenReturn(1);

        fixture.service.reconcileCredential(tenantId, targetUserId, "governed-secret", actorUserId);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getSessionVersion()).isEqualTo(8L);
        assertThat(user.getPasswordSetAt()).isNotNull();
        assertThat(user.getPasswordSetBy()).isEqualTo("admin-reconcile:" + actorUserId);
        verify(fixture.users).save(user);
        verify(fixture.sessionVersions).invalidate(tenantId, targetUserId);
        verify(fixture.refreshTokens).revokeAllActive(tenantId, targetUserId);
        verify(fixture.resetTokens).revokeAllActive(tenantId, targetUserId);
    }

    @Test
    void refusesPasswordlessUserSoInitializationContractRemainsSeparate() {
        Fixture fixture = new Fixture();
        UUID tenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        User user = new User(tenantId, "g2.manager@example.test", "G2 Manager", UserStatus.ACTIVE);

        when(fixture.users.findByTenantIdAndId(tenantId, targetUserId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> fixture.service.reconcileCredential(
                tenantId, targetUserId, "governed-secret", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not initialized");

        verify(fixture.users, never()).save(user);
        verify(fixture.refreshTokens, never()).revokeAllActive(tenantId, targetUserId);
        verify(fixture.resetTokens, never()).revokeAllActive(tenantId, targetUserId);
    }

    private static final class Fixture {
        private final UserRepository users = mock(UserRepository.class);
        private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        private final SecurityProperties securityProperties = new SecurityProperties();
        private final LoginRateLimiter loginRateLimiter = mock(LoginRateLimiter.class);
        private final SessionVersionCache sessionVersions = mock(SessionVersionCache.class);
        private final TenantRepository tenants = mock(TenantRepository.class);
        private final SubscriptionResolutionService subscriptions = mock(SubscriptionResolutionService.class);
        private final ControlPlaneAccessGuard controlPlane = mock(ControlPlaneAccessGuard.class);

        private final AuthService service = new AuthService(
                users,
                refreshTokens,
                resetTokens,
                jwt,
                passwordEncoder,
                securityProperties,
                loginRateLimiter,
                sessionVersions,
                tenants,
                subscriptions,
                controlPlane);
    }
}
