package com.sanad.platform.crm.web;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class CrmService {
    private static final Set<String> TABLES = Set.of("crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines", "crm_opportunities", "crm_activities");
    private final NamedParameterJdbcTemplate jdbc;
    private final CrmExtendedService extended;

    CrmService(NamedParameterJdbcTemplate jdbc, CrmExtendedService extended) {
        this.jdbc = jdbc;
        this.extended = extended;
    }

    @Transactional
    Map<String, Object> createAccount(Authentication authentication, CreateAccountRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        if (request.parentAccountId() != null) account(tenantId, request.parentAccountId());
        extended.validateOwner(tenantId, request.ownerUserId());
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,display_name,normalized_name,account_type,lifecycle_status,parent_account_id,owner_user_id,primary_currency_code,preferred_locale,time_zone,source,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,:displayName,:normalizedName,:accountType,'ACTIVE',:parentAccountId,:ownerUserId,:currency,:locale,:timeZone,:source,:actorId,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("displayName", required(request.displayName(), 240, "displayName")).addValue("normalizedName", norm(request.displayName())).addValue("accountType", value(request.accountType(), "BUSINESS").toUpperCase(Locale.ROOT)).addValue("parentAccountId", request.parentAccountId()).addValue("ownerUserId", request.ownerUserId()).addValue("currency", currency(request.primaryCurrencyCode())).addValue("locale", locale(request.preferredLocale())).addValue("timeZone", zone(request.timeZone())).addValue("source", optional(request.source(), 80, "source")).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "ACCOUNT", id, "crm.account.created", "Account created", "CRM_ACCOUNT", id, actorId, now);
        return account(tenantId, id);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listAccounts(Authentication authentication, int requestedLimit, String search) {
        UUID tenantId = tenantId(authentication);
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_accounts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'");
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (search != null && !search.isBlank()) { sql.append(" AND normalized_name LIKE :search"); params.addValue("search", "%" + norm(search) + "%"); }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit");
        params.addValue("limit", limit(requestedLimit));
        return jdbc.queryForList(sql.toString(), params);
    }

    @Transactional(readOnly = true)
    Map<String, Object> getAccount(Authentication authentication, UUID accountId) { return account(tenantId(authentication), accountId); }

    @Transactional
    Map<String, Object> updateAccount(Authentication authentication, UUID accountId, UpdateAccountRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> current = account(tenantId, accountId);
        if ("ARCHIVED".equals(current.get("lifecycle_status"))) throw conflict("Archived CRM account cannot be updated");
        if (request.parentAccountId() != null) { account(tenantId, request.parentAccountId()); assertNoAccountCycle(tenantId, accountId, request.parentAccountId()); }
        extended.validateOwner(tenantId, request.ownerUserId());
        Instant now = Instant.now();
        jdbc.update("UPDATE crm_accounts SET display_name=COALESCE(:displayName,display_name),normalized_name=COALESCE(:normalizedName,normalized_name),parent_account_id=:parentAccountId,owner_user_id=COALESCE(:ownerUserId,owner_user_id),primary_currency_code=COALESCE(:currency,primary_currency_code),preferred_locale=COALESCE(:locale,preferred_locale),time_zone=COALESCE(:timeZone,time_zone),source=COALESCE(:source,source),updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id", p().addValue("tenantId", tenantId).addValue("id", accountId).addValue("displayName", optional(request.displayName(), 240, "displayName")).addValue("normalizedName", request.displayName() == null ? null : norm(request.displayName())).addValue("parentAccountId", request.parentAccountId()).addValue("ownerUserId", request.ownerUserId()).addValue("currency", currency(request.primaryCurrencyCode())).addValue("locale", locale(request.preferredLocale())).addValue("timeZone", zone(request.timeZone())).addValue("source", optional(request.source(), 80, "source")).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "ACCOUNT", accountId, "crm.account.updated", "Account updated", "CRM_ACCOUNT", accountId, actorId, now);
        return account(tenantId, accountId);
    }

    @Transactional
    Map<String, Object> archiveAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        account(tenantId, accountId);
        Long activeChildren = jdbc.queryForObject("SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=:tenantId AND parent_account_id=:accountId AND lifecycle_status<>'ARCHIVED'", p().addValue("tenantId", tenantId).addValue("accountId", accountId), Long.class);
        if (activeChildren != null && activeChildren > 0) throw conflict("CRM account has active child accounts");
        Instant now = Instant.now();
        int changed = jdbc.update("UPDATE crm_accounts SET lifecycle_status='ARCHIVED',archived_at=:now,updated_at=:now,updated_by=:actorId,version=version+1 WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status<>'ARCHIVED'", context(tenantId, actorId, accountId, now));
        if (changed != 1) throw conflict("CRM account is already archived");
        timeline(tenantId, "ACCOUNT", accountId, "crm.account.archived", "Account archived", "CRM_ACCOUNT", accountId, actorId, now);
        return account(tenantId, accountId);
    }

    @Transactional
    Map<String, Object> createContact(Authentication authentication, CreateContactRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        if (request.accountId() != null) account(tenantId, request.accountId());
        extended.validateOwner(tenantId, request.ownerUserId());
        String given = required(request.givenName(), 120, "givenName");
        String family = optional(request.familyName(), 120, "familyName");
        String display = family == null ? given : given + " " + family;
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,account_id,given_name,family_name,display_name,normalized_name,primary_email,normalized_email,primary_phone,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,:accountId,:givenName,:familyName,:displayName,:normalizedName,:email,:normalizedEmail,:phone,:locale,:timeZone,'ACTIVE',:ownerUserId,:consent,:actorId,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("accountId", request.accountId()).addValue("givenName", given).addValue("familyName", family).addValue("displayName", display).addValue("normalizedName", norm(display)).addValue("email", optional(request.primaryEmail(), 255, "primaryEmail")).addValue("normalizedEmail", email(request.primaryEmail())).addValue("phone", optional(request.primaryPhone(), 64, "primaryPhone")).addValue("locale", locale(request.preferredLocale())).addValue("timeZone", zone(request.timeZone())).addValue("ownerUserId", request.ownerUserId()).addValue("consent", value(request.consentSummary(), "UNKNOWN").toUpperCase(Locale.ROOT)).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "CONTACT", id, "crm.contact.created", "Contact created", "CRM_CONTACT", id, actorId, now);
        if (request.accountId() != null) timeline(tenantId, "ACCOUNT", request.accountId(), "crm.contact.linked", "Contact linked", "CRM_CONTACT", id, actorId, now);
        return contact(tenantId, id);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listContacts(Authentication authentication, int requestedLimit, UUID accountId, String search) {
        UUID tenantId = tenantId(authentication);
        if (accountId != null) account(tenantId, accountId);
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_contacts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'");
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (accountId != null) { sql.append(" AND account_id=:accountId"); params.addValue("accountId", accountId); }
        if (search != null && !search.isBlank()) { sql.append(" AND (normalized_name LIKE :search OR normalized_email LIKE :search)"); params.addValue("search", "%" + norm(search) + "%"); }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit"); params.addValue("limit", limit(requestedLimit));
        return jdbc.queryForList(sql.toString(), params);
    }

    @Transactional
    Map<String, Object> createLead(Authentication authentication, CreateLeadRequest request) {
        UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); UUID id = UUID.randomUUID(); Instant now = Instant.now();
        extended.validateOwner(tenantId, request.ownerUserId());
        jdbc.update("INSERT INTO crm_leads (id,tenant_id,display_name,normalized_name,company_name,email,normalized_email,phone,source,status,owner_user_id,queue_id,score,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,:displayName,:normalizedName,:companyName,:email,:normalizedEmail,:phone,:source,'NEW',:ownerUserId,:queueId,:score,:actorId,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("displayName", required(request.displayName(), 240, "displayName")).addValue("normalizedName", norm(request.displayName())).addValue("companyName", optional(request.companyName(), 240, "companyName")).addValue("email", optional(request.email(), 255, "email")).addValue("normalizedEmail", email(request.email())).addValue("phone", optional(request.phone(), 64, "phone")).addValue("source", optional(request.source(), 120, "source")).addValue("ownerUserId", request.ownerUserId()).addValue("queueId", request.queueId()).addValue("score", request.score()).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "LEAD", id, "crm.lead.created", "Lead created", "CRM_LEAD", id, actorId, now); return lead(tenantId, id);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listLeads(Authentication authentication, int requestedLimit, String status) {
        UUID tenantId = tenantId(authentication); StringBuilder sql = new StringBuilder("SELECT * FROM crm_leads WHERE tenant_id=:tenantId"); MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (status != null && !status.isBlank()) { sql.append(" AND status=:status"); params.addValue("status", status.trim().toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit"); params.addValue("limit", limit(requestedLimit)); return jdbc.queryForList(sql.toString(), params);
    }

    @Transactional
    Map<String, Object> convertLead(Authentication authentication, UUID leadId, ConvertLeadRequest request) {
        UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); Map<String, Object> lead = lead(tenantId, leadId);
        if ("CONVERTED".equals(lead.get("status"))) { LinkedHashMap<String, Object> replay = new LinkedHashMap<>(); replay.put("lead", lead); replay.put("accountId", lead.get("converted_account_id")); replay.put("contactId", lead.get("converted_contact_id")); replay.put("opportunityId", lead.get("converted_opportunity_id")); replay.put("idempotent", true); return replay; }
        if ("ARCHIVED".equals(lead.get("status")) || "DISQUALIFIED".equals(lead.get("status"))) throw conflict("Lead must be active before conversion");
        Map<String, Object> account = createAccount(authentication, new CreateAccountRequest(value(request.accountName(), String.valueOf(lead.get("display_name"))), "PROSPECT", (UUID) lead.get("owner_user_id"), null, value(request.currencyCode(), "SAR"), "ar-SA", "Asia/Riyadh", "LEAD_CONVERSION"));
        String displayName = String.valueOf(lead.get("display_name")).trim(); int separator = displayName.indexOf(' '); String givenName = separator < 0 ? displayName : displayName.substring(0, separator); String familyName = separator < 0 ? null : displayName.substring(separator + 1).trim();
        Map<String, Object> contact = createContact(authentication, new CreateContactRequest((UUID) account.get("id"), givenName, familyName, (String) lead.get("email"), (String) lead.get("phone"), "ar-SA", "Asia/Riyadh", (UUID) lead.get("owner_user_id"), "UNKNOWN"));
        Map<String, Object> opportunity = null;
        if (Boolean.TRUE.equals(request.createOpportunity())) { Map<String, Object> pipeline = request.pipelineId() == null ? ensureDefaultPipeline(authentication, value(request.currencyCode(), "SAR")) : pipeline(tenantId, request.pipelineId()); UUID pipelineId = (UUID) pipeline.get("id"); UUID stageId = request.stageId() == null ? firstStage(tenantId, pipelineId) : request.stageId(); opportunity = createOpportunity(authentication, new CreateOpportunityRequest((UUID) account.get("id"), (UUID) contact.get("id"), pipelineId, stageId, value(request.opportunityName(), "Opportunity - " + lead.get("display_name")), request.amount(), value(request.currencyCode(), "SAR"), request.expectedCloseDate(), (UUID) lead.get("owner_user_id"))); }
        Instant now = Instant.now();
        jdbc.update("UPDATE crm_leads SET status='CONVERTED',converted_account_id=:accountId,converted_contact_id=:contactId,converted_opportunity_id=:opportunityId,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:leadId AND status<>'CONVERTED'", p().addValue("tenantId", tenantId).addValue("leadId", leadId).addValue("accountId", account.get("id")).addValue("contactId", contact.get("id")).addValue("opportunityId", opportunity == null ? null : opportunity.get("id")).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "LEAD", leadId, "crm.lead.converted", "Lead converted", "CRM_LEAD", leadId, actorId, now);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(); result.put("lead", lead(tenantId, leadId)); result.put("account", account); result.put("contact", contact); result.put("opportunity", opportunity); result.put("idempotent", false); return result;
    }

    @Transactional
    Map<String, Object> createPipeline(Authentication authentication, CreatePipelineRequest request) {
        UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); UUID id = UUID.randomUUID(); Instant now = Instant.now();
        List<String> stages = request.stages() == null || request.stages().isEmpty() ? List.of("New", "Qualified", "Proposal", "Won", "Lost") : request.stages();
        if (stages.size() < 2 || stages.size() > 20) throw bad("pipeline stages must contain 2 to 20 items");
        HashSet<String> uniqueStages = new HashSet<>(); for (String stageName : stages) if (!uniqueStages.add(norm(required(stageName, 160, "stage")))) throw bad("pipeline stage names must be unique");
        jdbc.update("INSERT INTO crm_pipelines (id,tenant_id,name,currency_code,active,created_by,created_at,updated_at) VALUES (:id,:tenantId,:name,:currency,TRUE,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("name", required(request.name(), 160, "name")).addValue("currency", currency(request.currencyCode())).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        List<UUID> stageIds = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) { UUID stageId = UUID.randomUUID(); stageIds.add(stageId); String stageName = required(stages.get(i), 160, "stage"); String terminal = stageName.equalsIgnoreCase("Won") ? "WON" : stageName.equalsIgnoreCase("Lost") ? "LOST" : null; int probability = terminal == null ? Math.min(90, Math.round((i * 100f) / Math.max(1, stages.size() - 1))) : ("WON".equals(terminal) ? 100 : 0); jdbc.update("INSERT INTO crm_pipeline_stages (id,tenant_id,pipeline_id,name,sequence,probability,terminal_state,active) VALUES (:id,:tenantId,:pipelineId,:name,:sequence,:probability,:terminal,TRUE)", p().addValue("id", stageId).addValue("tenantId", tenantId).addValue("pipelineId", id).addValue("name", stageName).addValue("sequence", i).addValue("probability", probability).addValue("terminal", terminal)); }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(pipeline(tenantId, id)); result.put("stageIds", stageIds); return result;
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listPipelines(Authentication authentication) { return jdbc.queryForList("SELECT * FROM crm_pipelines WHERE tenant_id=:tenantId AND active=TRUE ORDER BY created_at DESC,id", p().addValue("tenantId", tenantId(authentication))); }

    @Transactional
    Map<String, Object> createOpportunity(Authentication authentication, CreateOpportunityRequest request) {
        UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); account(tenantId, request.accountId());
        if (request.contactId() != null) { Map<String, Object> contact = contact(tenantId, request.contactId()); Object contactAccountId = contact.get("account_id"); if (contactAccountId != null && !request.accountId().equals(contactAccountId)) throw bad("CRM contact is not linked to the selected account"); }
        pipeline(tenantId, request.pipelineId()); Map<String, Object> stage = stage(tenantId, request.pipelineId(), request.stageId()); extended.validateOwner(tenantId, request.ownerUserId()); UUID id = UUID.randomUUID(); Instant now = Instant.now(); String terminal = stage.get("terminal_state") == null ? null : stage.get("terminal_state").toString(); String status = terminal == null ? "OPEN" : terminal;
        jdbc.update("INSERT INTO crm_opportunities (id,tenant_id,account_id,contact_id,pipeline_id,stage_id,name,amount,currency_code,probability,forecast_category,expected_close_date,owner_user_id,status,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,:accountId,:contactId,:pipelineId,:stageId,:name,:amount,:currency,:probability,'PIPELINE',:expectedCloseDate,:ownerUserId,:status,:actorId,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("accountId", request.accountId()).addValue("contactId", request.contactId()).addValue("pipelineId", request.pipelineId()).addValue("stageId", request.stageId()).addValue("name", required(request.name(), 240, "name")).addValue("amount", request.amount()).addValue("currency", currency(request.currencyCode())).addValue("probability", stage.get("probability")).addValue("expectedCloseDate", request.expectedCloseDate()).addValue("ownerUserId", request.ownerUserId()).addValue("status", status).addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        stageHistory(tenantId, id, null, request.stageId(), actorId, "created"); timeline(tenantId, "OPPORTUNITY", id, "crm.opportunity.created", "Opportunity created", "CRM_OPPORTUNITY", id, actorId, now); return opportunity(tenantId, id);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listOpportunities(Authentication authentication, int requestedLimit, UUID accountId) { UUID tenantId = tenantId(authentication); StringBuilder sql = new StringBuilder("SELECT * FROM crm_opportunities WHERE tenant_id=:tenantId"); MapSqlParameterSource params = p().addValue("tenantId", tenantId); if (accountId != null) { account(tenantId, accountId); sql.append(" AND account_id=:accountId"); params.addValue("accountId", accountId); } sql.append(" ORDER BY updated_at DESC,id LIMIT :limit"); params.addValue("limit", limit(requestedLimit)); return jdbc.queryForList(sql.toString(), params); }

    @Transactional
    Map<String, Object> moveOpportunity(Authentication authentication, UUID opportunityId, MoveOpportunityRequest request) { UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); Map<String, Object> opportunity = opportunity(tenantId, opportunityId); String currentStatus = String.valueOf(opportunity.get("status")); UUID currentStage = (UUID) opportunity.get("stage_id"); if (Set.of("WON", "LOST", "CANCELLED", "ARCHIVED").contains(currentStatus) && !currentStage.equals(request.stageId())) throw conflict("Terminal CRM opportunity cannot move to another stage"); UUID pipelineId = (UUID) opportunity.get("pipeline_id"); Map<String, Object> target = stage(tenantId, pipelineId, request.stageId()); String terminal = target.get("terminal_state") == null ? null : target.get("terminal_state").toString(); String status = terminal == null ? value(request.status(), "OPEN").toUpperCase(Locale.ROOT) : terminal; Instant now = Instant.now(); jdbc.update("UPDATE crm_opportunities SET stage_id=:stageId,status=:status,probability=:probability,win_loss_reason=:reason,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id", context(tenantId, actorId, opportunityId, now).addValue("stageId", request.stageId()).addValue("status", status).addValue("probability", target.get("probability")).addValue("reason", optional(request.reason(), 500, "reason"))); stageHistory(tenantId, opportunityId, currentStage, request.stageId(), actorId, request.reason()); timeline(tenantId, "OPPORTUNITY", opportunityId, "crm.opportunity.stage_changed", "Opportunity stage changed", "CRM_OPPORTUNITY", opportunityId, actorId, now); return opportunity(tenantId, opportunityId); }

    @Transactional
    Map<String, Object> createActivity(Authentication authentication, CreateActivityRequest request) { UUID tenantId = tenantId(authentication); UUID actorId = userId(authentication); extended.validateOwner(tenantId, request.ownerUserId()); extended.validateRelated(tenantId, request.relatedType(), request.relatedId()); if (request.startAt() != null && request.dueAt() != null && request.dueAt().isBefore(request.startAt())) throw bad("dueAt cannot be before startAt"); UUID id = UUID.randomUUID(); Instant now = Instant.now(); String relatedType = request.relatedType() == null ? null : request.relatedType().toUpperCase(Locale.ROOT); jdbc.update("INSERT INTO crm_activities (id,tenant_id,activity_type,subject,body,related_type,related_id,owner_user_id,status,priority,start_at,due_at,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,:type,:subject,:body,:relatedType,:relatedId,:ownerUserId,'OPEN',:priority,:startAt,:dueAt,:actorId,:actorId,:now,:now)", p().addValue("id", id).addValue("tenantId", tenantId).addValue("type", value(request.activityType(), "TASK").toUpperCase(Locale.ROOT)).addValue("subject", required(request.subject(), 240, "subject")).addValue("body", optional(request.body(), 4000, "body")).addValue("relatedType", relatedType).addValue("relatedId", request.relatedId()).addValue("ownerUserId", request.ownerUserId()).addValue("priority", request.priority() == null ? 50 : request.priority()).addValue("startAt", request.startAt()).addValue("dueAt", request.dueAt()).addValue("actorId", actorId).addValue("now", Timestamp.from(now))); if (relatedType != null && request.relatedId() != null) timeline(tenantId, relatedType, request.relatedId(), "crm.activity.created", "Activity created", "CRM_ACTIVITY", id, actorId, now); return activity(tenantId, id); }

    @Transactional(readOnly = true)
    List<Map<String, Object>> timeline(Authentication authentication, String subjectType, UUID subjectId, int requestedLimit) { UUID tenantId = tenantId(authentication); extended.validateRelated(tenantId, subjectType, subjectId); return jdbc.queryForList("SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_type=:subjectType AND subject_id=:subjectId ORDER BY occurred_at DESC,id LIMIT :limit", p().addValue("tenantId", tenantId).addValue("subjectType", subjectType.toUpperCase(Locale.ROOT)).addValue("subjectId", subjectId).addValue("limit", limit(requestedLimit))); }

    private void assertNoAccountCycle(UUID tenantId, UUID accountId, UUID proposedParentId) { UUID cursor = proposedParentId; HashSet<UUID> visited = new HashSet<>(); for (int depth = 0; cursor != null && depth < 100; depth++) { if (cursor.equals(accountId)) throw bad("parentAccountId creates an account hierarchy cycle"); if (!visited.add(cursor)) throw conflict("Existing CRM account hierarchy contains a cycle"); List<UUID> parents = jdbc.query("SELECT parent_account_id FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id", p().addValue("tenantId", tenantId).addValue("id", cursor), (rs, rowNum) -> rs.getObject("parent_account_id", UUID.class)); cursor = parents.isEmpty() ? null : parents.get(0); } if (cursor != null) throw conflict("CRM account hierarchy exceeds maximum depth"); }
    private Map<String, Object> ensureDefaultPipeline(Authentication authentication, String currencyCode) { UUID tenantId = tenantId(authentication); List<Map<String, Object>> existing = jdbc.queryForList("SELECT * FROM crm_pipelines WHERE tenant_id=:tenantId AND name='Default Sales Pipeline' AND active=TRUE", p().addValue("tenantId", tenantId)); return existing.isEmpty() ? createPipeline(authentication, new CreatePipelineRequest("Default Sales Pipeline", currencyCode, List.of("New", "Qualified", "Proposal", "Won", "Lost"))) : existing.get(0); }
    private UUID firstStage(UUID tenantId, UUID pipelineId) { return jdbc.queryForObject("SELECT id FROM crm_pipeline_stages WHERE tenant_id=:tenantId AND pipeline_id=:pipelineId AND active=TRUE ORDER BY sequence,id LIMIT 1", p().addValue("tenantId", tenantId).addValue("pipelineId", pipelineId), UUID.class); }
    private Map<String, Object> account(UUID tenantId, UUID id) { return one("crm_accounts", tenantId, id, "CRM account not found"); }
    private Map<String, Object> contact(UUID tenantId, UUID id) { return one("crm_contacts", tenantId, id, "CRM contact not found"); }
    private Map<String, Object> lead(UUID tenantId, UUID id) { return one("crm_leads", tenantId, id, "CRM lead not found"); }
    private Map<String, Object> pipeline(UUID tenantId, UUID id) { return one("crm_pipelines", tenantId, id, "CRM pipeline not found"); }
    private Map<String, Object> opportunity(UUID tenantId, UUID id) { return one("crm_opportunities", tenantId, id, "CRM opportunity not found"); }
    private Map<String, Object> activity(UUID tenantId, UUID id) { return one("crm_activities", tenantId, id, "CRM activity not found"); }
    private Map<String, Object> stage(UUID tenantId, UUID pipelineId, UUID stageId) { try { return jdbc.queryForMap("SELECT * FROM crm_pipeline_stages WHERE tenant_id=:tenantId AND pipeline_id=:pipelineId AND id=:stageId AND active=TRUE", p().addValue("tenantId", tenantId).addValue("pipelineId", pipelineId).addValue("stageId", stageId)); } catch (EmptyResultDataAccessException exception) { throw missing("CRM pipeline stage not found"); } }
    private Map<String, Object> one(String table, UUID tenantId, UUID id, String message) { if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported CRM table"); try { return jdbc.queryForMap("SELECT * FROM " + table + " WHERE tenant_id=:tenantId AND id=:id", p().addValue("tenantId", tenantId).addValue("id", id)); } catch (EmptyResultDataAccessException exception) { throw missing(message); } }
    private void stageHistory(UUID tenantId, UUID opportunityId, UUID fromStage, UUID toStage, UUID actorId, String reason) { jdbc.update("INSERT INTO crm_opportunity_stage_history (id,tenant_id,opportunity_id,from_stage_id,to_stage_id,changed_by,changed_at,reason) VALUES (:id,:tenantId,:opportunityId,:fromStage,:toStage,:actorId,:now,:reason)", p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId).addValue("opportunityId", opportunityId).addValue("fromStage", fromStage).addValue("toStage", toStage).addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())).addValue("reason", optional(reason, 500, "reason"))); }
    private void timeline(UUID tenantId, String subjectType, UUID subjectId, String eventType, String summary, String sourceType, UUID sourceId, UUID actorId, Instant now) { jdbc.update("INSERT INTO crm_timeline_events (id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) VALUES (:id,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)", p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId).addValue("subjectType", subjectType).addValue("subjectId", subjectId).addValue("eventType", eventType).addValue("summary", summary).addValue("sourceType", sourceType).addValue("sourceId", sourceId).addValue("now", Timestamp.from(now)).addValue("actorId", actorId)); }
    private MapSqlParameterSource context(UUID tenantId, UUID actorId, UUID id, Instant now) { return p().addValue("tenantId", tenantId).addValue("actorId", actorId).addValue("id", id).addValue("now", Timestamp.from(now)); }
    private UUID tenantId(Authentication authentication) { return context(authentication, "tenant_id"); }
    private UUID userId(Authentication authentication) { return context(authentication, "user_id"); }
    private UUID context(Authentication authentication, String key) { if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required"); try { return UUID.fromString(details.get(key).toString()); } catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception); } }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static int limit(int requested) { return Math.max(1, Math.min(requested, 200)); }
    private static String required(String value, int max, String field) { String result = value == null ? "" : value.trim(); if (result.isEmpty()) throw bad(field + " is required"); if (result.length() > max) throw bad(field + " exceeds " + max); return result; }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw bad(field + " exceeds " + max); return result; }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String norm(String value) { return value(value, "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim(); }
    private static String email(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private static String currency(String value) { if (value == null || value.isBlank()) return null; String result = value.trim().toUpperCase(Locale.ROOT); if (!result.matches("[A-Z]{3}")) throw bad("currency must be ISO alpha-3"); return result; }
    private static String locale(String value) { return value == null || value.isBlank() ? null : new Locale.Builder().setLanguageTag(value.trim()).build().toLanguageTag(); }
    private static String zone(String value) { return value == null || value.isBlank() ? null : ZoneId.of(value.trim()).getId(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
