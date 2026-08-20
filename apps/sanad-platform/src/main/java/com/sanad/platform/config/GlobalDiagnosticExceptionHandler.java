package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Global diagnostic exception handler.
 *
 * <p>Catches unhandled exceptions and returns the exception message
 * (without the full stack trace) in the response body so that production
 * 500 root causes can be identified without access to server logs.
 *
 * <p>The full stack trace is logged at ERROR level for server-side
 * diagnostics.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalDiagnosticExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalDiagnosticExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception e) {
        log.error("Unhandled exception: type={} message={}", e.getClass().getName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "exceptionType", e.getClass().getSimpleName(),
                "message", e.getMessage() != null ? e.getMessage() : "No message available",
                "cause", e.getCause() != null ? e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() : "none"
        ));
    }
}
