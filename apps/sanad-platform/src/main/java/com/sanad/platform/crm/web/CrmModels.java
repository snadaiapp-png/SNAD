package com.sanad.platform.crm.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record CreateAccountRequest(String displayName, String accountType, UUID ownerUserId, UUID parentAccountId, String primaryCurrencyCode, String preferredLocale, String timeZone, String source) { }
record UpdateAccountRequest(String displayName, UUID ownerUserId, UUID parentAccountId, String primaryCurrencyCode, String preferredLocale, String timeZone, String source) { }
record CreateContactRequest(UUID accountId, String givenName, String familyName, String primaryEmail, String primaryPhone, String preferredLocale, String timeZone, UUID ownerUserId, String consentSummary) { }
record UpdateContactRequest(UUID accountId, String givenName, String familyName, String primaryEmail, String primaryPhone, String preferredLocale, String timeZone, UUID ownerUserId, String consentSummary) { }
record CreateLeadRequest(String displayName, String companyName, String email, String phone, String source, UUID ownerUserId, UUID queueId, BigDecimal score) { }
record UpdateLeadStatusRequest(String status, String reason) { }
record ConvertLeadRequest(String accountName, Boolean createOpportunity, UUID pipelineId, UUID stageId, String opportunityName, BigDecimal amount, String currencyCode, LocalDate expectedCloseDate) { }
record CreatePipelineRequest(String name, String currencyCode, List<String> stages) { }
record CreateOpportunityRequest(UUID accountId, UUID contactId, UUID pipelineId, UUID stageId, String name, BigDecimal amount, String currencyCode, LocalDate expectedCloseDate, UUID ownerUserId) { }
record MoveOpportunityRequest(UUID stageId, String status, String reason) { }
record CreateActivityRequest(String activityType, String subject, String body, String relatedType, UUID relatedId, UUID ownerUserId, Integer priority, OffsetDateTime startAt, OffsetDateTime dueAt) { }
record CompleteActivityRequest(String result) { }
