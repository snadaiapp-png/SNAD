package com.sanad.platform.user.access;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationAccessProjectionServiceTest {

    @Mock private ApplicationIamRegistryRepository registry;
    @Mock private UserApplicationAccessReadRepository accessRead;
    @Mock private UserRepository users;

    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ROLE = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void syntheticFutureApplicationIsDiscoveredWithoutUsersSourceKnowledge() {
        User target = new User(TENANT, "future.user@example.com", "Future User", UserStatus.ACTIVE);
        when(users.findByTenantIdAndId(TENANT, USER)).thenReturn(Optional.of(target));
        when(registry.findDiscoverable()).thenReturn(List.of(
                new ApplicationIamRegistration(
                        "FUTURE_LEDGER", "Future Ledger", "دفتر المستقبل", "ACTIVE", "1",
                        Set.of("FUTURE_LEDGER"), Set.of("TENANT"), Set.of("FUTURE_LEDGER.READ"),
                        Set.of(), Map.of("routeRoots", List.of("future-ledger"))))));
        when(accessRead.knownCapabilities()).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.effectiveCapabilityCodes(TENANT, USER)).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.activeRoleGrants(TENANT, USER)).thenReturn(List.of(
                new UserAccessResponse(UUID.randomUUID(), TENANT, USER, ROLE, "FUTURE_LEDGER_USER",
                        null, UserGrantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)));
        when(accessRead.capabilityCodesByRoleIds(TENANT, Set.of(ROLE)))
                .thenReturn(Map.of(ROLE, Set.of("FUTURE_LEDGER.READ")));

        var service = new UserApplicationAccessProjectionService(registry, accessRead, users);

        List<ApplicationAccessProjection> result = service.project(TENANT, USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).applicationCode()).isEqualTo("FUTURE_LEDGER");
        assertThat(result.get(0).effectiveAccess()).isTrue();
        assertThat(result.get(0).effectiveCapabilities()).containsExactly("FUTURE_LEDGER.READ");
        assertThat(result.get(0).assignedRoles()).containsExactly("FUTURE_LEDGER_USER");
    }

    @Test
    void unsupportedScopeFailsClosed() {
        User target = new User(TENANT, "future.user@example.com", "Future User", UserStatus.ACTIVE);
        when(users.findByTenantIdAndId(TENANT, USER)).thenReturn(Optional.of(target));
        when(registry.findDiscoverable()).thenReturn(List.of(
                new ApplicationIamRegistration(
                        "FUTURE_LEDGER", "Future Ledger", "دفتر المستقبل", "ACTIVE", "1",
                        Set.of("FUTURE_LEDGER"), Set.of("PLANETARY_CLUSTER"), Set.of("FUTURE_LEDGER.READ"),
                        Set.of(), Map.of("routeRoots", List.of("future-ledger"))))));
        when(accessRead.knownCapabilities()).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.effectiveCapabilityCodes(TENANT, USER)).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.activeRoleGrants(TENANT, USER)).thenReturn(List.of());
        when(accessRead.capabilityCodesByRoleIds(TENANT, Set.of())).thenReturn(Map.of());

        var service = new UserApplicationAccessProjectionService(registry, accessRead, users);

        ApplicationAccessProjection projection = service.project(TENANT, USER).get(0);

        assertThat(projection.registryValid()).isFalse();
        assertThat(projection.effectiveAccess()).isFalse();
        assertThat(projection.reason()).isEqualTo("UNKNOWN_OR_UNSUPPORTED_SCOPE");
    }

    @Test
    void unknownDeclaredCapabilityFailsClosed() {
        User target = new User(TENANT, "future.user@example.com", "Future User", UserStatus.ACTIVE);
        when(users.findByTenantIdAndId(TENANT, USER)).thenReturn(Optional.of(target));
        when(registry.findDiscoverable()).thenReturn(List.of(
                new ApplicationIamRegistration(
                        "FUTURE_LEDGER", "Future Ledger", "دفتر المستقبل", "ACTIVE", "1",
                        Set.of("FUTURE_LEDGER"), Set.of("TENANT"),
                        Set.of("FUTURE_LEDGER.READ", "FUTURE_LEDGER.DOES_NOT_EXIST"),
                        Set.of(), Map.of("routeRoots", List.of("future-ledger"))))));
        when(accessRead.knownCapabilities()).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.effectiveCapabilityCodes(TENANT, USER)).thenReturn(Set.of("FUTURE_LEDGER.READ"));
        when(accessRead.activeRoleGrants(TENANT, USER)).thenReturn(List.of());
        when(accessRead.capabilityCodesByRoleIds(TENANT, Set.of())).thenReturn(Map.of());

        var service = new UserApplicationAccessProjectionService(registry, accessRead, users);

        ApplicationAccessProjection projection = service.project(TENANT, USER).get(0);

        assertThat(projection.registryValid()).isFalse();
        assertThat(projection.effectiveAccess()).isFalse();
        assertThat(projection.reason()).isEqualTo("UNKNOWN_DECLARED_CAPABILITY");
        assertThat(projection.effectiveCapabilities()).isEmpty();
    }
}
