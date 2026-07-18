package com.sanad.platform.security.service;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginDestinationResolver}.
 *
 * <p>Pins the capability-derived destination logic introduced by
 * EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginDestinationResolverTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ADMIN_ROLE = UUID.randomUUID();

    @Mock private UserRoleGrantRepository roleGrantRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RoleCapabilityRepository roleCapabilityRepository;
    @Mock private AccessCapabilityRepository accessCapabilityRepository;
    @Mock private ControlPlaneAccessGuard controlPlaneAccessGuard;

    private LoginDestinationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LoginDestinationResolver(
                roleGrantRepository, roleRepository, roleCapabilityRepository,
                accessCapabilityRepository, controlPlaneAccessGuard);
    }

    @Test
    void userWithoutAnyMatchingCapabilities_seesOnlyWorkspace_andLandsThere() {
        UUID roleId = UUID.randomUUID();
        grant(roleId, "MEMBER", RoleStatus.ACTIVE, list("OTHER.READ"));

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).containsExactly("/workspace");
        assertThat(result.getDefaultDestination()).isEqualTo("/workspace");
    }

    @Test
    void crmCapabilityUser_seesCrmDestinations_andLandsOnCrm() {
        UUID roleId = UUID.randomUUID();
        grant(roleId, "MEMBER", RoleStatus.ACTIVE, list("CRM.ACCOUNT.READ"));

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).containsExactly("/workspace", "/crm", "/crm/command-center");
        assertThat(result.getDefaultDestination()).isEqualTo("/crm");
    }

    @Test
    void credentialRotationRequired_overridesEverythingAndForcesWorkspace() {
        UUID roleId = UUID.randomUUID();
        grant(roleId, "MEMBER", RoleStatus.ACTIVE, list("CRM.ACCOUNT.READ"));

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, true);

        assertThat(result.getAvailable()).contains("/crm");
        assertThat(result.getDefaultDestination()).isEqualTo("/workspace");
    }

    @Test
    void controlPlaneUser_requiresTenantMatch_andAdminRole_andControlCapability() {
        grant(ADMIN_ROLE, "ADMIN", RoleStatus.ACTIVE, list("USER.WRITE"));
        when(controlPlaneAccessGuard.isControlPlaneTenant(TENANT)).thenReturn(true);

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).contains("/control-plane");
        assertThat(result.getDefaultDestination()).isEqualTo("/control-plane");
    }

    @Test
    void adminRoleInNonControlTenant_doesNotGetControlPlaneDestination() {
        grant(ADMIN_ROLE, "ADMIN", RoleStatus.ACTIVE, list("USER.WRITE"));
        when(controlPlaneAccessGuard.isControlPlaneTenant(TENANT)).thenReturn(false);

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).doesNotContain("/control-plane");
        // USER.* is not a CRM/Finance/ERP family, so default lands on workspace.
        assertThat(result.getDefaultDestination()).isEqualTo("/workspace");
    }

    @Test
    void nonAdminRoleInControlTenant_doesNotGetControlPlane() {
        UUID roleId = UUID.randomUUID();
        grant(roleId, "MEMBER", RoleStatus.ACTIVE, list("USER.WRITE"));
        when(controlPlaneAccessGuard.isControlPlaneTenant(TENANT)).thenReturn(true);

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).doesNotContain("/control-plane");
    }

    @Test
    void noRoleGrants_seesOnlyWorkspace() {
        when(roleGrantRepository.findByTenantIdAndUserIdAndStatus(eq(TENANT), eq(USER), any()))
                .thenReturn(Collections.emptyList());

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).containsExactly("/workspace");
        assertThat(result.getDefaultDestination()).isEqualTo("/workspace");
    }

    @Test
    void multipleCapabilityFamilies_showAllMatchingDestinations() {
        UUID roleId = UUID.randomUUID();
        grant(roleId, "MANAGER", RoleStatus.ACTIVE,
                list("CRM.ACCOUNT.READ", "FINANCE.READ", "ERP.READ"));

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable())
                .contains("/crm", "/crm/command-center", "/finance", "/erp");
        // CRM is picked first per the documented default policy order.
        assertThat(result.getDefaultDestination()).isEqualTo("/crm");
    }

    @Test
    void inactiveRole_grantDoesNotContributeCapabilities() {
        UUID roleId = UUID.randomUUID();
        // Active grant, but role is INACTIVE → capabilities should not apply.
        grant(roleId, "MEMBER", RoleStatus.INACTIVE, list("CRM.ACCOUNT.READ"));

        LoginDestinationResolver.DestinationSet result = resolver.resolve(TENANT, USER, false);

        assertThat(result.getAvailable()).containsExactly("/workspace");
        assertThat(result.getDefaultDestination()).isEqualTo("/workspace");
    }

    // ---------- helpers ----------

    /**
     * Stub the repository chain for a single ACTIVE grant pointing at {@code roleId}.
     * The role carries the provided capability codes.
     */
    private void grant(UUID roleId, String roleCode, RoleStatus status, List<String> capabilityCodes) {
        UserRoleGrant grant = new UserRoleGrant(TENANT, USER, roleId, /* organizationId */ null);
        grant.setStatus(UserGrantStatus.ACTIVE);
        when(roleGrantRepository.findByTenantIdAndUserIdAndStatus(eq(TENANT), eq(USER), any()))
                .thenReturn(Collections.singletonList(grant));

        Role role = roleWithCode(roleId, roleCode, status);
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(Collections.singletonList(role));

        List<RoleCapability> links = new ArrayList<>();
        List<AccessCapability> caps = new ArrayList<>();
        for (String code : capabilityCodes) {
            UUID capId = UUID.randomUUID();
            links.add(new RoleCapability(TENANT, roleId, capId));
            caps.add(accessCapabilityWithCode(capId, code));
        }
        when(roleCapabilityRepository.findByTenantIdAndRoleId(TENANT, roleId)).thenReturn(links);
        when(accessCapabilityRepository.findAllById(any())).thenReturn(caps);
    }

    /**
     * Construct a Role via its public constructor (no setters for id/tenantId).
     * setStatus is publicly available. id is set reflectively because the resolver
     * reads role.getId() to scope capability lookups.
     */
    private Role roleWithCode(UUID roleId, String code, RoleStatus status) {
        Role role = new Role(TENANT, code, code, "test role");
        role.setStatus(status);
        try {
            java.lang.reflect.Field idField = Role.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(role, roleId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set Role.id for test", e);
        }
        return role;
    }

    /**
     * Construct an AccessCapability and reflectively assign its id (no public setter).
     */
    private AccessCapability accessCapabilityWithCode(UUID capId, String code) {
        AccessCapability cap = new AccessCapability(code, code, "test capability");
        try {
            java.lang.reflect.Field idField = AccessCapability.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(cap, capId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set AccessCapability.id for test", e);
        }
        return cap;
    }

    private static List<String> list(String... codes) {
        return Arrays.asList(codes);
    }
}
