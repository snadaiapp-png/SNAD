package com.sanad.platform.platformiam.api;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.platformiam.exception.LastPlatformOwnerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sanad.platform.platformiam.api")
public class PlatformIamApiExceptionHandler {

    @ExceptionHandler(AccessResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(AccessResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "PLATFORM_IAM_NOT_FOUND", "Platform IAM resource not found");
    }

    @ExceptionHandler(AccessConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(AccessConflictException exception) {
        return response(HttpStatus.CONFLICT, "PLATFORM_IAM_CONFLICT", "Request conflicts with current state");
    }

    @ExceptionHandler(LastPlatformOwnerException.class)
    public ResponseEntity<Map<String, Object>> lastOwner(LastPlatformOwnerException exception) {
        return response(HttpStatus.CONFLICT, LastPlatformOwnerException.REASON_CODE,
                "The final active platform owner cannot be removed");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "PLATFORM_IAM_FORBIDDEN", "Authorization required");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "PLATFORM_IAM_BAD_REQUEST", "Invalid request");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> stateConflict(IllegalStateException exception) {
        return response(HttpStatus.CONFLICT, "PLATFORM_IAM_CONFLICT", "Request conflicts with current state");
    }

    private static ResponseEntity<Map<String, Object>> response(
            HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
