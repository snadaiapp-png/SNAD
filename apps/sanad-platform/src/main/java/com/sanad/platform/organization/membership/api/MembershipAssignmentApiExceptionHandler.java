package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipUserLinkConflictException;
import com.sanad.platform.user.api.UserMembershipController;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        OrganizationMembershipAssignmentController.class,
        UserMembershipController.class
})
public class MembershipAssignmentApiExceptionHandler {

    @ExceptionHandler(OrganizationMembershipUserLinkConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            OrganizationMembershipUserLinkConflictException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({OrganizationMembershipNotFoundException.class,
            EntityNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Missing required query parameter: " + exception.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter: " + exception.getName(), request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
