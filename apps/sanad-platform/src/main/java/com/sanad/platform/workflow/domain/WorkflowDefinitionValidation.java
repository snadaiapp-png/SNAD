package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic outcome of the Y2 publish validation (design decision AN3).
 * A definition version may publish only when {@code valid} is true.
 *
 * <p>Error codes are stable, language-neutral identifiers (AM3) — the UI
 * renders localized labels; the backend contract stays fixed.</p>
 */
public record WorkflowDefinitionValidation(boolean valid, List<Error> errors) {

    public record Error(String code, String message, UUID stepId) {}

    public static WorkflowDefinitionValidation of(List<Error> errors) {
        return new WorkflowDefinitionValidation(errors.isEmpty(), List.copyOf(errors));
    }

    public static WorkflowDefinitionValidation ok() {
        return new WorkflowDefinitionValidation(true, List.of());
    }
}
