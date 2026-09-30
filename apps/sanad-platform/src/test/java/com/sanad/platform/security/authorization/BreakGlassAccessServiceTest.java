package com.sanad.platform.security.authorization;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.evaluation.AuthorizationDecision;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.access.evaluation.DecisionSource;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideRepository;
import com.sanad.platform.access.override.UserPermissionOverrideService;
import com.sanad.platform.access.override.dto.CreateOverrideRequest;
import com.sanad.platform.access.override.dto.OverrideResponse;
import com.sanad.platform.access.relationship.RelationshipResolver;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 1 Task 15 — break-glass is time-boxed, reason-mandatory, audited via
 * the override service, and evaluated NORMALLY: an active direct DENY still
 * beats a break-glass ALLOW.
 */
class BreakGlassAccessServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    private UserPermissionOverrideService overrideService;
    private BreakGlassAccessService service;

    @BeforeEach
    void wire() {
        overrideService = mock(UserPermissionOverrideService.class);
        service = new BreakGlassAccessService(overrideService);
    }

    @Test
    void reasonShorterThanTwentyCharactersRejected() {
        assertThatThrownBy(() -> service.grantEmergencyOverride(
                tenantId, actorUserId, targetUserId, "CRM.ACCOUNT.READ",
                "too short", Instant.now().plusSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BREAK_GLASS_REASON_INSUFFICIENT");
    }

    @Test
    void windowLongerThanFourHoursRejected() {
        assertThatThrownBy(() -> service.grantEmergencyOverride(
                tenantId, actorUserId, targetUserId, "CRM.ACCOUNT.READ",
                "governed emergency recovery operation", Instant.now().plus(Duration.ofHours(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BREAK_GLASS_WINDOW_EXCEEDED");
    }

    @Test
    void successCreatesOrdinaryTenantAllAllowOverrideWithMarkerReason() {
        OverrideResponse response = new OverrideResponse(UUID.randomUUID(), targetUserId,
                "CRM.ACCOUNT.READ", "ALLOW", "TENANT_ALL", null,
                BreakGlassAccessService.REASON_MARKER + " governed emergency recovery",
                Instant.now(), Instant.now().plusSeconds(3600), 0);
        when(overrideService.create(eq(tenantId), eq(actorUserId), any(CreateOverrideRequest.class)))
                .thenReturn(response);

        OverrideResponse result = service.grantEmergencyOverride(
                tenantId, actorUserId, targetUserId, "CRM.ACCOUNT.READ",
                "governed emergency recovery", Instant.now().plusSeconds(3600));

        assertThat(result.effect()).isEqualTo("ALLOW");
        ArgumentCaptor<CreateOverrideRequest> captor =
                ArgumentCaptor.forClass(CreateOverrideRequest.class);
        org.mockito.Mockito.verify(overrideService)
                .create(eq(tenantId), eq(actorUserId), captor.capture());
        CreateOverrideRequest request = captor.getValue();
        assertThat(request.effect()).isEqualTo("ALLOW");
        assertThat(request.scopeType()).isEqualTo("TENANT_ALL");
        assertThat(request.reason()).startsWith(BreakGlassAccessService.REASON_MARKER);
        assertThat(request.validUntil()).isNotNull();
    }

    @Test
    void activeDirectDenyStillBeatsBreakGlassAllowInPipeline() {
        // Break-glass ALLOW row + active DENY row for the same capability:
        // the pipeline must still return DENY (EXPLICIT_DENY).
        AccessCapabilityService capabilityService = mock(AccessCapabilityService.class);
        AccessCapability capability = mock(AccessCapability.class);
        UUID capabilityId = UUID.randomUUID();
        when(capability.getId()).thenReturn(capabilityId);
        when(capability.getCode()).thenReturn("CRM.ACCOUNT.READ");
        when(capability.getStatus()).thenReturn(CapabilityStatus.ACTIVE);
        when(capabilityService.loadByCode("CRM.ACCOUNT.READ")).thenReturn(capability);

        UserPermissionOverrideRepository overrideRepository =
                mock(UserPermissionOverrideRepository.class);
        when(overrideRepository.findOverrides(tenantId, targetUserId, capabilityId))
                .thenReturn(List.of(
                        new UserPermissionOverrideRepository.ActiveOverride(
                                UUID.randomUUID(), "DENY", null, null,
                                Instant.now().minusSeconds(60), null),
                        new UserPermissionOverrideRepository.ActiveOverride(
                                UUID.randomUUID(), "ALLOW", "TENANT_ALL", null,
                                Instant.now().minusSeconds(60),
                                Instant.now().plusSeconds(3600))));

        CapabilityEvaluationService pipeline = new CapabilityEvaluationService(
                mock(UserRoleGrantService.class), mock(com.sanad.platform.access.role.RoleService.class),
                mock(RoleCapabilityService.class), capabilityService,
                mock(OrganizationRepository.class), overrideRepository,
                mock(RelationshipResolver.class), new MockEnvironment());

        AuthorizationDecision decision =
                pipeline.evaluateDetailed(tenantId, targetUserId, "CRM.ACCOUNT.READ", null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_DENY);
    }
}
