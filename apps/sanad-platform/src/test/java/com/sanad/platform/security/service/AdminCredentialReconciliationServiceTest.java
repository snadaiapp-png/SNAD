package com.sanad.platform.security.service;

import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.filter.SessionVersionCache;
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

class AdminCredentialReconciliationServiceTest {

    @Test
    void reconcilesExistingCredentialAndRevokesSessionMaterial() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = new User(tenantId, "g2-user@example.invalid", "G2 User", UserStatus.ACTIVE);
        user.setPasswordHash("old-hash");
        user.setMustChangePassword(true);
        long previousSessionVersion = user.getSessionVersion();

        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        SessionVersionCache sessionCache = mock(SessionVersionCache.class);

        when(users.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(encoder.encode("replacement-value")).thenReturn("new-hash");

        AdminCredentialReconciliationService service = new AdminCredentialReconciliationService(
                users, refreshTokens, resetTokens, encoder, sessionCache);

        service.reconcileCredential(tenantId, userId, "replacement-value", actorId);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPasswordSetBy()).isEqualTo("admin-reconcile:" + actorId);
        assertThat(user.getPasswordSetAt()).isNotNull();
        assertThat(user.getSessionVersion()).isEqualTo(previousSessionVersion + 1);
        verify(users).save(user);
        verify(sessionCache).invalidate(tenantId, userId);
        verify(refreshTokens).revokeAllActive(tenantId, userId);
        verify(resetTokens).revokeAllActive(tenantId, userId);
    }

    @Test
    void refusesInactiveTargetWithoutMutation() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User(tenantId, "inactive@example.invalid", "Inactive", UserStatus.SUSPENDED);
        user.setPasswordHash("old-hash");

        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        SessionVersionCache sessionCache = mock(SessionVersionCache.class);
        when(users.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));

        AdminCredentialReconciliationService service = new AdminCredentialReconciliationService(
                users, refreshTokens, resetTokens, encoder, sessionCache);

        assertThatThrownBy(() -> service.reconcileCredential(
                tenantId, userId, "replacement-value", UUID.randomUUID()))
                .isInstanceOf(AccountInactiveException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(users, never()).save(user);
        verify(refreshTokens, never()).revokeAllActive(tenantId, userId);
        verify(resetTokens, never()).revokeAllActive(tenantId, userId);
    }

    @Test
    void refusesTargetOutsideAuthenticatedTenantScope() {
        UUID authenticatedTenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        SessionVersionCache sessionCache = mock(SessionVersionCache.class);
        when(users.findByTenantIdAndId(authenticatedTenantId, targetUserId)).thenReturn(Optional.empty());

        AdminCredentialReconciliationService service = new AdminCredentialReconciliationService(
                users, refreshTokens, resetTokens, encoder, sessionCache);

        assertThatThrownBy(() -> service.reconcileCredential(
                authenticatedTenantId, targetUserId, "replacement-value", UUID.randomUUID()))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
        verify(refreshTokens, never()).revokeAllActive(authenticatedTenantId, targetUserId);
        verify(resetTokens, never()).revokeAllActive(authenticatedTenantId, targetUserId);
    }
}
