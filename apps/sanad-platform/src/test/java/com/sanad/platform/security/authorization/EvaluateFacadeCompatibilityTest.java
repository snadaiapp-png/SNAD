package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideRepository;
import com.sanad.platform.access.relationship.RelationshipResolver;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 1 Task 8 — legacy facade binary/behavior compatibility contract.
 *
 * <p>The legacy {@code evaluate(UUID, UUID, String, UUID)} signature never
 * changes (874 {@code @RequireCapability} call sites stay binary compatible),
 * baseline reason strings are preserved, and with the committed defaults
 * {@code SANAD_UAC_PIPELINE_ENABLED=false} / {@code SANAD_UAC_MODE=legacy} the
 * facade preserves legacy behavior bit-for-bit (override rows are NOT
 * consulted). Only when the owner explicitly enables the authoritative
 * pipeline does the facade delegate to {@code evaluateDetailed}.</p>
 */
class EvaluateFacadeCompatibilityTest {

    private static final String CAPABILITY_CODE = "USER.READ";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID capabilityId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    private UserRoleGrantService grants;
    private RoleService roles;
    private RoleCapabilityService mappings;
    private AccessCapabilityService capabilities;
    private OrganizationRepository organizations;
    private UserPermissionOverrideRepository overrides;
    private RelationshipResolver relationships;

    @BeforeEach
    void wireMocks() {
        grants = mock(UserRoleGrantService.class);
        roles = mock(RoleService.class);
        mappings = mock(RoleCapabilityService.class);
        capabilities = mock(AccessCapabilityService.class);
        organizations = mock(OrganizationRepository.class);
        overrides = mock(UserPermissionOverrideRepository.class);
        relationships = mock(RelationshipResolver.class);
    }

    private CapabilityEvaluationService service(MockEnvironment environment) {
        return new CapabilityEvaluationService(grants, roles, mappings, capabilities,
                organizations, overrides, relationships, environment);
    }

    @Test
    void legacyEvaluateSignatureIsPresentAndPublic() throws Exception {
        Method evaluate = CapabilityEvaluationService.class.getMethod(
                "evaluate", UUID.class, UUID.class, String.class, UUID.class);
        assertThat(evaluate.getReturnType()).isEqualTo(AccessDecisionResponse.class);
        assertThat(Modifier.isPublic(evaluate.getModifiers())).isTrue();
    }

    @Test
    void flagOffPreservesLegacyRoleGrantBehaviorWithBaselineReasonStrings() {
        // Baseline role-grant scenario recorded at 8d0d49c7: allowed=true,
        // reason=ROLE_CAPABILITY_MATCH, matched role id/code carried through.
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserRoleGrant grant = roleGrant();
        Role role = activeRole();
        when(capabilities.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AccessDecisionResponse response =
                service(legacyEnvironment()).evaluate(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("ROLE_CAPABILITY_MATCH");
        assertThat(response.matchedRoleId()).isEqualTo(roleId);
        assertThat(response.matchedRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    void flagOffIgnoresOverrideRowsEvenWhenAnActiveDirectDenyExists() {
        // Legacy behavior is bit-for-bit: with the flag OFF (committed default)
        // the facade never consults overrides — a direct DENY row must NOT
        // change the decision.
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserRoleGrant grant = roleGrant();
        Role role = activeRole();
        UserPermissionOverrideRepository.ActiveOverride denyRow =
                new UserPermissionOverrideRepository.ActiveOverride(
                        UUID.randomUUID(), "DENY", null, null,
                        Instant.now().minusSeconds(60), null);
        when(capabilities.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrides.findOverrides(tenantId, userId, capabilityId)).thenReturn(List.of(denyRow));
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AccessDecisionResponse response =
                service(legacyEnvironment()).evaluate(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("ROLE_CAPABILITY_MATCH");
    }

    @Test
    void legacyUnknownCapabilityReasonStringIsPreserved() {
        when(capabilities.loadByCode(CAPABILITY_CODE))
                .thenThrow(new AccessResourceNotFoundException("missing"));

        AccessDecisionResponse response =
                service(legacyEnvironment()).evaluate(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("CAPABILITY_NOT_FOUND");
    }

    @Test
    void pipelineEnabledDelegatesToFiveStagePipelineAndDenyDominates() {
        // Flag ON (explicit owner action): the facade delegates to the pipeline.
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserRoleGrant grant = roleGrant();
        Role role = activeRole();
        UserPermissionOverrideRepository.ActiveOverride denyRow =
                new UserPermissionOverrideRepository.ActiveOverride(
                        UUID.randomUUID(), "DENY", null, null,
                        Instant.now().minusSeconds(60), null);
        when(organizations.findByTenantIdAndId(tenantId, null))
                .thenReturn(Optional.empty());
        when(capabilities.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrides.findOverrides(tenantId, userId, capabilityId)).thenReturn(List.of(denyRow));
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AccessDecisionResponse response =
                service(authoritativeEnvironment()).evaluate(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("EXPLICIT_DIRECT_DENY");
    }

    @Test
    void pipelineEnabledPreservesBaselineRoleGrantMapping() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserRoleGrant grant = roleGrant();
        Role role = activeRole();
        when(organizations.findByTenantIdAndId(tenantId, null)).thenReturn(Optional.empty());
        when(capabilities.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrides.findOverrides(tenantId, userId, capabilityId)).thenReturn(List.of());
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AccessDecisionResponse response =
                service(authoritativeEnvironment()).evaluate(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("ROLE_CAPABILITY_MATCH");
        assertThat(response.matchedRoleId()).isEqualTo(roleId);
        assertThat(response.matchedRoleCode()).isEqualTo("ADMIN");
    }

    // ---- fixtures ----

    private MockEnvironment legacyEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("sanad.uac.pipeline-enabled", "false");
        environment.setProperty("sanad.uac.mode", "legacy");
        return environment;
    }

    private MockEnvironment authoritativeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("sanad.uac.pipeline-enabled", "true");
        environment.setProperty("sanad.uac.mode", "authoritative");
        return environment;
    }

    private AccessCapability capability(CapabilityStatus status) {
        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getId()).thenReturn(capabilityId);
        when(capability.getCode()).thenReturn(CAPABILITY_CODE);
        when(capability.getStatus()).thenReturn(status);
        return capability;
    }

    private UserRoleGrant roleGrant() {
        UserRoleGrant grant = mock(UserRoleGrant.class);
        when(grant.getRoleId()).thenReturn(roleId);
        when(grant.isTenantWide()).thenReturn(true);
        when(grant.getOrganizationId()).thenReturn(null);
        return grant;
    }

    private Role activeRole() {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("ADMIN");
        when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
        return role;
    }
}
