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
}
