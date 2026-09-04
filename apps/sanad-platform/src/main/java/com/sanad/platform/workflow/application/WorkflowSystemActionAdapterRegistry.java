package com.sanad.platform.workflow.application;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of {@link WorkflowSystemActionAdapter} implementations keyed by
 * their declared {@code type()} token (resolved from a step's
 * {@code configuration.adapter} JSON key).
 *
 * <p>Fail-closed contract: an unknown adapter code is a graph-resolution
 * incident (IllegalStateException -> HTTP 409), never a silent skip or a
 * 500. Production adapters register as ordinary beans; deterministic E2E
 * adapters register only under the {@code workflow-e2e} profile.</p>
 */
@Service
public class WorkflowSystemActionAdapterRegistry {

    private final Map<String, WorkflowSystemActionAdapter> adaptersByType;

    public WorkflowSystemActionAdapterRegistry(java.util.List<WorkflowSystemActionAdapter> adapters) {
        this.adaptersByType = adapters == null ? Map.of() : adapters.stream()
                .collect(Collectors.toUnmodifiableMap(
                        adapter -> adapter.type().toUpperCase(java.util.Locale.ROOT),
                        Function.identity()));
    }

    public WorkflowSystemActionAdapter require(String adapterCode) {
        if (adapterCode == null || adapterCode.isBlank()) {
            throw new IllegalStateException(
                    "SYSTEM_ACTION step is missing its configuration.adapter token");
        }
        WorkflowSystemActionAdapter adapter = adaptersByType.get(adapterCode.toUpperCase(java.util.Locale.ROOT));
        if (adapter == null) {
            throw new IllegalStateException(
                    "No WorkflowSystemActionAdapter registered for type '" + adapterCode + "'");
        }
        return adapter;
    }

    /** Deterministic always-failing adapter used ONLY by the E2E release gate. */
    public static final class E2EAlwaysFailAdapter implements WorkflowSystemActionAdapter {
        public static final String TYPE = "E2E_ALWAYS_FAIL";

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public ActionResult execute(ActionRequest request) {
            // TRANSIENT failure on purpose: the system-action service then
            // exhausts its retry budget and opens the incident WITHOUT
            // throwing through the transaction boundary — the caller receives
            // a controlled failed ExecutionResult and surfaces a real, fully
            // persisted incident (P10 contract). A permanentFailure here would
            // throw and roll back the incident row with it.
            return ActionResult.transientFailure("E2E_DETERMINISTIC_FAILURE");
        }

        @Override
        public ActionResult compensate(ActionRequest request) {
            return ActionResult.permanentFailure("E2E_DETERMINISTIC_FAILURE");
        }
    }
}
