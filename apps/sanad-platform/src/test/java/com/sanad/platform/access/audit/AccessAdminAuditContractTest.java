package com.sanad.platform.access.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural gate preventing access-admin mutations from silently losing audit wiring. */
class AccessAdminAuditContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/sanad/platform");

    @Test
    void legacyMutationServicesRemainBoundToCentralAuditAdapter() throws Exception {
        assertContains("access/role/RoleService.java", "AccessMutationAuditSupport", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_STATUS_CHANGE");
        assertContains("access/role/RoleCapabilityService.java", "AccessMutationAuditSupport", "ROLE_CAPABILITY_ATTACH", "ROLE_CAPABILITY_DETACH");
        assertContains("access/capability/AccessCapabilityService.java", "AccessMutationAuditSupport", "CAPABILITY_CREATE", "CAPABILITY_UPDATE", "CAPABILITY_STATUS_CHANGE");
        assertContains("access/grant/UserRoleGrantService.java", "AccessMutationAuditSupport", "USER_ROLE_GRANT", "USER_ROLE_REVOKE");
    }

    @Test
    void newAuthorizationMutationsWriteDurableChangeEventsAndVersionBumps() throws Exception {
        assertContains("access/override/UserPermissionOverrideService.java",
                "authorization_change_events", "authorizationVersionService.bump", "PlatformAuditWriter");
        assertContains("access/relationship/AccessRelationshipService.java",
                "authorization_change_events", "authorizationVersionService.bump", "PlatformAuditWriter");
        assertContains("security/authorization/AuthorizationMutationCoordinator.java",
                "authorization_change_events", "versions.bump", "projections.rebuild",
                "capabilityChanged", "publishEvent");
    }

    @Test
    void controllersAreCapabilityGuardedAndDoNotAcceptTenantIdAsRequestAuthority() throws Exception {
        assertContains("access/api/UserOverrideController.java", "@RequireCapability(\"AUTHORIZATION.OVERRIDE.MANAGE\")", "AccessPrincipalContext.requireTenantId");
        assertContains("access/api/RelationshipController.java", "@RequireCapability(\"AUTHORIZATION.RELATIONSHIP.MANAGE\")", "AccessPrincipalContext.requireTenantId");
        assertContains("access/api/EffectivePermissionController.java", "@RequireCapability(\"ROLE.READ\")", "@RequireCapability(\"AUTHORIZATION.RESYNC\")");
        assertThat(read("access/api/UserOverrideController.java")).doesNotContain("@RequestParam UUID tenantId");
        assertThat(read("access/api/RelationshipController.java")).doesNotContain("@RequestParam UUID tenantId");
    }

    private static void assertContains(String relative, String... tokens) throws Exception {
        String source = read(relative);
        for (String token : tokens) assertThat(source).contains(token);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
