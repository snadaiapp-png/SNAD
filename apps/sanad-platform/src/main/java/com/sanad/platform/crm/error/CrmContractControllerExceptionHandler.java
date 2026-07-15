package com.sanad.platform.crm.error;

import com.sanad.platform.crm.web.CrmContractController;
import com.sanad.platform.crm.web.CrmContractControllerR1;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Applies the canonical CRM error envelope to the governed V2 contract controllers.
 * The legacy controller remains covered by {@link CrmExceptionHandler} directly.
 */
@RestControllerAdvice(assignableTypes = {
        CrmContractController.class,
        CrmContractControllerR1.class
})
public class CrmContractControllerExceptionHandler extends CrmExceptionHandler {
}
