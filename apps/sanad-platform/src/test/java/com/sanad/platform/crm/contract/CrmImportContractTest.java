package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.dto.CrmDtos;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — Import contract.
 * <p>
 * The Import domain has three response DTOs:
 *   - {@link CrmDtos.ImportJobResponse} (job summary)
 *   - {@link CrmDtos.ImportErrorResponse} (per-row error)
 *   - {@link CrmDtos.ImportRunResponse} (run status)
 * <p>
 * Verifies each DTO is a Java record with camelCase fields.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmImportContractTest {

    private final CrmDtoMapper mapper = new CrmDtoMapper();

    @Test
    void importJobResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.ImportJobResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "ImportJobResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "ImportJobResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void importErrorResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.ImportErrorResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "ImportErrorResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "ImportErrorResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void importRunResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.ImportRunResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "ImportRunResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "ImportRunResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void mapperIsPresent() {
        assertNotNull(mapper);
    }
}
