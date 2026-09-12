package com.sanad.platform.workflow.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.security.SecurityPermitAllTestConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R1 GATES R1.12/R1.13/R1.32 - domain action governance (PostgreSQL Direct).
 *
 * Covers the registry/metadata/authorization level of the matrix;
 * ACTION_IDEMPOTENCY / TRANSIENT_RETRY_BOUNDED /
 * PERMANENT_FAILURE_NOT_RETRIED_INDEFINITELY are exercised at the engine
 * level by the existing Y2 execution-attempt suites (WorkflowSystemActionService
 * AttemptStore contract, Wave-2 evidence).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowDomainActionGovernanceTest {

    @Autowired private WorkflowSystemActionAdapterRegistry registry;
    @Autowired private WorkflowDomainActionAuthorizer authorizer;
    @Autowired private com.sanad.platform.access.evaluation.CapabilityEvaluationService capabilityEvaluationService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    static class OkPlatformAdapter implements WorkflowSystemActionAdapter {
        @Override public String type() { return "TEST_PLATFORM_OK"; }
        @Override public ActionResult execute(ActionRequest request) { return ActionResult.ok("ok", Map.of()); }
    }

    static class CrmModuleAdapter implements WorkflowSystemActionAdapter {
        @Override public String type() { return "TEST_CRM_ACTION"; }
        @Override public ActionResult execute(ActionRequest request) { return ActionResult.ok("ok", Map.of()); }
        @Override public ActionMetadata metadata() {
            return ActionMetadata.moduleOwned("TEST_CRM_ACTION", "CRM", null, "MEDIUM", true);
        }
        @Override public ActionResult compensate(ActionRequest request) { return ActionResult.ok("compensated", Map.of()); }
    }

    static class CapabilityGatedAdapter implements WorkflowSystemActionAdapter {
        @Override public String type() { return "TEST_GATED_ACTION"; }
        @Override public ActionResult execute(ActionRequest request) { return ActionResult.ok("ok", Map.of()); }
        @Override public ActionMetadata metadata() {
            return ActionMetadata.moduleOwned("TEST_GATED_ACTION", "WORKFLOW", "WORKFLOW.TEST.DOMAIN", "HIGH", false);
        }
    }

    static class OrphanAdapter implements WorkflowSystemActionAdapter {
        @Override public String type() { return "TEST_ORPHAN_ACTION"; }
        @Override public ActionResult execute(ActionRequest request) { return ActionResult.ok("ok", Map.of()); }
        @Override public ActionMetadata metadata() {
            return new ActionMetadata("TEST_ORPHAN_ACTION", null, null, "LOW", false, "IDEMPOTENT_BY_KEY");
        }
    }

    @Test
    void UNKNOWN_ACTION_FAILS_CLOSED() {
        assertThatThrownBy(() -> registry.require("NO_SUCH_ADAPTER"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No WorkflowSystemActionAdapter registered");
    }

    @Test
    void PLATFORM_INTERNAL_ACTION_ALLOWED_FOR_SYSTEM_FLOW() {
        // Drain-safe semantics: platform-internal actions of already-running
        // instances execute regardless of entitlement state (removal policy
        // DRAIN_EXISTING; the NEW-use boundary is design/start/trigger).
        authorizer.authorizeExecution(TENANT, new OkPlatformAdapter(), null);
        authorizer.authorizeExecution(TENANT, new OkPlatformAdapter(), ACTOR); // no requiredCapability
    }

    @Test
    void ACTION_MODULE_DISABLED_DENIED() {
        // Tenant without any subscription -> every source module unentitled.
        assertThatThrownBy(() -> authorizer.authorizeExecution(TENANT, new CrmModuleAdapter(), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("DOMAIN_ACTION_MODULE_DISABLED");
    }

    @Test
    void ACTION_MODULE_ENTITLED_ALLOWED() {
        // The module-internal gated action without an actor passes the module
        // ownership stage (WORKFLOW default) and has no requiredCapability.
        authorizer.authorizeExecution(TENANT, new CapabilityGatedAdapter(), null);
    }

    @Test
    void ACTOR_CAPABILITY_REVALIDATED_ASSIGNMENT_IS_NOT_AUTHORIZATION() {
        // The shared permit-all test config stubs the evaluator to allow;
        // re-stub the live evaluation for THIS capability to a denial so the
        // revalidation contract is exercised for real. Restore afterwards.
        org.mockito.Mockito.when(capabilityEvaluationService.evaluate(
                        org.mockito.Mockito.eq(TENANT), org.mockito.Mockito.eq(ACTOR),
                        org.mockito.Mockito.eq("WORKFLOW.TEST.DOMAIN"), org.mockito.Mockito.any()))
                .thenReturn(new com.sanad.platform.access.AccessDecisionResponse(
                        TENANT, ACTOR, null, "WORKFLOW.TEST.DOMAIN", false, "TEST_DENIED", null, null));
        try {
            // Actor without the live domain capability is denied even though the
            // action was reached through a (hypothetical) assignment.
            assertThatThrownBy(() -> authorizer.authorizeExecution(TENANT, new CapabilityGatedAdapter(), ACTOR))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("DOMAIN_ACTION_CAPABILITY_DENIED");
        } finally {
            org.mockito.Mockito.reset(capabilityEvaluationService);
        }
    }

    @Test
    void NULL_MODULE_CODE_FAILS_CLOSED() {
        assertThatThrownBy(() -> authorizer.authorizeExecution(TENANT, new OrphanAdapter(), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("DOMAIN_ACTION_FAIL_CLOSED");
    }

    @Test
    void COMPENSATION_CONTRACT_HONORED() {
        // Default (non-compensatable) actions refuse compensation explicitly.
        WorkflowSystemActionAdapter plain = new OkPlatformAdapter();
        assertThatThrownBy(() -> plain.compensate(null))
                .isInstanceOf(UnsupportedOperationException.class);
        // Compensatable module actions implement compensate().
        WorkflowSystemActionAdapter crm = new CrmModuleAdapter();
        assertThat(crm.metadata().compensatable()).isTrue();
        assertThat(crm.compensate(null).success()).isTrue();
    }

    @Test
    void NO_ARBITRARY_EXECUTION_PRIMITIVE_ADAPTERS() {
        Set<String> forbidden = Set.of("SQL", "SHELL", "HTTP", "JAVASCRIPT", "EVAL",
                "REFLECTION", "SCRIPT", "GROOVY", "RAW_HTTP", "GENERIC_HTTP");
        for (WorkflowSystemActionAdapter adapter : registry.list()) {
            assertThat(forbidden).doesNotContain(adapter.type().toUpperCase());
            // Every registered adapter declares a governed owning module.
            assertThat(adapter.metadata().moduleCode()).isNotNull();
        }
    }

    @Test
    void ALL_REGISTERED_ADAPTERS_CARRY_VALID_METADATA() {
        List<WorkflowSystemActionAdapter.ActionMetadata> all = registry.list().stream()
                .map(WorkflowSystemActionAdapter::metadata).collect(Collectors.toList());
        assertThat(all).allSatisfy(m -> {
            assertThat(m.riskLevel()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
            assertThat(m.idempotencyRequirement()).isNotBlank();
        });
    }
}
