package com.sanad.platform.crm.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class CrmExtendedService {
    private static final Set<String> TABLES = Set.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_opportunities", "crm_activities", "crm_import_jobs",
            "crm_custom_field_definitions");

    private final NamedParameterJdbcTemplate jdbc;

    CrmExtendedService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Map<String, Object> dashboard(Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", scalarLong("SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'", params));
        result.put("contacts", scalarLong("SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'", params));
        result.put("openLeads", scalarLong("SELECT COUNT(*) FROM crm_leads WHERE tenant_id=:tenantId AND status NOT IN ('CONVERTED','DISQUALIFIED','ARCHIVED')", params));
        result.put("openOpportunities", scalarLong("SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'", params));
        BigDecimal weighted = jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(amount,0) * probability / 100),0) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'", params, BigDecimal.class);
        result.put("weightedPipeline", weighted == null ? BigDecimal.ZERO : weighted);
        result.put("overdueActivities", scalarLong("SELECT COUNT(*) FROM crm_activities WHERE tenant_id=:tenantId AND status IN ('OPEN','IN_PROGRESS') AND due_at IS NOT NULL AND due_at<CURRENT_TIMESTAMP", params));
        result.put("recentActivity", jdbc.queryForList("SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId ORDER BY occurred_at DESC,id LIMIT 10", params));
        return result;
    }

    @Transactional(readOnly = true)
    Map<String, Object> customer360(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("account", one("crm_accounts", tenantId, accountId, "CRM account not found"));
        MapSqlParameterSource params = p().addValue("tenantId", tenantId).addValue("accountId", accountId);
        result.put("contacts", jdbc.queryForList("SELECT * FROM crm_contacts WHERE tenant_id=:tenantId AND account_id=:accountId AND lifecycle_status<>'ARCHIVED' ORDER BY updated_at DESC,id", params));
        result.put("opportunities", jdbc.queryForList("SELECT opportunity.*,pipeline.name AS pipeline_name,stage.name AS stage_name FROM crm_opportunities opportunity JOIN crm_pipelines pipeline ON pipeline.tenant_id=opportunity.tenant_id AND pipeline.id=opportunity.pipeline_id JOIN crm_pipeline_stages stage ON stage.tenant_id=opportunity.tenant_id AND stage.id=opportunity.stage_id WHERE opportunity.tenant_id=:tenantId AND opportunity.account_id=:accountId ORDER BY opportunity.updated_at DESC,opportunity.id", params));
        result.put("activities", jdbc.queryForList("SELECT * FROM crm_activities WHERE tenant_id=:tenantId AND related_type='ACCOUNT' AND related_id=:accountId ORDER BY created_at DESC,id LIMIT 100", params));
        result.put("timeline", jdbc.queryForList("SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_type='ACCOUNT' AND subject_id=:accountId ORDER BY occurred_at DESC,id LIMIT 200", params));
        return result;
    }

    @Transactional
    Map<String, Object> restoreAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Instant now = Instant.now();
        int changed = jdbc.update("UPDATE crm_accounts SET lifecycle_status='ACTIVE',archived_at=NULL,updated_at=:now,updated_by=:actorId,version=version+1 WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status='ARCHIVED'", context(tenantId, actorId, accountId, now));
        if (changed != 1) throw conflict("CRM account is not archived or does not exist");
        timeline(tenantId, "ACCOUNT", accountId, "crm.account.restored", "Account restored", "CRM_ACCOUNT", accountId, actorId, now);
        return one("crm_accounts", tenantId, accountId, "CRM account not found");
    }

    @Transactional(readOnly = true)
    Map<String, Object> getContact(Authentication authentication, UUID contactId) {
        return one("crm_contacts", tenantId(authentication), contactId, "CRM contact not found");
    }

    @Transactional
    Map<String, Object> updateContact(Authentication authentication, UUID contactId, UpdateContactRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> existing = one("crm_contacts", tenantId, contactId, "CRM contact not found");
        if ("ARCHIVED".equals(existing.get("lifecycle_status"))) throw conflict("Archived CRM contact cannot be updated");
        if (request.accountId() != null) one("crm_accounts", tenantId, request.accountId(), "CRM account not found");
        validateOwner(tenantId, request.ownerUserId());
        String given = optional(request.givenName(), 120, "givenName");
        String family = optional(request.familyName(), 120, "familyName");
        String displayName = null;
        if (given != null || family != null) {
            String actualGiven = given == null ? String.valueOf(existing.get("given_name")) : given;
            Object currentFamily = existing.get("family_name");
            String actualFamily = family == null && currentFamily != null ? currentFamily.toString() : family;
            displayName = actualFamily == null || actualFamily.isBlank() ? actualGiven : actualGiven + " " + actualFamily;
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE crm_contacts SET account_id=COALESCE(:accountId,account_id),given_name=COALESCE(:givenName,given_name),family_name=COALESCE(:familyName,family_name),display_name=COALESCE(:displayName,display_name),normalized_name=COALESCE(:normalizedName,normalized_name),primary_email=COALESCE(:email,primary_email),normalized_email=COALESCE(:normalizedEmail,normalized_email),primary_phone=COALESCE(:phone,primary_phone),preferred_locale=COALESCE(:locale,preferred_locale),time_zone=COALESCE(:timeZone,time_zone),owner_user_id=COALESCE(:ownerUserId,owner_user_id),consent_summary=COALESCE(:consent,consent_summary),updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id", p().addValue("tenantId", tenantId).addValue("id", contactId).addValue("accountId", request.accountId()).addValue("givenName", given).addValue("familyName", family).addValue("displayName", displayName).addValue("normalizedName", displayName == null ? null : normalize(displayName)).addValue("email", optional(request.primaryEmail(), 255, "primaryEmail")).addValue("normalizedEmail", normalizeEmail(request.primaryEmail())).addValue("phone", optional(request.primaryPhone(), 64, "primaryPhone")).addValue("locale", optional(request.preferredLocale(), 35, "preferredLocale")).addValue("timeZone", optional(request.timeZone(), 64, "timeZone")).addValue("ownerUserId", request.ownerUserId()).addValue("consent", upper(request.consentSummary())).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "CONTACT", contactId, "crm.contact.updated", "Contact updated", "CRM_CONTACT", contactId, actorId, now);
        return one("crm_contacts", tenantId, contactId, "CRM contact not found");
    }

    @Transactional
    Map<String, Object> archiveContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, true);
    }

    @Transactional
    Map<String, Object> restoreContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, false);
    }

    private Map<String, Object> changeContactArchive(Authentication authentication, UUID contactId, boolean archive) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Instant now = Instant.now();
        String expected = archive ? "ACTIVE" : "ARCHIVED";
        String next = archive ? "ARCHIVED" : "ACTIVE";
        int changed = jdbc.update("UPDATE crm_contacts SET lifecycle_status=:nextStatus,archived_at=:archivedAt,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status=:expectedStatus", p().addValue("tenantId", tenantId).addValue("id", contactId).addValue("nextStatus", next).addValue("expectedStatus", expected).addValue("archivedAt", archive ? Timestamp.from(now) : null).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (changed != 1) throw conflict("CRM contact lifecycle transition is not allowed");
        timeline(tenantId, "CONTACT", contactId, archive ? "crm.contact.archived" : "crm.contact.restored", archive ? "Contact archived" : "Contact restored", "CRM_CONTACT", contactId, actorId, now);
        return one("crm_contacts", tenantId, contactId, "CRM contact not found");
    }

    @Transactional(readOnly = true)
    Map<String, Object> getLead(Authentication authentication, UUID leadId) {
        return one("crm_leads", tenantId(authentication), leadId, "CRM lead not found");
    }

    @Transactional
    Map<String, Object> changeLeadStatus(Authentication authentication, UUID leadId, UpdateLeadStatusRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> lead = one("crm_leads", tenantId, leadId, "CRM lead not found");
        String current = String.valueOf(lead.get("status"));
        String next = request.status().trim().toUpperCase(Locale.ROOT);
        if (!leadTransitionAllowed(current, next)) throw conflict("Invalid CRM lead status transition: " + current + " -> " + next);
        Instant now = Instant.now();
        jdbc.update("UPDATE crm_leads SET status=:status,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id", context(tenantId, actorId, leadId, now).addValue("status", next));
        timeline(tenantId, "LEAD", leadId, "crm.lead.status_changed", "Lead status changed to " + next, "CRM_LEAD", leadId, actorId, now);
        return one("crm_leads", tenantId, leadId, "CRM lead not found");
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listPipelineStages(Authentication authentication, UUID pipelineId) {
        UUID tenantId = tenantId(authentication);
        one("crm_pipelines", tenantId, pipelineId, "CRM pipeline not found");
        return jdbc.queryForList("SELECT * FROM crm_pipeline_stages WHERE tenant_id=:tenantId AND pipeline_id=:pipelineId AND active=TRUE ORDER BY sequence,id", p().addValue("tenantId", tenantId).addValue("pipelineId", pipelineId));
    }

    @Transactional(readOnly = true)
    Map<String, Object> getOpportunity(Authentication authentication, UUID opportunityId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(one("crm_opportunities", tenantId, opportunityId, "CRM opportunity not found"));
        result.put("stageHistory", jdbc.queryForList("SELECT history.*,from_stage.name AS from_stage_name,to_stage.name AS to_stage_name FROM crm_opportunity_stage_history history LEFT JOIN crm_pipeline_stages from_stage ON from_stage.tenant_id=history.tenant_id AND from_stage.id=history.from_stage_id JOIN crm_pipeline_stages to_stage ON to_stage.tenant_id=history.tenant_id AND to_stage.id=history.to_stage_id WHERE history.tenant_id=:tenantId AND history.opportunity_id=:opportunityId ORDER BY history.changed_at DESC,history.id", p().addValue("tenantId", tenantId).addValue("opportunityId", opportunityId)));
        return result;
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listActivities(Authentication authentication, int requestedLimit, String relatedType, UUID relatedId, String status) {
        UUID tenantId = tenantId(authentication);
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_activities WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (relatedType != null && !relatedType.isBlank()) { sql.append(" AND related_type=:relatedType"); params.addValue("relatedType", relatedType.trim().toUpperCase(Locale.ROOT)); }
        if (relatedId != null) { sql.append(" AND related_id=:relatedId"); params.addValue("relatedId", relatedId); }
        if (status != null && !status.isBlank()) { sql.append(" AND status=:status"); params.addValue("status", status.trim().toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit");
        params.addValue("limit", limit(requestedLimit));
        return jdbc.queryForList(sql.toString(), params);
    }

    @Transactional(readOnly = true)
    Map<String, Object> getActivity(Authentication authentication, UUID activityId) {
        return one("crm_activities", tenantId(authentication), activityId, "CRM activity not found");
    }

    @Transactional
    Map<String, Object> completeActivity(Authentication authentication, UUID activityId, CompleteActivityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> activity = one("crm_activities", tenantId, activityId, "CRM activity not found");
        String status = String.valueOf(activity.get("status"));
        if (!Set.of("OPEN", "IN_PROGRESS").contains(status)) throw conflict("CRM activity cannot be completed from status " + status);
        Instant now = Instant.now();
        jdbc.update("UPDATE crm_activities SET status='COMPLETED',completed_at=:now,body=COALESCE(:result,body),updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id", context(tenantId, actorId, activityId, now).addValue("result", optional(request.result(), 4000, "result")));
        Object relatedType = activity.get("related_type");
        Object relatedId = activity.get("related_id");
        if (relatedType != null && relatedId instanceof UUID subjectId) timeline(tenantId, relatedType.toString(), subjectId, "crm.activity.completed", "Activity completed", "CRM_ACTIVITY", activityId, actorId, now);
        return one("crm_activities", tenantId, activityId, "CRM activity not found");
    }

    @Transactional
    Map<String, Object> createImportJob(Authentication authentication, CreateImportJobRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_import_jobs (id,tenant_id,entity_type,status,total_rows,processed_rows,succeeded_rows,failed_rows,requested_by,created_at,updated_at) VALUES (:id,:tenantId,:entityType,'UPLOADED',:totalRows,0,0,0,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("entityType", request.entityType().trim().toUpperCase(Locale.ROOT)).addValue("totalRows", request.totalRows() == null ? 0 : request.totalRows()).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        return one("crm_import_jobs", tenantId, id, "CRM import job not found");
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listImportJobs(Authentication authentication, int requestedLimit) {
        return jdbc.queryForList("SELECT * FROM crm_import_jobs WHERE tenant_id=:tenantId ORDER BY created_at DESC,id LIMIT :limit", p().addValue("tenantId", tenantId(authentication)).addValue("limit", limit(requestedLimit)));
    }

    @Transactional
    Map<String, Object> createCustomField(Authentication authentication, CreateCustomFieldRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO crm_custom_field_definitions (id,tenant_id,entity_type,field_key,label_ar,label_en,data_type,sensitive,searchable,required,active,created_at) VALUES (:id,:tenantId,:entityType,:fieldKey,:labelAr,:labelEn,:dataType,:sensitive,:searchable,:required,TRUE,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("entityType", request.entityType().trim().toUpperCase(Locale.ROOT)).addValue("fieldKey", request.fieldKey().trim()).addValue("labelAr", request.labelAr().trim()).addValue("labelEn", request.labelEn().trim()).addValue("dataType", request.dataType().trim().toUpperCase(Locale.ROOT)).addValue("sensitive", Boolean.TRUE.equals(request.sensitive())).addValue("searchable", Boolean.TRUE.equals(request.searchable())).addValue("required", Boolean.TRUE.equals(request.required())).addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) { throw conflict("CRM custom field key already exists"); }
        return one("crm_custom_field_definitions", tenantId, id, "CRM custom field not found");
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listCustomFields(Authentication authentication, String entityType) {
        UUID tenantId = tenantId(authentication);
        if (entityType == null || entityType.isBlank()) return jdbc.queryForList("SELECT * FROM crm_custom_field_definitions WHERE tenant_id=:tenantId AND active=TRUE ORDER BY entity_type,field_key", p().addValue("tenantId", tenantId));
        return jdbc.queryForList("SELECT * FROM crm_custom_field_definitions WHERE tenant_id=:tenantId AND active=TRUE AND entity_type=:entityType ORDER BY field_key", p().addValue("tenantId", tenantId).addValue("entityType", entityType.trim().toUpperCase(Locale.ROOT)));
    }

    void validateOwner(UUID tenantId, UUID ownerId) {
        if (ownerId == null) return;
        if (scalarLong("SELECT COUNT(*) FROM users WHERE tenant_id=:tenantId AND id=:ownerId AND status='ACTIVE'", p().addValue("tenantId", tenantId).addValue("ownerId", ownerId)) != 1) throw bad("CRM owner must be an active user in the same tenant");
    }

    void validateRelated(UUID tenantId, String relatedType, UUID relatedId) {
        if (relatedType == null && relatedId == null) return;
        if (relatedType == null || relatedId == null) throw bad("relatedType and relatedId must be supplied together");
        String table = switch (relatedType.trim().toUpperCase(Locale.ROOT)) {
            case "ACCOUNT" -> "crm_accounts";
            case "CONTACT" -> "crm_contacts";
            case "LEAD" -> "crm_leads";
            case "OPPORTUNITY" -> "crm_opportunities";
            default -> throw bad("Unsupported CRM relatedType");
        };
        one(table, tenantId, relatedId, "Related CRM record not found");
    }

    private boolean leadTransitionAllowed(String current, String next) {
        if (current.equals(next)) return true;
        return switch (current) {
            case "NEW" -> Set.of("ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "ASSIGNED" -> Set.of("CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "CONTACTED" -> Set.of("QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "QUALIFIED" -> Set.of("DISQUALIFIED", "ARCHIVED").contains(next);
            case "DISQUALIFIED" -> "ARCHIVED".equals(next);
            default -> false;
        };
    }

    private Map<String, Object> one(String table, UUID tenantId, UUID id, String message) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported CRM table");
        try { return jdbc.queryForMap("SELECT * FROM " + table + " WHERE tenant_id=:tenantId AND id=:id", p().addValue("tenantId", tenantId).addValue("id", id)); }
        catch (EmptyResultDataAccessException exception) { throw missing(message); }
    }

    private long scalarLong(String sql, MapSqlParameterSource params) { Long value = jdbc.queryForObject(sql, params, Long.class); return value == null ? 0 : value; }

    private void timeline(UUID tenantId, String subjectType, UUID subjectId, String eventType, String summary, String sourceType, UUID sourceId, UUID actorId, Instant now) {
        jdbc.update("INSERT INTO crm_timeline_events (id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) VALUES (:id,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)", p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId).addValue("subjectType", subjectType).addValue("subjectId", subjectId).addValue("eventType", eventType).addValue("summary", summary).addValue("sourceType", sourceType).addValue("sourceId", sourceId).addValue("now", Timestamp.from(now)).addValue("actorId", actorId));
    }

    private MapSqlParameterSource context(UUID tenantId, UUID actorId, UUID id, Instant now) { return p().addValue("tenantId", tenantId).addValue("actorId", actorId).addValue("id", id).addValue("now", Timestamp.from(now)); }
    private UUID tenantId(Authentication authentication) { return contextValue(authentication, "tenant_id"); }
    private UUID userId(Authentication authentication) { return contextValue(authentication, "user_id"); }
    private UUID contextValue(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        try { return UUID.fromString(details.get(key).toString()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception); }
    }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static int limit(int requested) { return Math.max(1, Math.min(requested, 200)); }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw bad(field + " exceeds " + max); return result; }
    private static String normalize(String value) { return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static String normalizeEmail(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private static String upper(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
