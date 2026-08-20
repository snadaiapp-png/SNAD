package com.sanad.platform.crm.error;

import com.sanad.platform.crm.web.CrmAddressCommunicationController;
import com.sanad.platform.crm.web.CrmAddressCommunicationOperationsController;
import com.sanad.platform.crm.web.CrmCommunicationPolicyController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Applies the established CRM error envelope to the CRM-007 controllers.
 *
 * <p>The shared exception mappings remain defined once in
 * {@link CrmExceptionHandler}; this advice only extends their controller scope.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        CrmAddressCommunicationController.class,
        CrmAddressCommunicationOperationsController.class,
        CrmCommunicationPolicyController.class
})
public class CrmAddressCommunicationExceptionHandler extends CrmExceptionHandler {
}
