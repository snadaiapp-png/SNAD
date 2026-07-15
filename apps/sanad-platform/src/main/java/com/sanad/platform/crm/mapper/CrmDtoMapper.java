package com.sanad.platform.crm.mapper;

import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.AccountSummaryResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivityResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivitySummaryResponse;
import com.sanad.platform.crm.dto.CrmDtos.ArchiveAccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactSummaryResponse;
import com.sanad.platform.crm.dto.CrmDtos.Customer360Response;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldResponse;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldValuesResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportErrorResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportJobResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadConversionResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunityResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunitySummaryResponse;
import com.sanad.platform.crm.dto.CrmDtos.PipelineResponse;
import com.sanad.platform.crm.dto.CrmDtos.StageResponse;
import com.sanad.platform.crm.dto.CrmDtos.TimelineEventResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract — Mappers (snake_case DB rows → camelCase DTOs).
 * <p>
 * The CRM service layer returns {@code Map<String, Object>} rows whose
 * keys are PostgreSQL column names (snake_case). This class is the single
 * chokepoint where those rows are converted to typed DTOs with camelCase
 * field names.
 * <p>
 * Mappers are intentionally explicit (no reflection, no ModelMapper) so:
 *   - Adding a new column does NOT silently change the public contract.
 *   - Removing a column surfaces as a compile-time error.
 *   - Contract tests can verify the exact field set.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@Component
public class CrmDtoMapper {

    // ── Accounts ────────────────────────────────────────────────────────

