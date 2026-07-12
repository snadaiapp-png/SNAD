package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.dto.CrmDtos;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CRM-G2 Contract Test — Mapper (snake_case DB row → camelCase DTO).
 * <p>
 * Verifies that the {@link CrmDtoMapper} correctly converts raw JDBC row
 * Maps (snake_case column names) to typed DTOs (camelCase field names)
 * for every CRM entity. This is the single chokepoint that prevents
 * internal column names from leaking into the public API contract.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmMapperContractTest {

    private final CrmDtoMapper mapper = new CrmDtoMapper();

    @Test
    void mapsAccountRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Map<String, Object> row = baseAccountRow(id, ownerId, 7L, "ACTIVE");
        CrmDtos.AccountResponse dto = mapper.toAccountResponse(row);
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals(7L, dto.version());
        assertEquals("Acme Corp", dto.displayName());
        assertEquals("acme corp", dto.normalizedDisplayName());
        assertEquals("BUSINESS", dto.accountType());
        assertEquals("ACTIVE", dto.lifecycleStatus());
        assertEquals("SAR", dto.primaryCurrencyCode());
        assertEquals("ar-SA", dto.preferredLocale());
        assertEquals("Asia/Riyadh", dto.timeZone());
        assertEquals(ownerId, dto.ownerUserId());
        assertNotNull(dto.createdAt());
        assertNotNull(dto.updatedAt());
    }

    @Test
    void mapsContactRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 3L);
        row.put("account_id", accountId);
        row.put("given_name", "Aisha");
        row.put("family_name", "Al-Saud");
        row.put("display_name", "Aisha Al-Saud");
        row.put("primary_email", "aisha@example.com");
        row.put("normalized_email", "aisha@example.com");
        row.put("primary_phone", "+966500000001");
        row.put("preferred_locale", "ar-SA");
        row.put("time_zone", "Asia/Riyadh");
        row.put("lifecycle_status", "ACTIVE");
        row.put("owner_user_id", UUID.randomUUID());
        row.put("consent_summary", "GRANTED");
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.ContactResponse dto = mapper.toContactResponse(row);
        assertEquals(id, dto.id());
        assertEquals(3L, dto.version());
        assertEquals(accountId, dto.accountId());
        assertEquals("Aisha", dto.givenName());
        assertEquals("Al-Saud", dto.familyName());
        assertEquals("Aisha Al-Saud", dto.displayName());
        assertEquals("aisha@example.com", dto.primaryEmail());
        assertEquals("GRANTED", dto.consentSummary());
    }

    @Test
    void mapsLeadRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 2L);
        row.put("display_name", "Lead A");
        row.put("company_name", "Acme");
        row.put("email", "lead@example.com");
        row.put("phone", "+966500000002");
        row.put("source", "WEB_FORM");
        row.put("status", "NEW");
        row.put("owner_user_id", UUID.randomUUID());
        row.put("score", new java.math.BigDecimal("50.0"));
        row.put("converted_account_id", null);
        row.put("converted_contact_id", null);
        row.put("converted_opportunity_id", null);
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.LeadResponse dto = mapper.toLeadResponse(row);
        assertEquals(id, dto.id());
        assertEquals(2L, dto.version());
        assertEquals("Lead A", dto.displayName());
        assertEquals("NEW", dto.status());
        assertEquals(new java.math.BigDecimal("50.0"), dto.score());
        assertNull(dto.convertedAccountId());
    }

    @Test
    void mapsOpportunityRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 0L);
        row.put("account_id", accountId);
        row.put("contact_id", null);
        row.put("pipeline_id", pipelineId);
        row.put("stage_id", stageId);
        row.put("name", "Big Deal");
        row.put("amount", new java.math.BigDecimal("25000.000000"));
        row.put("currency_code", "SAR");
        row.put("probability", new java.math.BigDecimal("10.00"));
        row.put("status", "OPEN");
        row.put("win_loss_reason", null);
        row.put("expected_close_date", null);
        row.put("owner_user_id", UUID.randomUUID());
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.OpportunityResponse dto = mapper.toOpportunityResponse(row);
        assertEquals(id, dto.id());
        assertEquals(accountId, dto.accountId());
        assertEquals(pipelineId, dto.pipelineId());
        assertEquals(stageId, dto.stageId());
        assertEquals("Big Deal", dto.name());
        assertEquals("SAR", dto.currencyCode());
        assertEquals("OPEN", dto.status());
    }

    @Test
    void mapsActivityRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 0L);
        row.put("activity_type", "TASK");
        row.put("subject", "Follow up");
        row.put("body", "Call Aisha");
        row.put("related_type", "ACCOUNT");
        row.put("related_id", UUID.randomUUID());
        row.put("owner_user_id", UUID.randomUUID());
        row.put("status", "OPEN");
        row.put("priority", 50);
        row.put("start_at", null);
        row.put("due_at", Timestamp.from(Instant.now().plusSeconds(3600)));
        row.put("completed_at", null);
        row.put("result", null);
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.ActivityResponse dto = mapper.toActivityResponse(row);
        assertEquals(id, dto.id());
        assertEquals("TASK", dto.activityType());
        assertEquals("Follow up", dto.subject());
        assertEquals("OPEN", dto.status());
        assertEquals(50, dto.priority());
    }

    @Test
    void mapsImportJobRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("entity_type", "ACCOUNT");
        row.put("file_name", "accounts.csv");
        row.put("total_rows", 100L);
        row.put("processed_rows", 100L);
        row.put("successful_rows", 95L);
        row.put("failed_rows", 5L);
        row.put("status", "COMPLETED");
        row.put("mapping_json", "{\"displayName\":\"name\"}");
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.ImportJobResponse dto = mapper.toImportJobResponse(row);
        assertEquals(id, dto.id());
        assertEquals("ACCOUNT", dto.entityType());
        assertEquals("accounts.csv", dto.fileName());
        assertEquals(100L, dto.totalRows());
        assertEquals(95L, dto.successfulRows());
        assertEquals(5L, dto.failedRows());
        assertEquals("COMPLETED", dto.status());
    }

    @Test
    void mapsCustomFieldRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("entity_type", "ACCOUNT");
        row.put("field_key", "industry");
        row.put("label_ar", "القطاع");
        row.put("label_en", "Industry");
        row.put("data_type", "TEXT");
        row.put("sensitive", false);
        row.put("searchable", true);
        row.put("required", false);
        row.put("active", true);
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        CrmDtos.CustomFieldResponse dto = mapper.toCustomFieldResponse(row);
        assertEquals(id, dto.id());
        assertEquals("industry", dto.fieldKey());
        assertEquals("Industry", dto.labelEn());
        assertEquals("TEXT", dto.dataType());
        assertTrue(dto.searchable());
        assertTrue(dto.active());
    }

    @Test
    void nullRowProducesNullDto() {
        assertNull(mapper.toAccountResponse(null));
        assertNull(mapper.toContactResponse(null));
        assertNull(mapper.toLeadResponse(null));
        assertNull(mapper.toOpportunityResponse(null));
        assertNull(mapper.toActivityResponse(null));
    }

    private static Map<String, Object> baseAccountRow(UUID id, UUID ownerId, long version, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", version);
        row.put("display_name", "Acme Corp");
        row.put("normalized_name", "acme corp");
        row.put("account_type", "BUSINESS");
        row.put("lifecycle_status", status);
        row.put("primary_currency_code", "SAR");
        row.put("preferred_locale", "ar-SA");
        row.put("time_zone", "Asia/Riyadh");
        row.put("source", "WEB_FORM");
        row.put("parent_account_id", null);
        row.put("owner_user_id", ownerId);
        row.put("created_at", Timestamp.from(Instant.now()));
        row.put("updated_at", Timestamp.from(Instant.now()));
        return row;
    }

    private static void assertTrue(boolean condition) { org.junit.jupiter.api.Assertions.assertTrue(condition); }
}
