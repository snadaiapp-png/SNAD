package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.AccountSummaryResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivityResponse;
import com.sanad.platform.crm.dto.CrmDtos.ArchiveAccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactResponse;
import com.sanad.platform.crm.dto.CrmDtos.Customer360Response;
import com.sanad.platform.crm.dto.CrmDtos.LeadConversionResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunityResponse;
import com.sanad.platform.crm.dto.CrmDtos.PipelineResponse;
import com.sanad.platform.crm.dto.CrmDtos.StageResponse;
import com.sanad.platform.crm.dto.CrmDtos.TimelineEventResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — DTO shape verification.
 * <p>
 * Verifies that every CRM response DTO:
 *   - Has an {@code id} field of type {@link UUID}.
 *   - Has a {@code version} field of type {@code long}.
 *   - Uses camelCase field names (no snake_case leakage).
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmAccountContractTest {

    @Test
    void accountResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(AccountResponse.class);
    }

    @Test
    void accountResponseUsesCamelCaseFieldNames() {
        assertCamelCase(AccountResponse.class);
    }

    @Test
    void accountSummaryResponseUsesCamelCaseFieldNames() {
        assertCamelCase(AccountSummaryResponse.class);
    }

    @Test
    void archiveAccountResponseHasIdAndVersion() {
        assertHasIdAndVersion(ArchiveAccountResponse.class);
    }

    @Test
    void customer360ResponseHasAccountField() throws Exception {
        RecordComponent[] comps = Customer360Response.class.getRecordComponents();
        boolean hasAccount = false;
        for (RecordComponent c : comps) if (c.getName().equals("account")) hasAccount = true;
        assertTrue(hasAccount, "Customer360Response must have an 'account' field");
    }

    @Test
    void contactResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(ContactResponse.class);
    }

    @Test
    void leadResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(LeadResponse.class);
    }

    @Test
    void leadConversionResponseHasIdempotentFlag() throws Exception {
        RecordComponent[] comps = LeadConversionResponse.class.getRecordComponents();
        boolean hasIdempotent = false;
        for (RecordComponent c : comps) if (c.getName().equals("idempotent")) hasIdempotent = true;
        assertTrue(hasIdempotent, "LeadConversionResponse must have an 'idempotent' boolean field");
    }

    @Test
    void opportunityResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(OpportunityResponse.class);
    }

    @Test
    void pipelineResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(PipelineResponse.class);
    }

    @Test
    void stageResponseUsesCamelCaseFieldNames() {
        assertCamelCase(StageResponse.class);
    }

    @Test
    void activityResponseHasIdAndVersionFields() {
        assertHasIdAndVersion(ActivityResponse.class);
    }

    @Test
    void timelineEventResponseUsesCamelCaseFieldNames() {
        assertCamelCase(TimelineEventResponse.class);
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private static void assertHasIdAndVersion(Class<? extends Record> type) {
        RecordComponent[] comps = type.getRecordComponents();
        boolean hasId = false, hasVersion = false;
        for (RecordComponent c : comps) {
            if (c.getName().equals("id") && c.getType() == UUID.class) hasId = true;
            if (c.getName().equals("version") && (c.getType() == long.class || c.getType() == Long.class)) hasVersion = true;
        }
        assertTrue(hasId, type.getSimpleName() + " must have a 'id: UUID' field");
        assertTrue(hasVersion, type.getSimpleName() + " must have a 'version: long' field");
    }

    private static void assertCamelCase(Class<? extends Record> type) {
        for (RecordComponent c : type.getRecordComponents()) {
            String name = c.getName();
            assertNotNull(name, type.getSimpleName() + " has a null field name");
            // First char must be lowercase (camelCase) — no snake_case, no PascalCase.
            assertTrue(Character.isLowerCase(name.charAt(0)),
                    type.getSimpleName() + " field '" + name + "' is not camelCase (must start lowercase)");
            assertTrue(!name.contains("_"),
                    type.getSimpleName() + " field '" + name + "' contains an underscore (snake_case forbidden in public contract)");
        }
    }
}
