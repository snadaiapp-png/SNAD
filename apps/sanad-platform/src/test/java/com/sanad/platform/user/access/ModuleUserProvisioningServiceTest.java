package com.sanad.platform.user.access;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleUserProvisioningServiceTest {

    @Test
    void acceptsOnlyRolesWhoseCapabilitiesBelongToTheCurrentModuleNamespaces() {
        Set<String> hrNamespaces = Set.of("HRM", "HR");

        assertThat(ModuleUserProvisioningService.isModuleOnlyRole(
                Set.of("HRM.EMPLOYEE.VIEW", "HRM.LEAVE.SELF_VIEW"), hrNamespaces)).isTrue();

        assertThat(ModuleUserProvisioningService.isModuleOnlyRole(
                Set.of("HR.EMPLOYEE.READ"), hrNamespaces)).isTrue();

        assertThat(ModuleUserProvisioningService.isModuleOnlyRole(
                Set.of("HRM.EMPLOYEE.VIEW", "CRM.ACCOUNT.READ"), hrNamespaces)).isFalse();

        assertThat(ModuleUserProvisioningService.isModuleOnlyRole(
                Set.of("HRM.EMPLOYEE.VIEW", "USER.READ"), hrNamespaces)).isFalse();

        assertThat(ModuleUserProvisioningService.isModuleOnlyRole(
                Set.of(), hrNamespaces)).isFalse();
    }
}
