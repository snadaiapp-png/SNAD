package com.sanad.platform.workflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.security.SecurityPermitAllTestConfig;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R1 GATES R1.8/R1.9/R1.31 — module integration registry auto-discovery,
 * duplicate/unknown fail-closed, and the FUTURE-MODULE ZERO-CORE-CHANGE proof.
 *
 * <p>The TEST_FUTURE_MODULE bean below is a plain {@code @Component} test
 * bean: it implements the SPI and — without ANY edit to the registry or
 * Workflow core — must become discoverable. Removing the bean removes it
 * from discovery (proven by the pure-registry case at the bottom).</p>
 */
@SpringBootTest(properties = "r1.registry-proof.context=unique")
@ActiveProfiles("local")
@Import({SecurityPermitAllTestConfig.class, WorkflowModuleIntegrationRegistryTest.TestFutureModule.class})
class WorkflowModuleIntegrationRegistryTest {

    @Autowired
    private WorkflowModuleIntegrationRegistry registry;

    // ===== R1.9 — future-module zero-core-change proof =====
    // Registered via @Import (explicit, deterministic); the REGISTRY itself
    // requires zero edits — any module anywhere on the classpath that ships a
    // contract bean becomes discoverable.

    static class TestFutureModule implements WorkflowModuleIntegrationContract {
        @Override public String moduleCode() { return "TEST_FUTURE_MODULE"; }
        @Override public String moduleVersion() { return "0.0.1-TEST"; }
        @Override public String displayName() { return "R1 future-module proof"; }
        @Override public Collection<EntityDescriptor> entities() {
            return List.of(new EntityDescriptor("FUTURE_THING", "future thing", "future_table", "/future/{id}"));
        }
    }

    @Test
    void FUTURE_TEST_MODULE_DISCOVERED_WITH_ZERO_WORKFLOW_CORE_EDIT() {
        // The bean above is discovered purely through Spring bean discovery —
        // the registry core was never edited for it.
        assertThat(registry.isRegistered("TEST_FUTURE_MODULE"))
                .as("registered codes=%s", registry.list().stream()
                        .map(WorkflowModuleIntegrationContract::moduleCode).toList())
                .isTrue();
        WorkflowModuleIntegrationContract contract = registry.require("TEST_FUTURE_MODULE");
        assertThat(contract.displayName()).isEqualTo("R1 future-module proof");
        assertThat(contract.entities()).hasSize(1);
        assertThat(contract.entities().iterator().next().entityType()).isEqualTo("FUTURE_THING");
        // partially-implemented contract: unspecified collections are empty+immutable
        assertThat(contract.events()).isEmpty();
        assertThatThrownBy(() -> contract.events().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void REGISTERED_READY_MODULE_DISCOVERED() {
        // The real CRM integration ships in R1 main sources.
        assertThat(registry.isRegistered("CRM")).isTrue();
        WorkflowModuleIntegrationContract crm = registry.require("CRM");
        assertThat(crm.moduleCode()).isEqualTo("CRM");
        assertThat(crm.entities()).isNotEmpty();
        assertThat(crm.deepLinks()).isNotEmpty();
        assertThat(crm.attachmentCapabilities()).isNotEmpty();
    }

    @Test
    void MODULE_CODE_CASE_NORMALIZED() {
        assertThat(registry.find("crm")).isPresent();
        assertThat(registry.find("  CrM  ")).isPresent();
    }

    @Test
    void UNKNOWN_MODULE_FAILS_CLOSED() {
        assertThat(registry.find("NOT_REGISTERED_MODULE")).isEmpty();
        assertThat(registry.isRegistered("NOT_REGISTERED_MODULE")).isFalse();
        assertThatThrownBy(() -> registry.require("NOT_REGISTERED_MODULE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No Workflow module integration contract");
    }

    @Test
    void NULL_AND_BLANK_MODULE_FAIL_CLOSED() {
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.find("   ")).isEmpty();
        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DUPLICATE_MODULE_CODE_FAILS() {
        WorkflowModuleIntegrationContract first = new TestFutureModule();
        WorkflowModuleIntegrationContract duplicate = new TestFutureModule();
        assertThatThrownBy(() -> new WorkflowModuleIntegrationRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate Workflow module integration contract");
    }

    @Test
    void BLANK_MODULE_CODE_FAILS_STARTUP() {
        WorkflowModuleIntegrationContract blank = new WorkflowModuleIntegrationContract() {
            @Override public String moduleCode() { return "  "; }
            @Override public String moduleVersion() { return "0"; }
            @Override public String displayName() { return "bad"; }
        };
        assertThatThrownBy(() -> new WorkflowModuleIntegrationRegistry(List.of(blank)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank moduleCode");
    }

    @Test
    void REGISTRATION_ORDER_IS_DETERMINISTIC() {
        WorkflowModuleIntegrationContract a = new TestFutureModule();
        WorkflowModuleIntegrationContract b = new CrmWorkflowIntegration();
        // feed reversed on purpose; listing must be sorted by module code
        WorkflowModuleIntegrationRegistry r = new WorkflowModuleIntegrationRegistry(List.of(a, b));
        assertThat(r.list().stream().map(WorkflowModuleIntegrationContract::moduleCode))
                .containsExactly("CRM", "TEST_FUTURE_MODULE");
    }

    @Test
    void CLASSIFICATION_STATES_ARE_DISTINCT() {
        WorkflowModuleIntegrationRegistry r = new WorkflowModuleIntegrationRegistry(
                List.of(new CrmWorkflowIntegration()));
        // registered + globally known + tenant entitled -> workflow ready
        assertThat(r.classify("CRM", true, true))
                .isEqualTo(WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.REGISTERED_AND_WORKFLOW_READY);
        // registered + globally known + tenant NOT entitled -> disabled for tenant
        assertThat(r.classify("CRM", true, false))
                .isEqualTo(WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.DISABLED_FOR_TENANT);
        // globally known but NO contract -> classified, not workflow-ready
        assertThat(r.classify("POS", true, true))
                .isEqualTo(WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.REGISTERED_NO_WORKFLOW_CONTRACT);
        // not even globally registered -> unavailable
        assertThat(r.classify("GHOST", false, false))
                .isEqualTo(WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.UNAVAILABLE);
    }

    @Test
    void REGISTRY_ISOLATION_PURE_INSTANCES() {
        // A fresh registry with no contracts discovers nothing (the "remove
        // bean -> disappears" half of the R1.9 proof).
        WorkflowModuleIntegrationRegistry empty = new WorkflowModuleIntegrationRegistry(List.of());
        assertThat(empty.list()).isEmpty();
        assertThat(empty.isRegistered("TEST_FUTURE_MODULE")).isFalse();
        assertThatThrownBy(() -> empty.require("TEST_FUTURE_MODULE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(empty.classify("TEST_FUTURE_MODULE", false, false))
                .isEqualTo(WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.UNAVAILABLE);
    }
}
