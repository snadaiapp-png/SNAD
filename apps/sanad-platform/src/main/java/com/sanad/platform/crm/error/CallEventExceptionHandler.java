package com.sanad.platform.crm.error;

import com.sanad.platform.crm.calls.web.CallEventController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Applies the established CRM error envelope to the G8 call-event controller
 * (G8 EXECUTION 03). Shared mappings stay in {@link CrmExceptionHandler}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        CallEventController.class
})
public class CallEventExceptionHandler extends CrmExceptionHandler {
}
