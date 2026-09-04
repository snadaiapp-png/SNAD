package com.sanad.platform.hr.api.v2;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HRM-G0 / WS5 Task 2 — canonical HRM v2 error-model projection.
 *
 * <p>Binds to every controller under {@code com.sanad.platform.hr.api.v2}
 * (the canonical 58-operation surface, Task 3+) and projects domain
 * failures onto the stable envelope:
 *
 * <ul>
 *   <li>{@link HrDomainException} → the code's fixed HTTP status</li>
 *   <li>legacy text-prefixed {@code HRM_*} {@link IllegalStateException} /
 *       {@link IllegalArgumentException} raised by WS2..WS6 services → the
 *       identical envelope without modifying any service code</li>
 *   <li>bean validation ({@link MethodArgumentNotValidException}) → 400 with
 *       per-field violations</li>
 * </ul>
 *
 * <p>Codes carrying no canonical envelope meaning (e.g. operational
 * append/claim failures) are re-thrown untouched: they remain the platform's
 * diagnostic 5xx surface and must never masquerade as client-facing HRM
 * semantics. Status is derived exclusively from the stable code, never from
 * message text, so localization cannot alter the contract.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sanad.platform.hr.api.v2")
public class HrApiExceptionHandler {

    /**
     * Legacy services raise {@code "HRM_SOME_CODE: human message"}. Only a
     * strict prefix parse participates in the envelope; anything else falls
     * through to the platform-wide diagnostic handling.
     */
    private static final Pattern LEGACY_CODE_PREFIX = Pattern.compile("^(HRM_[A-Z_]+):");

    @ExceptionHandler(HrDomainException.class)
    ResponseEntity<HrApiErrorResponse> onDomainException(HrDomainException ex) {
        return ResponseEntity.status(ex.code().httpStatus())
                .body(HrApiErrorResponse.of(ex.code(), ex.getMessage(), ex.violations()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<HrApiErrorResponse> onValidationFailure(MethodArgumentNotValidException ex) {
        List<HrApiErrorResponse.Violation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                violations.add(HrApiErrorResponse.Violation.of(error.getField(), error.getDefaultMessage())));
        return ResponseEntity.badRequest()
                .body(HrApiErrorResponse.of(HrApiErrorCode.HRM_VALIDATION_FAILED,
                        "Request failed validation", violations));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<HrApiErrorResponse> onIllegalState(IllegalStateException ex) throws IllegalStateException {
        return projectLegacy(ex.getMessage(), ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<HrApiErrorResponse> onIllegalArgument(IllegalArgumentException ex)
            throws IllegalArgumentException {
        return projectLegacy(ex.getMessage(), ex);
    }

    private ResponseEntity<HrApiErrorResponse> projectLegacy(String message, RuntimeException original)
            throws RuntimeException {
        if (message == null) {
            throw original;
        }
        Matcher matcher = LEGACY_CODE_PREFIX.matcher(message);
        if (!matcher.find()) {
            throw original;
        }
        HrApiErrorCode code;
        try {
            code = HrApiErrorCode.valueOf(matcher.group(1));
        } catch (IllegalArgumentException unrecognized) {
            // Operational/internal HRM codes keep platform diagnostic semantics.
            throw original;
        }
        return ResponseEntity.status(code.httpStatus())
                .body(HrApiErrorResponse.of(code, message));
    }
}
