package com.sanad.platform.hr.compliance.domain;

import java.util.List;

/**
 * Typed outcome of interpreting a versioned compliance rule against an
 * evaluation context. Rule parameters are DATA only; the interpretation is
 * performed by a registered rule handler, never by dynamic code execution.
 */
public record RuleEvaluation(
        boolean violation,
        String reasonCode,
        List<String> warnings) {

    public RuleEvaluation {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
