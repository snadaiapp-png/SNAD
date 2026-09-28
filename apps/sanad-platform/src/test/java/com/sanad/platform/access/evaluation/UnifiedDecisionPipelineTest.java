package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 1 Task 7 — five-stage decision pipeline contract (spec §4.1 Revision D).
 *
 * <p>A. hard guards · B. explicit direct DENY (capability-wide) ·
 * C. candidate ALLOW sources (direct ALLOW, role grant, relationship policy,
 * delegated grant) · D. scope resolution/union · E. default DENY.</p>
 *
 * <p>There is NO PROTECTED_SAFETY source and no allow source that outranks an
 * active direct DENY. Fixtures are built BEFORE stubbing (no nested mock
 * stubbing inside when() arguments).</p>
 */
class UnifiedDecisionPipelineTest {

    private static final String CAPABILITY_CODE = "CRM.ACCOUNT.READ";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID capabilityId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    private AccessCapabilityService capabilityService;
    private UserRoleGrantService grantService;
    private RoleService roleService;
    private RoleCapabilityService roleCapabilityService;
    private OrganizationRepository organizationRepository;
    private UserPermissionOverrideRepository overrideRepository;
    private RelationshipResolver relationshipResolver;
    private CapabilityEvaluationService service;

    @BeforeEach
    void wirePipeline() {
        capabilityService = mock(AccessCapabilityService.class);
        grantService = mock(UserRoleGrantService.class);
        roleService = mock(RoleService.class);
        roleCapabilityService = mock(RoleCapabilityService.class);
        organizationRepository = mock(OrganizationRepository.class);
        overrideRepository = mock(UserPermissionOverrideRepository.class);
        relationshipResolver = mock(RelationshipResolver.class);
        Optional<com.sanad.platform.organization.domain.Organization> existingOrg =
                Optional.of(mock(com.sanad.platform.organization.domain.Organization.class));
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId)).thenReturn(existingOrg);
        service = new CapabilityEvaluationService(grantService, roleService, roleCapabilityService,
                capabilityService, organizationRepository, overrideRepository, relationshipResolver);
    }

    // ---- Stage A: hard guards ----

    @Test
    void a_unknownCapabilityIsDefaultDenyWithCapabilityNotFound() {
        when(capabilityService.loadByCode("NO.SUCH.CODE"))
                .thenThrow(new AccessResourceNotFoundException("missing"));

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, "NO.SUCH.CODE", null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.DEFAULT_DENY);
        assertThat(decision.reason()).isEqualTo("CAPABILITY_NOT_FOUND");
    }

    @Test
    void b_inactiveCapabilityIsDenied() {
        AccessCapability inactive = capability(CapabilityStatus.INACTIVE);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(inactive);

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.DEFAULT_DENY);
        assertThat(decision.reason()).isEqualTo("CAPABILITY_INACTIVE");
    }

    // ---- Stage B: explicit direct DENY dominates every ALLOW source ----

    @Test
    void c_directDenyBeatsActiveRoleGrant() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride denyRow =
                override("DENY", null, null, null);
        UserRoleGrant grant = roleGrant(true);
        Role role = activeRole();
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(denyRow));
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(roleCapabilityService.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_DENY);
    }

    @Test
    void d_directDenyBeatsExplicitAllowOverride() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride denyRow =
                override("DENY", null, null, null);
        UserPermissionOverrideRepository.ActiveOverride allowRow =
                override("ALLOW", "TENANT_ALL", null, null);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(denyRow, allowRow));

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_DENY);
    }

    @Test
    void e_directDenyBeatsRelationshipPolicyAllow() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride denyRow =
                override("DENY", null, null, null);
        RelationshipResolver.RelationshipPolicyGrant relationshipGrant =
                new RelationshipResolver.RelationshipPolicyGrant(
                        UUID.randomUUID(), "MANAGES", "TEAM", teamId, "TEAM");
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(denyRow));
        when(relationshipResolver.resolveRelationshipGrant(tenantId, userId, capabilityId, null))
                .thenReturn(Optional.of(relationshipGrant));

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_DENY);
    }

    // ---- Stage C/D: candidate ALLOW sources and scope resolution ----

    @Test
    void f_expiredOverrideIsIgnoredAndPipelineFallsThrough() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride expired =
                override("ALLOW", "TENANT_ALL", null, Instant.now().minusSeconds(3600));
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(expired));
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of());

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.DEFAULT_DENY);
        assertThat(decision.reason()).isEqualTo("NO_MATCHING_ACTIVE_ROLE");
    }

    @Test
    void g_validTenantAllAllowIsGranted() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride allowRow =
                override("ALLOW", "TENANT_ALL", null, null);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(allowRow));

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("ALLOW");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_ALLOW);
        assertThat(decision.scopeType()).isEqualTo("TENANT_ALL");
    }

    @Test
    void h_multipleMatchingScopesProduceDeterministicUnionTrace() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        UserPermissionOverrideRepository.ActiveOverride teamAllow =
                override("ALLOW", "TEAM", teamId, null);
        UserRoleGrant grant = roleGrant(true);
        Role role = activeRole();
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId))
                .thenReturn(List.of(teamAllow));
        when(relationshipResolver.supportsRelationshipScope(tenantId, userId, "TEAM", teamId))
                .thenReturn(true);
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(roleCapabilityService.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, organizationId);

        assertThat(decision.decision()).isEqualTo("ALLOW");
        assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_ALLOW);
        List<String> trace = decision.trace();
        assertThat(trace).anyMatch(t -> t.contains("TEAM"));
        assertThat(trace).anyMatch(t -> t.contains("TENANT_ALL"));
    }

    // ---- Stage E: default deny ----

    @Test
    void i_noCandidateAndNoScopeIsDefaultDeny() {
        AccessCapability capability = capability(CapabilityStatus.ACTIVE);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(overrideRepository.findOverrides(tenantId, userId, capabilityId)).thenReturn(List.of());
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of());
        when(relationshipResolver.resolveRelationshipGrant(tenantId, userId, capabilityId, null))
                .thenReturn(Optional.empty());

        AuthorizationDecision decision =
                service.evaluateDetailed(tenantId, userId, CAPABILITY_CODE, null);

        assertThat(decision.decision()).isEqualTo("DENY");
        assertThat(decision.source()).isEqualTo(DecisionSource.DEFAULT_DENY);
        assertThat(decision.reason()).isEqualTo("NO_MATCHING_ACTIVE_ROLE");
    }

    // ---- Source enum exactness (no PROTECTED_SAFETY anywhere) ----

    @Test
    void j_decisionSourceHasNoProtectedSafetyAndExactlyTheContractedConstants() {
        Set<String> names = Arrays.stream(DecisionSource.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "EXPLICIT_DENY", "EXPLICIT_ALLOW", "ROLE_GRANT",
                "RELATIONSHIP_POLICY", "DELEGATED_GRANT", "DEFAULT_DENY");
        assertThat(names).doesNotContain("PROTECTED_SAFETY");
    }

    // ---- fixtures (built before stubbing) ----

    private AccessCapability capability(CapabilityStatus status) {
        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getId()).thenReturn(capabilityId);
        when(capability.getCode()).thenReturn(CAPABILITY_CODE);
        when(capability.getStatus()).thenReturn(status);
        return capability;
    }

    private UserPermissionOverrideRepository.ActiveOverride override(
            String effect, String scopeType, UUID scopeReference, Instant validUntil) {
        return new UserPermissionOverrideRepository.ActiveOverride(
                UUID.randomUUID(), effect, scopeType, scopeReference,
                Instant.now().minusSeconds(60), validUntil);
    }

    private UserRoleGrant roleGrant(boolean tenantWide) {
        UserRoleGrant grant = mock(UserRoleGrant.class);
        when(grant.getRoleId()).thenReturn(roleId);
        when(grant.isTenantWide()).thenReturn(tenantWide);
        when(grant.getOrganizationId()).thenReturn(tenantWide ? null : organizationId);
        return grant;
    }

    private Role activeRole() {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("SALES");
        when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
        return role;
    }
}
