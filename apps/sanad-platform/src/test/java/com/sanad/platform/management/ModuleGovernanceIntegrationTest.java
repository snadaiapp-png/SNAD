package com.sanad.platform.management;

import com.sanad.platform.management.application.ModuleGovernanceService;
import com.sanad.platform.module.registry.ModuleRepository;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class ModuleGovernanceIntegrationTest {

    @Autowired private ModuleGovernanceService moduleGovernanceService;
    @Autowired private ModuleRepository moduleRepository;

    @Test
    void status_returnsAllRegisteredModulesInRegistryOrder() {
        var modules = moduleGovernanceService.getModuleStatuses();
        var registryCodes = moduleRepository.findAll().stream().map(m -> m.getCode()).toList();
        var projectionCodes = modules.stream().map(m -> (String) m.get("code")).toList();

        assertThat(modules).hasSameSizeAs(registryCodes);
        assertThat(modules).isNotEmpty();
        assertThat(projectionCodes).containsExactlyElementsOf(registryCodes);
    }

    @Test
    void status_containsExecutiveModuleFields() {
        var modules = moduleGovernanceService.getModuleStatuses();

        assertThat(modules).allSatisfy(module -> {
            assertThat(module).containsKeys("id", "code", "name", "status", "enabled", "displayOrder", "version", "capabilities");
        });
    }

    @Test
    void status_includesRegisteredCapabilities() {
        var modules = moduleGovernanceService.getModuleStatuses();

        assertThat(modules).allSatisfy(module -> {
            var capabilities = module.get("capabilities");
            assertThat(capabilities).isInstanceOf(List.class);
        });
    }

    @Test
    void status_doesNotMutateGlobalRegistry() {
        var before = moduleRepository.findAll().stream().map(m -> m.getCode() + ":" + m.getStatus() + ":" + m.isEnabled()).toList();
        moduleGovernanceService.getModuleStatuses();
        var after = moduleRepository.findAll().stream().map(m -> m.getCode() + ":" + m.getStatus() + ":" + m.isEnabled()).toList();

        assertThat(after).containsExactlyElementsOf(before);
    }

    @Test
    void managementEndpoint_requiresExecutiveCommandCenterView() throws Exception {
        var method = Class.forName("com.sanad.platform.management.api.CommandCenterController")
                .getDeclaredMethod("moduleStatuses", org.springframework.security.core.Authentication.class);
        var annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("EXECUTIVE_COMMAND_CENTER.VIEW");
    }
}
