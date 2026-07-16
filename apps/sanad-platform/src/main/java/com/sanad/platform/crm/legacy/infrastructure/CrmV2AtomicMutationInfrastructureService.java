package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.*;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Version-scoped mutations used exclusively by the CRM v2 HTTP contract.
 * The expected version participates in the UPDATE statement itself so a
 * successful If-Match check cannot be invalidated by a concurrent writer.
 */
@Service
public class CrmV2AtomicMutationInfrastructureService {
    private static final Map<String, Set<String>> LEAD_TRANSITIONS = Map.of(
            "NEW", Set.of("ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED"),
            "ASSIGNED", Set.of("CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED"),
            "CONTACTED", Set.of("QUALIFIED", "DISQUALIFIED", "ARCHIVED"),
            "QUALIFIED", Set.of("DISQUALIFIED", "ARCHIVED"),
            "DISQUALIFIED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of());

    private final NamedParameterJdbcTemplate jdbc;

    public CrmV2AtomicMutationInfrastructureService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> updateAccount(
            Authentication authentication,
            UUID accountId,
            UpdateAccountRequest request,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> current = row("crm_accounts", tenantId, accountId, CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        if ("ARCHIVED".equals(current.get("lifecycle_status"))) {
            throw new CrmContractException(CrmErrorCode.CONFLICT, "Archived CRM account cannot be updated.");
        }
        if (request.parentAccountId() != null && request.parentAccountId().equals(accountId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "An account cannot be its own parent.");
        }
        Instant now = Instant.now();
        MapSqlParameterSource params = context(tenantId, actorId, accountId, expectedVersion, now)
                .addValue("displayName", clean(request.displayName(), 240))
                .addValue("normalizedName", request.displayName() == null ? null : normalize(request.displayName()))
                .addValue("parentAccountId", request.parentAccountId())
                .addValue("ownerUserId", request.ownerUserId())
                .addValue("currency", currency(request.primaryCurrencyCode()))
                .addValue("locale", locale(request.preferredLocale()))
                .addValue("timeZone", zone(request.timeZone()))
                .addValue("source", clean(request.source(), 80));
        int updated = jdbc.update(
                "UPDATE crm_accounts SET "
                        + "display_name=COALESCE(:displayName,display_name),"
                        + "normalized_name=COALESCE(:normalizedName,normalized_name),"
                        + "parent_account_id=COALESCE(:parentAccountId,parent_account_id),"
                        + "owner_user_id=COALESCE(:ownerUserId,owner_user_id),"
                        + "primary_currency_code=COALESCE(:currency,primary_currency_code),"
                        + "preferred_locale=COALESCE(:locale,preferred_locale),"
                        + "time_zone=COALESCE(:timeZone,time_zone),"
                        + "source=COALESCE(:source,source),"
                        + "updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                params);
        requireUpdated(updated);
        timeline(tenantId, "ACCOUNT", accountId, "crm.account.updated", "Account updated", "CRM_ACCOUNT", accountId, actorId, now);
        return row("crm_accounts", tenantId, accountId, CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> setAccountArchived(
            Authentication authentication,
            UUID accountId,
            boolean archived,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        row("crm_accounts", tenantId, accountId, CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        if (archived) {
            Long children = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=:tenantId "
                            + "AND parent_account_id=:id AND lifecycle_status<>'ARCHIVED'",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", accountId),
                    Long.class);
            if (children != null && children > 0) {
                throw new CrmContractException(CrmErrorCode.CONFLICT, "CRM account has active child accounts.");
            }
        }
        Instant now = Instant.now();
        MapSqlParameterSource params = context(tenantId, actorId, accountId, expectedVersion, now)
                .addValue("status", archived ? "ARCHIVED" : "ACTIVE")
                .addValue("archivedAt", archived ? Timestamp.from(now) : null);
        int updated = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status=:status,archived_at=:archivedAt,"
                        + "updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                params);
        requireUpdated(updated);
        timeline(
                tenantId,
                "ACCOUNT",
                accountId,
                archived ? "crm.account.archived" : "crm.account.restored",
                archived ? "Account archived" : "Account restored",
                "CRM_ACCOUNT",
                accountId,
                actorId,
                now);
        return row("crm_accounts", tenantId, accountId, CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> updateContact(
            Authentication authentication,
            UUID contactId,
            UpdateContactRequest request,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> current = row("crm_contacts", tenantId, contactId, CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        if ("ARCHIVED".equals(current.get("lifecycle_status"))) {
            throw new CrmContractException(CrmErrorCode.CONFLICT, "Archived CRM contact cannot be updated.");
        }
        String given = clean(request.givenName(), 120);
        String family = clean(request.familyName(), 120);
        String effectiveGiven = given == null ? String.valueOf(current.get("given_name")) : given;
        String effectiveFamily = family == null ? asNullableString(current.get("family_name")) : family;
        String display = effectiveFamily == null || effectiveFamily.isBlank()
                ? effectiveGiven : effectiveGiven + " " + effectiveFamily;
        Instant now = Instant.now();
        MapSqlParameterSource params = context(tenantId, actorId, contactId, expectedVersion, now)
                .addValue("accountId", request.accountId())
                .addValue("givenName", given)
                .addValue("familyName", family)
                .addValue("displayName", display)
                .addValue("normalizedName", normalize(display))
                .addValue("email", clean(request.primaryEmail(), 255))
                .addValue("normalizedEmail", request.primaryEmail() == null ? null : request.primaryEmail().trim().toLowerCase(Locale.ROOT))
                .addValue("phone", clean(request.primaryPhone(), 64))
                .addValue("locale", locale(request.preferredLocale()))
                .addValue("timeZone", zone(request.timeZone()))
                .addValue("ownerUserId", request.ownerUserId())
                .addValue("consent", upper(request.consentSummary()));
        int updated = jdbc.update(
                "UPDATE crm_contacts SET "
                        + "account_id=COALESCE(:accountId,account_id),"
                        + "given_name=COALESCE(:givenName,given_name),"
                        + "family_name=COALESCE(:familyName,family_name),"
                        + "display_name=:displayName,normalized_name=:normalizedName,"
                        + "primary_email=COALESCE(:email,primary_email),"
                        + "normalized_email=COALESCE(:normalizedEmail,normalized_email),"
                        + "primary_phone=COALESCE(:phone,primary_phone),"
                        + "preferred_locale=COALESCE(:locale,preferred_locale),"
                        + "time_zone=COALESCE(:timeZone,time_zone),"
                        + "owner_user_id=COALESCE(:ownerUserId,owner_user_id),"
                        + "consent_summary=COALESCE(:consent,consent_summary),"
                        + "updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                params);
        requireUpdated(updated);
        timeline(tenantId, "CONTACT", contactId, "crm.contact.updated", "Contact updated", "CRM_CONTACT", contactId, actorId, now);
        return row("crm_contacts", tenantId, contactId, CrmErrorCode.CRM_CONTACT_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> setContactArchived(
            Authentication authentication,
            UUID contactId,
            boolean archived,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        row("crm_contacts", tenantId, contactId, CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        Instant now = Instant.now();
        MapSqlParameterSource params = context(tenantId, actorId, contactId, expectedVersion, now)
                .addValue("status", archived ? "ARCHIVED" : "ACTIVE")
                .addValue("archivedAt", archived ? Timestamp.from(now) : null);
        int updated = jdbc.update(
                "UPDATE crm_contacts SET lifecycle_status=:status,archived_at=:archivedAt,"
                        + "updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                params);
        requireUpdated(updated);
        timeline(
                tenantId,
                "CONTACT",
                contactId,
                archived ? "crm.contact.archived" : "crm.contact.restored",
                archived ? "Contact archived" : "Contact restored",
                "CRM_CONTACT",
                contactId,
                actorId,
                now);
        return row("crm_contacts", tenantId, contactId, CrmErrorCode.CRM_CONTACT_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> changeLeadStatus(
            Authentication authentication,
            UUID leadId,
            UpdateLeadStatusRequest request,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> current = row("crm_leads", tenantId, leadId, CrmErrorCode.CRM_LEAD_NOT_FOUND);
        String from = String.valueOf(current.get("status"));
        String to = request.status().trim().toUpperCase(Locale.ROOT);
        if (!from.equals(to) && !LEAD_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new CrmContractException(CrmErrorCode.CRM_INVALID_LEAD_TRANSITION);
        }
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_leads SET status=:status,updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                context(tenantId, actorId, leadId, expectedVersion, now).addValue("status", to));
        requireUpdated(updated);
        timeline(tenantId, "LEAD", leadId, "crm.lead.status_changed", "Lead status changed", "CRM_LEAD", leadId, actorId, now);
        return row("crm_leads", tenantId, leadId, CrmErrorCode.CRM_LEAD_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> moveOpportunityStage(
            Authentication authentication,
            UUID opportunityId,
            MoveOpportunityRequest request,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> opportunity = row("crm_opportunities", tenantId, opportunityId, CrmErrorCode.CRM_OPPORTUNITY_NOT_FOUND);
        UUID pipelineId = asUuid(opportunity.get("pipeline_id"));
        UUID fromStage = asUuid(opportunity.get("stage_id"));
        Map<String, Object> target;
        try {
            target = jdbc.queryForMap(
                    "SELECT * FROM crm_pipeline_stages WHERE tenant_id=:tenantId "
                            + "AND pipeline_id=:pipelineId AND id=:stageId AND active=TRUE",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("pipelineId", pipelineId)
                            .addValue("stageId", request.stageId()));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_STAGE_NOT_FOUND);
        }
        String currentStatus = String.valueOf(opportunity.get("status"));
        if (Set.of("WON", "LOST", "CANCELLED", "ARCHIVED").contains(currentStatus)
                && !fromStage.equals(request.stageId())) {
            throw new CrmContractException(CrmErrorCode.CRM_INVALID_OPPORTUNITY_STAGE);
        }
        String terminal = asNullableString(target.get("terminal_state"));
        String status = terminal == null
                ? (request.status() == null ? "OPEN" : request.status().trim().toUpperCase(Locale.ROOT))
                : terminal;
        Instant now = Instant.now();
        MapSqlParameterSource params = context(tenantId, actorId, opportunityId, expectedVersion, now)
                .addValue("stageId", request.stageId())
                .addValue("status", status)
                .addValue("probability", target.get("probability"))
                .addValue("reason", clean(request.reason(), 500));
        int updated = jdbc.update(
                "UPDATE crm_opportunities SET stage_id=:stageId,status=:status,probability=:probability,"
                        + "win_loss_reason=:reason,updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                params);
        requireUpdated(updated);
        jdbc.update(
                "INSERT INTO crm_opportunity_stage_history "
                        + "(id,tenant_id,opportunity_id,from_stage_id,to_stage_id,changed_by,changed_at,reason) "
                        + "VALUES (:historyId,:tenantId,:id,:fromStage,:stageId,:actorId,:now,:reason)",
                params.addValue("historyId", UUID.randomUUID()).addValue("fromStage", fromStage));
        timeline(tenantId, "OPPORTUNITY", opportunityId, "crm.opportunity.stage_changed", "Opportunity stage changed", "CRM_OPPORTUNITY", opportunityId, actorId, now);
        return row("crm_opportunities", tenantId, opportunityId, CrmErrorCode.CRM_OPPORTUNITY_NOT_FOUND);
    }

    @Transactional
    public Map<String, Object> completeActivity(
            Authentication authentication,
            UUID activityId,
            CompleteActivityRequest request,
            long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> current = row("crm_activities", tenantId, activityId, CrmErrorCode.CRM_ACTIVITY_NOT_FOUND);
        if ("COMPLETED".equals(current.get("status"))) {
            throw new CrmContractException(CrmErrorCode.CONFLICT, "CRM activity is already completed.");
        }
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_activities SET status='COMPLETED',body=COALESCE(:result,body),"
                        + "completed_at=:now,updated_by=:actorId,updated_at=:now,version=version+1 "
                        + "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                context(tenantId, actorId, activityId, expectedVersion, now)
                        .addValue("result", clean(request.result(), 4000)));
        requireUpdated(updated);
        String relatedType = asNullableString(current.get("related_type"));
        UUID relatedId = current.get("related_id") == null ? null : asUuid(current.get("related_id"));
        if (relatedType != null && relatedId != null) {
            timeline(tenantId, relatedType, relatedId, "crm.activity.completed", "Activity completed", "CRM_ACTIVITY", activityId, actorId, now);
        }
        return row("crm_activities", tenantId, activityId, CrmErrorCode.CRM_ACTIVITY_NOT_FOUND);
    }

    private Map<String, Object> row(String table, UUID tenantId, UUID id, CrmErrorCode missingCode) {
        if (!Set.of("crm_accounts", "crm_contacts", "crm_leads", "crm_opportunities", "crm_activities").contains(table)) {
            throw new IllegalArgumentException("Unsupported CRM table");
        }
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM " + table + " WHERE tenant_id=:tenantId AND id=:id",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(missingCode);
        }
    }

    private void timeline(
            UUID tenantId,
            String subjectType,
            UUID subjectId,
            String eventType,
            String summary,
            String sourceType,
            UUID sourceId,
            UUID actorId,
            Instant now) {
        jdbc.update(
                "INSERT INTO crm_timeline_events "
                        + "(id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) "
                        + "VALUES (:timelineId,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)",
                new MapSqlParameterSource()
                        .addValue("timelineId", UUID.randomUUID())
                        .addValue("tenantId", tenantId)
                        .addValue("subjectType", subjectType)
                        .addValue("subjectId", subjectId)
                        .addValue("eventType", eventType)
                        .addValue("summary", summary)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
                        .addValue("now", Timestamp.from(now))
                        .addValue("actorId", actorId));
    }

    private static void requireUpdated(int updated) {
        if (updated != 1) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
    }

    private static MapSqlParameterSource context(
            UUID tenantId, UUID actorId, UUID id, long expectedVersion, Instant now) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("actorId", actorId)
                .addValue("id", id)
                .addValue("expectedVersion", expectedVersion)
                .addValue("now", Timestamp.from(now));
    }

    private static UUID tenantId(Authentication authentication) {
        return contextUuid(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return contextUuid(authentication, "user_id");
    }

    private static UUID contextUuid(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String currency(String value) {
        String result = upper(value);
        if (result != null && !result.matches("[A-Z]{3}")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR);
        }
        return result;
    }

    private static String locale(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new Locale.Builder().setLanguageTag(value.trim()).build().toLanguageTag();
        } catch (RuntimeException exception) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR);
        }
    }

    private static String zone(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (RuntimeException exception) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR);
        }
    }

    private static UUID asUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static String asNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
