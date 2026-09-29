package com.sanad.platform.platformiam;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.dto.CreatePlatformTemporaryAccessRequest;
import com.sanad.platform.platformiam.dto.PlatformUserResponse;
import com.sanad.platform.platformiam.service.PlatformAuthorizationService;
import com.sanad.platform.platformiam.service.PlatformTemporaryAccessService;
import com.sanad.platform.platformiam.service.PlatformUserService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformTemporaryAccessServiceTest {
    private static final UUID TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CAP = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID GRANT = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformAuthorizationService authorization = mock(PlatformAuthorizationService.class);
    private final PlatformUserService users = mock(PlatformUserService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final Authentication actor = actor(TENANT);
    private final PlatformTemporaryAccessService service = new PlatformTemporaryAccessService(
            jdbc, new ControlPlaneAccessGuard(TENANT.toString()), authorization, users, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(authorization.evaluate(eq(actor), anyString())).thenAnswer(invocation ->
                new AccessDecisionResponse(TENANT, ACTOR, null, invocation.getArgument(1),
                        true, "ROLE", null, null));
        when(users.get(actor, TARGET)).thenReturn(new PlatformUserResponse(TARGET, "user@example.test",
                "User", UserStatus.ACTIVE, PlatformMembershipStatus.ACTIVE, null, NOW));
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<String>>any(), eq(CAP)))
                .thenReturn(List.of("PLATFORM.USER.READ"));
    }

    @Test
    void grantUsesCanonicalTableTrustedActorAndRequiredExpiry() {
        var result = service.grant(actor, TARGET, request(NOW.plusSeconds(60), " coverage "));
        assertThat(result.userId()).isEqualTo(TARGET);
        assertThat(result.grantedBy()).isEqualTo(ACTOR);
        assertThat(result.effectiveTo()).isEqualTo(NOW.plusSeconds(60));
        assertThat(result.reason()).isEqualTo("coverage");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(jdbc).update(contains("INSERT INTO access_scope_grants"),
                eq(result.id()), eq(TENANT), eq(TARGET), eq(CAP), eq("coverage"), eq(ACTOR),
                eq(java.sql.Timestamp.from(NOW)), eq(java.sql.Timestamp.from(NOW.plusSeconds(60))));
        verify(audit).success(eq(actor), eq(TENANT), eq("PLATFORM_TEMPORARY_ACCESS_GRANTED"),
                eq("PLATFORM_USER"), eq(TARGET.toString()), eq("coverage"), isNull(), eq(result));
    }

    @Test
    void expiryAtNowOrInPastOrMissingIsRejected() {
        for (Instant expiry : new Instant[] { null, NOW, NOW.minusSeconds(1) }) {
            assertThatThrownBy(() -> service.grant(actor, TARGET, request(expiry, "coverage")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void blankOrOversizedReasonIsRejected() {
        for (String reason : new String[] { null, " ", "x".repeat(501) }) {
            assertThatThrownBy(() -> service.grant(actor, TARGET, request(NOW.plusSeconds(60), reason)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void tenantCallerCannotReadOrGrantControlPlaneAccess() {
        Authentication foreign = actor(UUID.randomUUID());
        assertThatThrownBy(() -> service.list(foreign, TARGET)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.grant(foreign, TARGET, request(NOW.plusSeconds(60), "coverage")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc, users, audit);
    }

    @Test
    void absentCapabilityDecisionDeniesDirectServiceInvocation() {
        when(authorization.evaluate(actor, "PLATFORM.PERMISSION.MANAGE")).thenReturn(null);
        assertThatThrownBy(() -> service.grant(actor, TARGET, request(NOW.plusSeconds(60), "coverage")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, audit);
    }

    @Test
    void foreignOrNonPlatformTargetIsRejectedBeforePersistence() {
        when(users.get(actor, TARGET)).thenThrow(new AccessResourceNotFoundException("Not found"));
        assertThatThrownBy(() -> service.grant(actor, TARGET, request(NOW.plusSeconds(60), "coverage")))
                .isInstanceOf(AccessResourceNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void revocationIsScopedToTenantUserAndDirectGrant() {
        when(jdbc.update(anyString(), eq(TENANT), eq(TARGET), eq(GRANT))).thenReturn(1);
        service.revoke(actor, TARGET, GRANT, "coverage ended");
        verify(jdbc).update(contains("AND role_id IS NULL AND is_direct_exception = TRUE"),
                eq(TENANT), eq(TARGET), eq(GRANT));
        verify(audit).success(eq(actor), eq(TENANT), eq("PLATFORM_TEMPORARY_ACCESS_REVOKED"),
                eq("PLATFORM_USER"), eq(TARGET.toString()), eq("coverage ended"), any(), any());
    }

    @Test
    void unknownOrOtherUsersGrantCannotBeRevoked() {
        assertThatThrownBy(() -> service.revoke(actor, TARGET, GRANT, "coverage ended"))
                .isInstanceOf(AccessResourceNotFoundException.class);
        verifyNoInteractions(audit);
    }

    private static CreatePlatformTemporaryAccessRequest request(Instant expiry, String reason) {
        return new CreatePlatformTemporaryAccessRequest(CAP, expiry, reason);
    }

    private static Authentication actor(UUID tenant) {
        var token = new UsernamePasswordAuthenticationToken("operator", "unused", List.of());
        token.setDetails(Map.of("tenant_id", tenant, "user_id", ACTOR));
        return token;
    }
}
