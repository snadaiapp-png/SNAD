package com.sanad.platform.crm.error;

import com.sanad.platform.crm.caller.web.CallerIdentificationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Applies the established CRM error envelope to the G8 caller-identification
 * controller (G8 EXECUTION 02).
 *
 * <p>The shared exception mappings remain defined once in
 * {@link CrmExceptionHandler}; this advice only extends their controller scope.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        CallerIdentificationController.class
})
public class CallerIdentificationExceptionHandler extends CrmExceptionHandler {
}
