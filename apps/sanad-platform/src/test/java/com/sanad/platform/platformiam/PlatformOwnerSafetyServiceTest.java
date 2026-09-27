package com.sanad.platform.platformiam;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
import com.sanad.platform.platformiam.exception.LastPlatformOwnerException;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.repository.PlatformRoleMetadataRepository;
import com.sanad.platform.platformiam.service.PlatformOwnerSafetyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformOwnerSafetyServiceTest {

    private static final UUID CONTROL_TENANT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ONE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_TWO =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OWNER_ROLE =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NON_OWNER_ROLE =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-27T03:00:00Z");

    @ParameterizedTest(name = "last owner cannot transition to {0}")
    @ValueSource(strings = {"SUSPENDED", "LOCKED", "DISABLED"})
    void lastOwnerMembershipDeactivationIsRejected(String requestedState) {
        Fixture fixture = fixture(List.of(membership(OWNER_ONE)));
        assertThat(requestedState).isIn("SUSPENDED", "LOCKED", "DISABLED");

        assertThatThrownBy(() -> fixture.service.assertMayDeactivateMembership(CONTROL_TENANT, OWNER_ONE))
                .isInstanceOf(LastPlatformOwnerException.class)
                .satisfies(error -> assertThat(((LastPlatformOwnerException) error).reasonCode())
                        .isEqualTo("LAST_PLATFORM_OWNER"));
    }

    @Test
    void lastOwnerRoleRemovalIsRejected() {
        Fixture fixture = fixture(List.of(membership(OWNER_ONE)));
        when(fixture.roleMetadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, OWNER_ROLE))
                .thenReturn(Optional.of(ownerRoleMetadata()));

        assertThatThrownBy(() -> fixture.service.assertMayRemoveOwnerRole(
                CONTROL_TENANT, OWNER_ONE, OWNER_ROLE))
                .isInstanceOf(LastPlatformOwnerException.class)
                .satisfies(error -> assertThat(((LastPlatformOwnerException) error).reasonCode())
                        .isEqualTo("LAST_PLATFORM_OWNER"));
    }

    @Test
    void oneOwnerMayBeDeactivatedWhenAnotherEffectiveActiveOwnerRemains() {
        Fixture fixture = fixture(List.of(membership(OWNER_ONE), membership(OWNER_TWO)));

        assertThatCode(() -> fixture.service.assertMayDeactivateMembership(CONTROL_TENANT, OWNER_ONE))
                .doesNotThrowAnyException();
    }

    @Test
    void oneOwnerRoleMayBeRemovedWhenAnotherEffectiveActiveOwnerRemains() {
        Fixture fixture = fixture(List.of(membership(OWNER_ONE), membership(OWNER_TWO)));
        when(fixture.roleMetadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, OWNER_ROLE))
                .thenReturn(Optional.of(ownerRoleMetadata()));

        assertThatCode(() -> fixture.service.assertMayRemoveOwnerRole(
                CONTROL_TENANT, OWNER_ONE, OWNER_ROLE))
                .doesNotThrowAnyException();
    }

    @Test
    void removingNonOwnerRoleDoesNotApplyOwnerInvariant() {
        Fixture fixture = fixture(List.of(membership(OWNER_ONE)));
        when(fixture.roleMetadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, NON_OWNER_ROLE))
                .thenReturn(Optional.of(new PlatformRoleMetadata(
                        CONTROL_TENANT, NON_OWNER_ROLE, PlatformRoleMetadata.RoleType.SYSTEM,
                        true, false, NOW, NOW)));

        assertThatCode(() -> fixture.service.assertMayRemoveOwnerRole(
                CONTROL_TENANT, OWNER_ONE, NON_OWNER_ROLE))
                .doesNotThrowAnyException();
    }

    private static Fixture fixture(List<PlatformMembership> effectiveOwners) {
        PlatformMembershipRepository memberships = mock(PlatformMembershipRepository.class);
        PlatformRoleMetadataRepository roleMetadata = mock(PlatformRoleMetadataRepository.class);
        when(memberships.lockActiveMembershipsByRoleCode(CONTROL_TENANT, "PLATFORM_OWNER"))
                .thenReturn(effectiveOwners);
        return new Fixture(
                memberships,
                roleMetadata,
                new PlatformOwnerSafetyService(memberships, roleMetadata));
    }

    private static PlatformMembership membership(UUID userId) {
        return new PlatformMembership(
                UUID.randomUUID(), CONTROL_TENANT, userId, PlatformMembershipStatus.ACTIVE,
                NOW.minusSeconds(600), NOW.minusSeconds(300), null, null, null,
                OWNER_ONE, OWNER_ONE, null, NOW.minusSeconds(600), NOW.minusSeconds(60));
    }

    private static PlatformRoleMetadata ownerRoleMetadata() {
        return new PlatformRoleMetadata(
                CONTROL_TENANT, OWNER_ROLE, PlatformRoleMetadata.RoleType.SYSTEM,
                true, true, NOW, NOW);
    }

    private record Fixture(
            PlatformMembershipRepository memberships,
            PlatformRoleMetadataRepository roleMetadata,
            PlatformOwnerSafetyService service) {
    }
}
