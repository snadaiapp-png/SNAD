package com.sanad.platform.security.notification;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.domain.PasswordResetToken;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 behavioral audit contract for administrative set-password link
 * issuance: USER_RESET_LINK_ISSUED with metadata-only payload (channel,
 * single-use, ttl, issuer) — never the raw link value or its secret — and no
 * false SUCCESS when delivery fails.
 */
class AdministrativeResetLinkPhase7AuditBehaviorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private SecurityNotificationService notificationService;
    private PlatformAuditWriter audit;
    private PasswordRecoveryNotificationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        notificationService = mock(SecurityNotificationService.class);
        audit = mock(PlatformAuditWriter.class);
        coordinator = new PasswordRecoveryNotificationCoordinator(
                userRepository, tokenRepository, notificationService);
        coordinator.setCredentialAuditWriter(audit);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void administrativeIssuanceEmitsCanonicalEventWithExactSafePayloadKeys() {
        String rawLink = coordinator.createAdministrativeResetLink(
                TENANT_ID, USER_ID, "ar", "127.0.0.1", ACTOR_ID);

        assertThat(rawLink).isNotBlank();
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).writeSuccess(eq(TENANT_ID), eq(ACTOR_ID), eq(TENANT_ID),
                eq("USER_RESET_LINK_ISSUED"), eq("USER"), eq(USER_ID.toString()),
                any(), any(), after.capture(), any(), any(Instant.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) after.getValue();
        assertThat(payload.keySet()).containsExactlyInAnyOrder(
                "userId", "deliveryChannel", "singleUse", "ttlMinutes", "issuedBy");
        assertThat(payload).containsEntry("issuedBy", "ADMINISTRATOR").containsEntry("singleUse", true);
        // The single-use link value must never enter the audit payload.
        assertThat(String.valueOf(payload)).doesNotContain(rawLink);
    }

    @Test
    void deliveryFailureEmitsNoFalseSuccessAudit() {
        doThrow(new IllegalStateException("delivery unavailable"))
                .when(notificationService).deliverResetLink(any(), anyString(), anyString(), eq(true));

        assertThatThrownBy(() -> coordinator.createAdministrativeResetLink(
                TENANT_ID, USER_ID, "ar", "127.0.0.1", ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(audit, never()).writeSuccess(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any(), any(), any());
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }
}
