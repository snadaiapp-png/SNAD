package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowExecutionAttempt;
import com.sanad.platform.workflow.domain.WorkflowIncident;

import java.util.Map;
import java.util.UUID;

/**
 * Durable adapter contract for {@code SYSTEM_ACTION} steps (design decision
 * O3). Implementations are module-owned, time-bounded, idempotent where the
 * definition requires it, and never expose unrestricted DB/network/shell
 * primitives to workflow definitions.
 */
public interface WorkflowSystemActionAdapter {

    /** Stable adapter type discriminator persisted with attempts. */
    String type();

    /**
     * R1 GATE R1.12 — bounded governance metadata for the module-owned action.
     *
     * <p>The default marks the action as a platform-internal WORKFLOW action
     * (backward compatible with the existing platform/E2E adapters). A module
     * adapter MUST override {@code metadata()} to declare its owning module
     * code; the {@link WorkflowDomainActionAuthorizer} then enforces the
     * source-module entitlement and revalidates actor capability at action
     * time (assignment is not authorization). A null moduleCode fails closed
     * — every executable action belongs to a governed owner.</p>
     */
    default ActionMetadata metadata() {
        return ActionMetadata.platformInternal(type());
    }

    record ActionMetadata(
            String type,
            String moduleCode,          // owning module; null = fail closed
            String requiredCapability,  // domain capability revalidated at action time (nullable)
            String riskLevel,           // LOW | MEDIUM | HIGH | CRITICAL
            boolean compensatable,
            String idempotencyRequirement) {

        public static ActionMetadata platformInternal(String type) {
            return new ActionMetadata(type, "WORKFLOW", null, "MEDIUM", false, "IDEMPOTENT_BY_KEY");
        }

        public static ActionMetadata moduleOwned(String type, String moduleCode,
                                                 String requiredCapability, String riskLevel,
                                                 boolean compensatable) {
            return new ActionMetadata(type, moduleCode, requiredCapability, riskLevel,
                    compensatable, "IDEMPOTENT_BY_KEY");
        }
    }

    /**
     * Executes the action. Implementations must honor their declared timeout
     * and classify their own business failures as permanent — the platform
     * retries transient infrastructure failures only.
     */
    ActionResult execute(ActionRequest request);

    /** Compensation is optional; only compensatable actions implement it. */
    default ActionResult compensate(ActionRequest request) {
        throw new UnsupportedOperationException(type() + " is not compensatable");
    }

    record ActionRequest(
            UUID tenantId,
            UUID workflowInstanceId,
            UUID workflowStepInstanceId,
            String idempotencyKey,
            Map<String, Object> input,
            UUID correlationId,
            UUID causationId,
            int attemptNumber) {}

    record ActionResult(
            boolean success,
            boolean transientFailure,
            String failureCategory,
            String externalReference,
            java.util.Map<String, Object> output) {

        public static ActionResult ok(String externalReference, Map<String, Object> output) {
            return new ActionResult(true, false, null, externalReference, output);
        }

        public static ActionResult transientFailure(String category) {
            return new ActionResult(false, true, category, null, Map.of());
        }

        public static ActionResult permanentFailure(String category) {
            return new ActionResult(false, false, category, null, Map.of());
        }
    }

    /** Attempt row reader used by the resilience engine for idempotency. */
    interface AttemptStore {
        void insert(WorkflowExecutionAttempt attempt);
        void update(WorkflowExecutionAttempt attempt);
        boolean hasSucceeded(UUID tenantId, String idempotencyKey);
        int nextAttemptNumber(UUID stepInstanceId);
        void insertIncident(WorkflowIncident incident);
    }
}
