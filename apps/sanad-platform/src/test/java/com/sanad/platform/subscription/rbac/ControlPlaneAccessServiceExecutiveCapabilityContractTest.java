package com.sanad.platform.subscription.rbac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneAccessServiceExecutiveCapabilityContractTest {
    @Test
    void accessCheckV2MustExposeBroadExecutiveAuthoritiesUsedByMutationEndpoints() {
        assertThat(ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES)
                .contains("EXECUTIVE_VIEW", "EXECUTIVE_MANAGE");
    }

    @Test
    void accessCheckV2MustExposeCanonicalUacAdministrationAuthorities() {
        assertThat(ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES).contains(
                "ROLE.READ",
                "AUTHORIZATION.OVERRIDE.MANAGE",
                "AUTHORIZATION.RELATIONSHIP.MANAGE",
                "AUTHORIZATION.RESYNC",
                "AUTHORIZATION.BREAK_GLASS",
                "AUTHORIZATION.RECOVER",
                "AUTHORIZATION.PLATFORM.MANAGE");
    }

    @Test
        void accessCheckV2MustExposePlatformIamAuthoritiesUsedByUsersModule() {
        assertThat(ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES)
                .contains(
                        "PLATFORM.USER.READ",
                        "PLATFORM.USER.CREATE",
                        "PLATFORM.USER.UPDATE",
                        "PLATFORM.USER.SUSPEND",
                        "PLATFORM.USER.DISABLE",
                        "PLATFORM.ROLE.READ",
                        "PLATFORM.ROLE.CREATE",
                        "PLATFORM.ROLE.UPDATE",
                        "PLATFORM.ROLE.ASSIGN",
                        "PLATFORM.ROLE.DELETE",
                        "PLATFORM.PERMISSION.READ",
                        "PLATFORM.PERMISSION.MANAGE",
                        "PLATFORM.SESSION.READ",
                        "PLATFORM.SESSION.REVOKE",
                        "PLATFORM.AUDIT.READ",
                        "PLATFORM.SECURITY.READ",
                        "PLATFORM.SECURITY.MANAGE");    }
}
