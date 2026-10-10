package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.security.ratelimit.LoginRateLimiter;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialReconciliationServiceTest {

    @Test
    void reconcilesExistingCredentialAndRevokesAllSessionsWithinTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        SessionVersionCache sessionVersions = mock(SessionVersionCache.class);

        User target = new User(tenantId, "g2-employee@example.test", "G2 Employee", UserStatus.ACTIVE);
        target.setPasswordHash("encoded-old");
        target.setMustChangePassword(true);
        target.setSessionVersion(7);

        when(users.findByTenantIdAndId(tenantId, targetUserId)).thenReturn(Optional.of(target));
        when(encoder.encode("governed-secret")).thenReturn("encoded-governed");
        when(refreshTokens.revokeAllActive(tenantId, targetUserId)).thenReturn(2);
        when(resetTokens.revokeAllActive(tenantId, targetUserId)).thenReturn(1);

        AuthService service = new AuthService(
                users,
                refreshTokens,
                resetTokens,
                jwt,
                encoder,
                new SecurityProperties(),
                rateLimiter,
                sessionVersions);

        service.reconcileCredential(tenantId, targetUserId, "governed-secret", actorUserId);

        assertThat(target.getPasswordHash()).isEqualTo("encoded-governed");
        assertThat(target.isMustChangePassword()).isFalse();
        assertThat(target.getSessionVersion()).isEqualTo(8);
        assertThat(target.getPasswordSetAt()).isNotNull();
        assertThat(target.getPasswordSetBy()).isEqualTo("production-recovery:" + actorUserId);
        verify(users).save(target);
        verify(refreshTokens).revokeAllActive(tenantId, targetUserId);
        verify(resetTokens).revokeAllActive(tenantId, targetUserId);
        verify(sessionVersions).invalidate(tenantId, targetUserId);
    }
}
