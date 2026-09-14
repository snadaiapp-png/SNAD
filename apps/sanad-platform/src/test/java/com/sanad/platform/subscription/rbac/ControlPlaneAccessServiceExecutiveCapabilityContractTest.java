package com.sanad.platform.subscription.rbac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneAccessServiceExecutiveCapabilityContractTest {

    @Test
    void accessCheckV2MustExposeBroadExecutiveAuthoritiesUsedByMutationEndpoints() {
        assertThat(ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES)
                .contains("EXECUTIVE_VIEW", "EXECUTIVE_MANAGE");
    }
}