    public AccountResponse toAccountResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new AccountResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("display_name")),
                str(row.get("normalized_name")),
                str(row.get("account_type")),
                str(row.get("lifecycle_status")),
                str(row.get("primary_currency_code")),
                str(row.get("preferred_locale")),
                str(row.get("time_zone")),
                str(row.get("source")),
                uuid(row.get("parent_account_id")),
                uuid(row.get("owner_user_id")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public AccountSummaryResponse toAccountSummary(Map<String, Object> row) {
        if (row == null) return null;
        return new AccountSummaryResponse(
                uuid(row.get("id")),
                str(row.get("display_name")),
                str(row.get("account_type")),
                str(row.get("lifecycle_status")),
                str(row.get("primary_currency_code")),
                offsetDateTime(row.get("updated_at")));
    }

    public ArchiveAccountResponse toArchiveAccountResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new ArchiveAccountResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("lifecycle_status")),
                offsetDateTime(row.get("updated_at")));
    }

    // ── Contacts ────────────────────────────────────────────────────────

    public ContactResponse toContactResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new ContactResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                uuid(row.get("account_id")),
                str(row.get("given_name")),
                str(row.get("family_name")),
                str(row.get("display_name")),
                str(row.get("primary_email")),
                str(row.get("normalized_email")),
                str(row.get("primary_phone")),
                str(row.get("preferred_locale")),
                str(row.get("time_zone")),
                str(row.get("lifecycle_status")),
                uuid(row.get("owner_user_id")),
                str(row.get("consent_summary")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public ContactSummaryResponse toContactSummary(Map<String, Object> row) {
        if (row == null) return null;
        return new ContactSummaryResponse(
                uuid(row.get("id")),
                uuid(row.get("account_id")),
                str(row.get("display_name")),
                str(row.get("primary_email")),
                str(row.get("primary_phone")),
                str(row.get("lifecycle_status")),
                offsetDateTime(row.get("updated_at")));
    }

    // ── Leads ───────────────────────────────────────────────────────────

    public LeadResponse toLeadResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new LeadResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("display_name")),
                str(row.get("company_name")),
                str(row.get("email")),
                str(row.get("phone")),
                str(row.get("source")),
                str(row.get("status")),
                uuid(row.get("owner_user_id")),
                bigDecimal(row.get("score")),
                uuid(row.get("converted_account_id")),
                uuid(row.get("converted_contact_id")),
                uuid(row.get("converted_opportunity_id")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    @SuppressWarnings("unchecked")
    public LeadConversionResponse toLeadConversionResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new LeadConversionResponse(
                toLeadResponse((Map<String, Object>) row.get("lead")),
                toAccountResponse((Map<String, Object>) row.get("account")),
                toContactResponse((Map<String, Object>) row.get("contact")),
                toOpportunityResponse((Map<String, Object>) row.get("opportunity")),
                Boolean.TRUE.equals(row.get("idempotent")));
    }

    // ── Pipelines & Stages ──────────────────────────────────────────────

    public PipelineResponse toPipelineResponse(Map<String, Object> row, List<Map<String, Object>> stages) {
        if (row == null) return null;
        return new PipelineResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("name")),
                str(row.get("currency_code")),
                boolVal(row.get("active")),
                stages == null ? List.of() : stages.stream().map(this::toStageResponse).toList(),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public StageResponse toStageResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new StageResponse(
                uuid(row.get("id")),
                uuid(row.get("pipeline_id")),
                str(row.get("name")),
                intVal(row.get("sequence")),
                bigDecimal(row.get("probability")),
                str(row.get("terminal_state")),
                boolVal(row.get("active")));
    }

    // ── Opportunities ───────────────────────────────────────────────────

    public OpportunityResponse toOpportunityResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new OpportunityResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                uuid(row.get("account_id")),
                uuid(row.get("contact_id")),
                uuid(row.get("pipeline_id")),
                uuid(row.get("stage_id")),
                str(row.get("name")),
                bigDecimal(row.get("amount")),
                str(row.get("currency_code")),
                bigDecimal(row.get("probability")),
                str(row.get("status")),
                str(row.get("win_loss_reason")),
                localDate(row.get("expected_close_date")),
                uuid(row.get("owner_user_id")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public OpportunitySummaryResponse toOpportunitySummary(Map<String, Object> row) {
        if (row == null) return null;
        return new OpportunitySummaryResponse(
                uuid(row.get("id")),
                uuid(row.get("account_id")),
                str(row.get("name")),
                bigDecimal(row.get("amount")),
                str(row.get("currency_code")),
                str(row.get("status")),
                offsetDateTime(row.get("updated_at")));
    }

    // ── Activities ──────────────────────────────────────────────────────

    public ActivityResponse toActivityResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new ActivityResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("activity_type")),
                str(row.get("subject")),
                str(row.get("body")),
                str(row.get("related_type")),
                uuid(row.get("related_id")),
                uuid(row.get("owner_user_id")),
                str(row.get("status")),
                intVal(row.get("priority")),
                offsetDateTime(row.get("start_at")),
                offsetDateTime(row.get("due_at")),
                offsetDateTime(row.get("completed_at")),
                str(row.get("result")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public ActivitySummaryResponse toActivitySummary(Map<String, Object> row) {
        if (row == null) return null;
        return new ActivitySummaryResponse(
                uuid(row.get("id")),
                str(row.get("activity_type")),
                str(row.get("subject")),
                str(row.get("status")),
                offsetDateTime(row.get("updated_at")));
    }

    // ── Timeline ────────────────────────────────────────────────────────

    public TimelineEventResponse toTimelineEventResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new TimelineEventResponse(
                uuid(row.get("id")),
                str(row.get("subject_type")),
                uuid(row.get("subject_id")),
                str(row.get("event_type")),
                str(row.get("summary")),
                str(row.get("source_type")),
                uuid(row.get("source_id")),
                offsetDateTime(row.get("occurred_at")),
                uuid(row.get("created_by")));
    }

    // ── Imports ─────────────────────────────────────────────────────────

    public ImportJobResponse toImportJobResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new ImportJobResponse(
                uuid(row.get("id")),
                str(row.get("entity_type")),
                str(row.get("file_name")),
                longVal(row.get("total_rows")),
                longVal(row.get("processed_rows")),
                longVal(row.get("successful_rows")),
                longVal(row.get("failed_rows")),
                str(row.get("status")),
                str(row.get("mapping_json")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public ImportErrorResponse toImportErrorResponse(Map<String, Object> row) {
        if (row == null) return null;
        Object rowData = row.get("row_data");
        Map<String, Object> rowDataMap = rowData instanceof Map ? (Map<String, Object>) rowData : Map.of();
        return new ImportErrorResponse(
                uuid(row.get("id")),
                uuid(row.get("job_id")),
                longVal(row.get("row_number")),
                str(row.get("error_code")),
                str(row.get("error_message")),
                rowDataMap);
    }

    // ── Custom Fields ───────────────────────────────────────────────────

    public CustomFieldResponse toCustomFieldResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new CustomFieldResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("entity_type")),
                str(row.get("field_key")),
                str(row.get("label_ar")),
                str(row.get("label_en")),
                str(row.get("data_type")),
                boolVal(row.get("sensitive")),
                boolVal(row.get("searchable")),
                boolVal(row.get("required")),
                boolVal(row.get("active")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    @SuppressWarnings("unchecked")
    public CustomFieldValuesResponse toCustomFieldValuesResponse(String entityType, UUID entityId, Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (row != null && row.get("values") instanceof Map m) {
            for (Object k : m.keySet()) values.put(String.valueOf(k), m.get(k));
        }
        return new CustomFieldValuesResponse(entityType, entityId, values);
    }

    // ── Customer 360 ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Customer360Response toCustomer360Response(
            Map<String, Object> accountRow,
            List<Map<String, Object>> contactRows,
            List<Map<String, Object>> opportunityRows,
            List<Map<String, Object>> activityRows,
            List<Map<String, Object>> timelineRows,
            Map<String, Object> customFieldValues) {
        return new Customer360Response(
                toAccountResponse(accountRow),
                contactRows == null ? List.of() : contactRows.stream().map(this::toContactSummary).toList(),
                opportunityRows == null ? List.of() : opportunityRows.stream().map(this::toOpportunitySummary).toList(),
                activityRows == null ? List.of() : activityRows.stream().map(this::toActivitySummary).toList(),
                timelineRows == null ? List.of() : timelineRows.stream().map(this::toTimelineEventResponse).toList(),
                customFieldValues == null ? Map.of() : customFieldValues);
    }

    // ── Primitive conversion helpers ────────────────────────────────────

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }

    private static UUID uuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        try { return UUID.fromString(String.valueOf(v)); } catch (IllegalArgumentException e) { return null; }
    }

    private static long longVal(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    private static int intVal(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return 0; }
    }

    private static boolean boolVal(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static OffsetDateTime offsetDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime odt) return odt;
        if (v instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (v instanceof java.util.Date d) return d.toInstant().atOffset(ZoneOffset.UTC);
        return null;
    }

    private static LocalDate localDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof java.sql.Date sd) return sd.toLocalDate();
        if (v instanceof java.util.Date d) return d.toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
        try { return LocalDate.parse(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Tags (feature/crm-tags)
    // ────────────────────────────────────────────────────────────────────

    public com.sanad.platform.crm.dto.CrmDtos.TagResponse toTagResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new com.sanad.platform.crm.dto.CrmDtos.TagResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("name")),
                str(row.get("color")),
    // Tasks (feature/crm-tasks)
    // ────────────────────────────────────────────────────────────────────

    public com.sanad.platform.crm.dto.CrmDtos.TaskResponse toTaskResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new com.sanad.platform.crm.dto.CrmDtos.TaskResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("title")),
                str(row.get("description")),
                str(row.get("related_type")),
                uuid(row.get("related_id")),
                uuid(row.get("assignee_user_id")),
                uuid(row.get("owner_user_id")),
                str(row.get("status")),
                row.get("priority") == null ? null : intVal(row.get("priority")),
                offsetDateTime(row.get("start_at")),
                offsetDateTime(row.get("due_at")),
                offsetDateTime(row.get("completed_at")),
                str(row.get("result")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public com.sanad.platform.crm.dto.CrmDtos.TagAssignmentResponse toTagAssignmentResponse(
            Map<String, Object> row, String tagName, String tagColor) {
        if (row == null) return null;
        return new com.sanad.platform.crm.dto.CrmDtos.TagAssignmentResponse(
                uuid(row.get("id")),
                uuid(row.get("tag_id")),
                tagName,
                tagColor,
                str(row.get("subject_type")),
                uuid(row.get("subject_id")),
                offsetDateTime(row.get("assigned_at")));
    public com.sanad.platform.crm.dto.CrmDtos.TaskSummaryResponse toTaskSummary(Map<String, Object> row) {
        if (row == null) return null;
        return new com.sanad.platform.crm.dto.CrmDtos.TaskSummaryResponse(
                uuid(row.get("id")),
                str(row.get("title")),
                str(row.get("status")),
                row.get("priority") == null ? null : intVal(row.get("priority")),
                offsetDateTime(row.get("due_at")),
                offsetDateTime(row.get("updated_at")));
    }

    // ────────────────────────────────────────────────────────────────────
    // Notes (feature/crm-notes)
    // ────────────────────────────────────────────────────────────────────

    public com.sanad.platform.crm.dto.CrmDtos.NoteResponse toNoteResponse(Map<String, Object> row) {
        if (row == null) return null;
        return new com.sanad.platform.crm.dto.CrmDtos.NoteResponse(
                uuid(row.get("id")),
                longVal(row.get("version")),
                str(row.get("subject_type")),
                uuid(row.get("subject_id")),
                str(row.get("body")),
                uuid(row.get("author_user_id")),
                boolVal(row.get("archived")),
                offsetDateTime(row.get("created_at")),
                offsetDateTime(row.get("updated_at")));
    }

    public com.sanad.platform.crm.dto.CrmDtos.NoteSummaryResponse toNoteSummary(Map<String, Object> row) {
        if (row == null) return null;
        String body = str(row.get("body"));
        String preview = body == null ? "" : (body.length() > 140 ? body.substring(0, 140) + "..." : body);
        return new com.sanad.platform.crm.dto.CrmDtos.NoteSummaryResponse(
                uuid(row.get("id")),
                uuid(row.get("author_user_id")),
                preview,
                offsetDateTime(row.get("created_at")));
    }
}
