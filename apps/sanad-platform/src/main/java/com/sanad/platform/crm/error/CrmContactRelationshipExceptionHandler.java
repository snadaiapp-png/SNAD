package com.sanad.platform.crm.error;

import com.sanad.platform.crm.web.CrmContactRelationshipController;
import com.sanad.platform.crm.web.CrmContactRelationshipImportController;
import com.sanad.platform.crm.web.CrmContactRelationshipVersionedMutationController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Applies the established CRM error contract to the CRM-006 relationship
 * controllers. Handler methods are inherited from {@link CrmExceptionHandler},
 * while the assignable-type scope prevents interference with non-CRM APIs.
 */
@RestControllerAdvice(assignableTypes = {
        CrmContactRelationshipController.class,
        CrmContactRelationshipImportController.class,
        CrmContactRelationshipVersionedMutationController.class
})
public class CrmContactRelationshipExceptionHandler extends CrmExceptionHandler {
}
