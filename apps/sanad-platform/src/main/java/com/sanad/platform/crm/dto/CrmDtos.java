package com.sanad.platform.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract — Response / Request DTOs (camelCase).
 * <p>
 * These records form the public, typed API contract for the CRM module.
 * They are consumed by:
 *   - The OpenAPI generator (springdoc-openapi) to produce the schema artifact.
 *   - The frontend type generator (openapi-typescript) to produce TS types.
 *   - The contract tests under apps/sanad-platform/src/test/java/.../crm/contract/.
 * <p>
 * Field naming MUST be camelCase. Database column names (snake_case) are
 * converted to camelCase by the mappers in {@code com.sanad.platform.crm.mapper}.
 * <p>
 * Branch: crm/003-stable-api-contracts
 * Gate: CRM-G2 — API Contract and Concurrency Gate
 */
public final class CrmDtos {

    private CrmDtos() {}

    // ────────────────────────────────────────────────────────────────────
    // Accounts
    // ────────────────────────────────────────────────────────────────────

    public record AccountResponse(
            UUID id,
            long version,
            String displayName,
            String normalizedDisplayName,
            String accountType,
            String lifecycleStatus,
            String primaryCurrencyCode,
            String preferredLocale,
            String timeZone,
            String source,
            UUID parentAccountId,
            UUID ownerUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AccountSummaryResponse(
            UUID id,
            String displayName,
            String accountType,
            String lifecycleStatus,
            String primaryCurrencyCode,
            OffsetDateTime updatedAt) {}

    public record ArchiveAccountResponse(
            UUID id,
            long version,
            String lifecycleStatus,
            OffsetDateTime updatedAt) {}

    public record Customer360Response(
            AccountResponse account,
            List<ContactSummaryResponse> contacts,
            List<OpportunitySummaryResponse> opportunities,
            List<ActivitySummaryResponse> activities,
            List<TimelineEventResponse> timeline,
            Map<String, Object> customFields) {}

    // ────────────────────────────────────────────────────────────────────
    // Contacts
    // ────────────────────────────────────────────────────────────────────

    public record ContactResponse(
            UUID id,
            long version,
            UUID accountId,
            String givenName,
            String familyName,
            String displayName,
            String primaryEmail,
            String normalizedEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String lifecycleStatus,
            UUID ownerUserId,
            String consentSummary,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record ContactSummaryResponse(
            UUID id,
            UUID accountId,
            String displayName,
            String primaryEmail,
            String primaryPhone,
            String lifecycleStatus,
            OffsetDateTime updatedAt) {}

    // ────────────────────────────────────────────────────────────────────
    // Leads
    // ────────────────────────────────────────────────────────────────────

    public record LeadResponse(
            UUID id,
            long version,
            String displayName,
            String companyName,
            String email,
            String phone,
            String source,
            String status,
            UUID ownerUserId,
            BigDecimal score,
            UUID convertedAccountId,
            UUID convertedContactId,
            UUID convertedOpportunityId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record LeadConversionResponse(
            LeadResponse lead,
            AccountResponse account,
            ContactResponse contact,
            OpportunityResponse opportunity,
            boolean idempotent) {}

    // ────────────────────────────────────────────────────────────────────
    // Pipelines and Stages
    // ────────────────────────────────────────────────────────────────────

    public record PipelineResponse(
            UUID id,
            long version,
            String name,
            String currencyCode,
            boolean active,
            List<StageResponse> stages,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record StageResponse(
            UUID id,
            UUID pipelineId,
            String name,
            int sequence,
            BigDecimal probability,
            String terminalState,
            boolean active) {}

    // ────────────────────────────────────────────────────────────────────
    // Opportunities
    // ────────────────────────────────────────────────────────────────────

    public record OpportunityResponse(
            UUID id,
            long version,
            UUID accountId,
            UUID contactId,
            UUID pipelineId,
            UUID stageId,
            String name,
            BigDecimal amount,
            String currencyCode,
            BigDecimal probability,
            String status,
            String winLossReason,
            LocalDate expectedCloseDate,
            UUID ownerUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record OpportunitySummaryResponse(
            UUID id,
            UUID accountId,
            String name,
            BigDecimal amount,
            String currencyCode,
            String status,
            OffsetDateTime updatedAt) {}

    // ────────────────────────────────────────────────────────────────────
    // Activities
    // ────────────────────────────────────────────────────────────────────

    public record ActivityResponse(
            UUID id,
            long version,
            String activityType,
            String subject,
            String body,
            String relatedType,
            UUID relatedId,
            UUID ownerUserId,
            String status,
            Integer priority,
            OffsetDateTime startAt,
            OffsetDateTime dueAt,
            OffsetDateTime completedAt,
            String result,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record ActivitySummaryResponse(
            UUID id,
            String activityType,
            String subject,
            String status,
            OffsetDateTime updatedAt) {}

    // ────────────────────────────────────────────────────────────────────
    // Timeline
    // ────────────────────────────────────────────────────────────────────

    public record TimelineEventResponse(
            UUID id,
            String subjectType,
            UUID subjectId,
            String eventType,
            String summary,
            String sourceType,
            UUID sourceId,
            OffsetDateTime occurredAt,
            UUID createdBy) {}

    // ────────────────────────────────────────────────────────────────────
    // Imports
    // ────────────────────────────────────────────────────────────────────

    public record ImportJobResponse(
            UUID id,
            String entityType,
            String fileName,
            long totalRows,
            long processedRows,
            long successfulRows,
            long failedRows,
            String status,
            String mappingJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record ImportErrorResponse(
            UUID id,
            UUID jobId,
            long rowNumber,
            String errorCode,
            String errorMessage,
            Map<String, Object> rowData) {}

    public record ImportRunResponse(
            UUID jobId,
            String status,
            long processedRows,
            long successfulRows,
            long failedRows,
            OffsetDateTime updatedAt) {}

    // ────────────────────────────────────────────────────────────────────
    // Custom Fields
    // ────────────────────────────────────────────────────────────────────

    public record CustomFieldResponse(
            UUID id,
            long version,
            String entityType,
            String fieldKey,
            String labelAr,
            String labelEn,
            String dataType,
            boolean sensitive,
            boolean searchable,
            boolean required,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record CustomFieldValuesResponse(
            String entityType,
            UUID entityId,
            Map<String, Object> values) {}

    // ────────────────────────────────────────────────────────────────────
    // Notes (feature/crm-notes)
    // ────────────────────────────────────────────────────────────────────

    public record NoteResponse(
            UUID id,
            long version,
            String subjectType,
            UUID subjectId,
            String body,
            UUID authorUserId,
            boolean archived,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record NoteSummaryResponse(
            UUID id,
            UUID authorUserId,
            String bodyPreview,
            OffsetDateTime createdAt) {}
    // Tasks (feature/crm-tasks)
    // ────────────────────────────────────────────────────────────────────

    public record TaskResponse(
            UUID id,
            long version,
            String title,
            String description,
            String relatedType,
            UUID relatedId,
            UUID assigneeUserId,
            UUID ownerUserId,
            String status,
            Integer priority,
            OffsetDateTime startAt,
            OffsetDateTime dueAt,
            OffsetDateTime completedAt,
            String result,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record TaskSummaryResponse(
            UUID id,
            String title,
            String status,
            Integer priority,
            OffsetDateTime dueAt,
            OffsetDateTime updatedAt) {}
}
