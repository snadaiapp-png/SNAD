package com.sanad.platform.security.service;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.dto.ForgotPasswordRequest;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.security.ratelimit.LoginRateLimiter;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 behavioral audit contract for credential administration:
 * USER_CREDENTIAL_INITIALIZED and USER_RESET_LINK_ISSUED are emitted through
 * the centralized {@link PlatformAuditWriter} with metadata-only payloads —
 * never the temporary access secret, its hash, the raw single-use token, or
 * the link URL.
 */
class CredentialAdministrationPhase7AuditBehaviorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private PasswordEncoder passwordEncoder;
    private PlatformAuditWriter audit;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        audit = mock(PlatformAuditWriter.class);
        service = new AuthService(
                userRepository, refreshTokenRepository, passwordResetTokenRepository,
                mock(JwtTokenProvider.class), passwordEncoder, new SecurityProperties(),
                mock(LoginRateLimiter.class), mock(SessionVersionCache.class));
        service.setCredentialAuditWriter(audit);
    }

    @Test
    void initializeCredentialEmitsCanonicalEventWithExactSafePayloadKeys() {
        User user = activePasswordlessUser();
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(contains("Temp"))).thenReturn("unit-test-encoded-hash");

        service.initializeCredential(TENANT_ID, USER_ID, "Temp-Access-99!", ACTOR_ID);

        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).writeSuccess(eq(TENANT_ID), eq(ACTOR_ID), eq(TENANT_ID),
                eq("USER_CREDENTIAL_INITIALIZED"), eq("USER"), eq(USER_ID.toString()),
                any(), any(), after.capture(), any(), any(Instant.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) after.getValue();
        assertThat(payload.keySet()).containsExactlyInAnyOrder(
                "userId", "temporaryAccessProvisioned", "rotationRequired", "setBy");
        assertThat(payload).containsEntry("rotationRequired", true).containsEntry("setBy", "admin-initialize");
    }

    @Test
    void initializeCredentialRefusalForExistingCredentialEmitsNoAudit() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn("already-set-hash");
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.initializeCredential(TENANT_ID, USER_ID, "Another-Temp-1!", ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verify(audit, never()).writeSuccess(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void actionableResetIssuanceEmitsCanonicalEventWithoutTokenMaterial() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getTenantId()).thenReturn(TENANT_ID);
        when(userRepository.findAllByEmail("solo@example.com")).thenReturn(List.of(user));

        String rawToken = service.initiatePasswordReset(
                new ForgotPasswordRequest("solo@example.com"), "127.0.0.1");

        assertThat(rawToken).as("actionable issuance returns the raw token for delivery").isNotBlank();
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).writeSuccess(eq(TENANT_ID), eq(null), eq(TENANT_ID),
                eq("USER_RESET_LINK_ISSUED"), eq("USER"), eq(USER_ID.toString()),
                any(), any(), after.capture(), any(), any(Instant.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) after.getValue();
        assertThat(payload.keySet()).containsExactlyInAnyOrder(
                "userId", "deliveryChannel", "singleUse", "ttlMinutes");
        assertThat(payload).containsEntry("singleUse", true).containsEntry("deliveryChannel", "email");
        // The raw single-use value must never enter the audit payload.
        assertThat(String.valueOf(payload)).doesNotContain(rawToken);
    }

    @Test
    void ambiguousOrInactiveResetRequestEmitsNoIssuanceAudit() {
        User first = activeUserOnTenant(TENANT_ID);
        User second = activeUserOnTenant(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        when(userRepository.findAllByEmail("shared@example.com")).thenReturn(List.of(first, second));

        String token = service.initiatePasswordReset(
                new ForgotPasswordRequest("shared@example.com"), "127.0.0.1");

        assertThat(token).isNull();
        verify(audit, never()).writeSuccess(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any(), any(), any());
    }

    private static User activePasswordlessUser() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn(null);
        when(user.getId()).thenReturn(USER_ID);
        return user;
    }

    private static User activeUserOnTenant(UUID tenantId) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getTenantId()).thenReturn(tenantId);
        when(user.getId()).thenReturn(UUID.randomUUID());
        return user;
    }
}
