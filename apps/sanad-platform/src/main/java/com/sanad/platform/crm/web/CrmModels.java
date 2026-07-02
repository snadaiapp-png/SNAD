package com.sanad.platform.crm.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record CreateAccountRequest(
        @NotBlank @Size(max = 240) String displayName,
        @Pattern(regexp = "BUSINESS|PERSON|PARTNER|PROSPECT|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE) String accountType,
        UUID ownerUserId,
        UUID parentAccountId,
        @Pattern(regexp = "[A-Za-z]{3}") String primaryCurrencyCode,
        @Size(max = 35) String preferredLocale,
        @Size(max = 64) String timeZone,
        @Size(max = 80) String source) { }

record UpdateAccountRequest(
        @Size(max = 240) String displayName,
        UUID ownerUserId,
        UUID parentAccountId,
        @Pattern(regexp = "[A-Za-z]{3}") String primaryCurrencyCode,
        @Size(max = 35) String preferredLocale,
        @Size(max = 64) String timeZone,
        @Size(max = 80) String source) { }

record CreateContactRequest(
        UUID accountId,
        @NotBlank @Size(max = 120) String givenName,
        @Size(max = 120) String familyName,
        @Email @Size(max = 255) String primaryEmail,
        @Size(max = 64) String primaryPhone,
        @Size(max = 35) String preferredLocale,
        @Size(max = 64) String timeZone,
        UUID ownerUserId,
        @Pattern(regexp = "UNKNOWN|GRANTED|DENIED|WITHDRAWN", flags = Pattern.Flag.CASE_INSENSITIVE) String consentSummary) { }

record UpdateContactRequest(
        UUID accountId,
        @Size(max = 120) String givenName,
        @Size(max = 120) String familyName,
        @Email @Size(max = 255) String primaryEmail,
        @Size(max = 64) String primaryPhone,
        @Size(max = 35) String preferredLocale,
        @Size(max = 64) String timeZone,
        UUID ownerUserId,
        @Pattern(regexp = "UNKNOWN|GRANTED|DENIED|WITHDRAWN", flags = Pattern.Flag.CASE_INSENSITIVE) String consentSummary) { }

record CreateLeadRequest(
        @NotBlank @Size(max = 240) String displayName,
        @Size(max = 240) String companyName,
        @Email @Size(max = 255) String email,
        @Size(max = 64) String phone,
        @Size(max = 120) String source,
        UUID ownerUserId,
        UUID queueId,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal score) { }

record UpdateLeadStatusRequest(
        @NotBlank @Pattern(regexp = "NEW|ASSIGNED|CONTACTED|QUALIFIED|DISQUALIFIED|ARCHIVED", flags = Pattern.Flag.CASE_INSENSITIVE) String status,
        @Size(max = 500) String reason) { }

record ConvertLeadRequest(
        @Size(max = 240) String accountName,
        Boolean createOpportunity,
        UUID pipelineId,
        UUID stageId,
        @Size(max = 240) String opportunityName,
        @DecimalMin("0.0") BigDecimal amount,
        @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        LocalDate expectedCloseDate) { }

record CreatePipelineRequest(
        @NotBlank @Size(max = 160) String name,
        @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @Size(min = 2, max = 20) List<@NotBlank @Size(max = 160) String> stages) { }

record CreateOpportunityRequest(
        @NotNull UUID accountId,
        UUID contactId,
        @NotNull UUID pipelineId,
        @NotNull UUID stageId,
        @NotBlank @Size(max = 240) String name,
        @DecimalMin("0.0") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        LocalDate expectedCloseDate,
        UUID ownerUserId) { }

record MoveOpportunityRequest(
        @NotNull UUID stageId,
        @Pattern(regexp = "OPEN|CANCELLED", flags = Pattern.Flag.CASE_INSENSITIVE) String status,
        @Size(max = 500) String reason) { }

record CreateActivityRequest(
        @NotBlank @Pattern(regexp = "TASK|CALL|MEETING|EMAIL|NOTE|MESSAGE|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE) String activityType,
        @NotBlank @Size(max = 240) String subject,
        @Size(max = 4000) String body,
        @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY", flags = Pattern.Flag.CASE_INSENSITIVE) String relatedType,
        UUID relatedId,
        UUID ownerUserId,
        @Min(0) @Max(100) Integer priority,
        OffsetDateTime startAt,
        OffsetDateTime dueAt) { }

record CompleteActivityRequest(@Size(max = 4000) String result) { }

record CreateImportJobRequest(
        @NotBlank @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY", flags = Pattern.Flag.CASE_INSENSITIVE) String entityType,
        @Min(0) Long totalRows) { }

record CreateCustomFieldRequest(
        @NotBlank @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY", flags = Pattern.Flag.CASE_INSENSITIVE) String entityType,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,119}") String fieldKey,
        @NotBlank @Size(max = 240) String labelAr,
        @NotBlank @Size(max = 240) String labelEn,
        @NotBlank @Size(max = 32) String dataType,
        Boolean sensitive,
        Boolean searchable,
        Boolean required) { }
