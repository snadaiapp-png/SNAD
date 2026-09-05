package com.sanad.platform.hr.api.v2;

import java.util.List;

/**
 * HRM-G0 / WS5 Task 2 — typed domain exception carrying a canonical
 * {@link HrApiErrorCode} and optional field-level violations.
 *
 * <p>Raised by v2-facing application code; projected to clients by
 * {@link HrApiExceptionHandler} using the code's fixed HTTP status. Message
 * text is never used to infer status or code.
 */
public class HrDomainException extends RuntimeException {

    private final HrApiErrorCode code;
    private final List<HrApiErrorResponse.Violation> violations;

    public HrDomainException(HrApiErrorCode code, String message) {
        this(code, message, List.of());
    }

    public HrDomainException(HrApiErrorCode code, String message, List<HrApiErrorResponse.Violation> violations) {
        super(message);
        this.code = code;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public HrApiErrorCode code() {
        return code;
    }

    public List<HrApiErrorResponse.Violation> violations() {
        return violations;
    }

    public static HrDomainException of(HrApiErrorCode code, String message,
                                       List<HrApiErrorResponse.Violation> violations) {
        return new HrDomainException(code, message, violations);
    }
}
