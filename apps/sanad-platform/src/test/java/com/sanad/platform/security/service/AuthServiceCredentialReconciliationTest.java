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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceCredentialReconciliationTest {

    @Test
    void reconciliationRotatesCredentialRevokesSessionsAndAuditsActor() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        SessionVersionCache sessionCache = mock(SessionVersionCache.class);

        AuthService service = new AuthService(
                users,
                refreshTokens,
                resetTokens,
                jwt,
                encoder,
                new SecurityProperties(),
                limiter,
                sessionCache);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = new User(tenantId, "g2-employee@example.invalid", "G2 Employee QA", UserStatus.ACTIVE);
        user.setPasswordHash("encoded-old");
        user.setMustChangePassword(true);
        user.setSessionVersion(7L);

        when(users.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(encoder.encode("governed-secret-123")).thenReturn("encoded-new");

        Method method = AuthService.class.getDeclaredMethod(
                "reconcileCredential",
                UUID.class,
                UUID.class,
                String.class,
                UUID.class);
        method.invoke(service, tenantId, userId, "governed-secret-123", actorId);

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getSessionVersion()).isEqualTo(8L);
        assertThat(user.getPasswordSetAt()).isNotNull();
        assertThat(user.getPasswordSetBy()).isEqualTo("admin-reconcile:" + actorId);

        verify(users).save(user);
        verify(sessionCache).invalidate(tenantId, userId);
        verify(refreshTokens).revokeAllActive(tenantId, userId);
        verify(resetTokens).revokeAllActive(tenantId, userId);
    }
}
