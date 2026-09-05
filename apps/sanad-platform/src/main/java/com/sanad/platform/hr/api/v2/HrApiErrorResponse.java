package com.sanad.platform.hr.api.v2;

import java.util.List;

/**
 * HRM-G0 / WS5 Task 2 — canonical HRM v2 error envelope.
 *
 * <p>Shape (stable contract for all 58 canonical v2 operations):
 * <pre>{@code
 * {
 *   "code": "HRM_ACTIVATION_BLOCKED",
 *   "message": "...",
 *   "violations": [ { "field": "...", "message": "..." } ]
 * }
 * }</pre>
 *
 * <p>{@code code} is a stable machine-readable identifier (see
 * {@link HrApiErrorCode}); {@code message} is human-facing and may be
 * localized; {@code violations} carries field-level details (bean validation
 * failures or domain constraint details) and is always present (possibly
 * empty) so clients can rely on a uniform shape.
 */
public record HrApiErrorResponse(String code, String message, List<Violation> violations) {

    public HrApiErrorResponse {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * One field-level violation detail. Field paths are stable identifier
     * paths (DTO component names); messages are human-facing.
     */
    public record Violation(String field, String message) {

        public static Violation of(String field, String message) {
            return new Violation(field, message);
        }
    }

    public static HrApiErrorResponse of(HrApiErrorCode code, String message) {
        return new HrApiErrorResponse(code.name(), message, List.of());
    }

    public static HrApiErrorResponse of(HrApiErrorCode code, String message, List<Violation> violations) {
        return new HrApiErrorResponse(code.name(), message, violations);
    }
}
