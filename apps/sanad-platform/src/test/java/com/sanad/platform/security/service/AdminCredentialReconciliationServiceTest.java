package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.security.ratelimit.LoginRateLimiter;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCredentialReconciliationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private SessionVersionCache sessionVersionCache;

    private SecurityProperties securityProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                securityProperties,
                loginRateLimiter,
                sessionVersionCache);
    }

    @Test
    void reconciliationIsDisabledByDefault() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        assertThat(securityProperties.isCredentialReconciliationEnabled()).isFalse();
        assertThatThrownBy(() -> authService.reconcileCredential(
                tenantId, userId, "governed-secret", actorUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        verify(userRepository, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void enabledReconciliationRotatesCredentialAndRevokesAllSessions() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        User user = new User(tenantId, "qa@example.test", "QA", UserStatus.ACTIVE);
        user.setPasswordHash("old-hash");
        user.setMustChangePassword(true);
        user.setSessionVersion(7L);

        securityProperties.setCredentialReconciliationEnabled(true);
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("governed-secret")).thenReturn("new-hash");
        when(refreshTokenRepository.revokeAllActive(tenantId, userId)).thenReturn(2);
        when(passwordResetTokenRepository.revokeAllActive(tenantId, userId)).thenReturn(1);

        authService.reconcileCredential(tenantId, userId, "governed-secret", actorUserId);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getSessionVersion()).isEqualTo(8L);
        assertThat(user.getPasswordSetBy()).isEqualTo("admin-reconcile:" + actorUserId);
        assertThat(user.getPasswordSetAt()).isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllActive(tenantId, userId);
        verify(passwordResetTokenRepository).revokeAllActive(tenantId, userId);
        verify(sessionVersionCache).invalidate(tenantId, userId);
    }
}
