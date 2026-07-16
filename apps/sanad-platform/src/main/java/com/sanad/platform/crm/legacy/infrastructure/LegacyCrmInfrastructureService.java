package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class LegacyCrmInfrastructureService {
    private static final Logger log = LoggerFactory.getLogger(LegacyCrmInfrastructureService.class);
    private static final int MAX_IMPORT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXPANDED_XLSX_BYTES = 50 * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 10_000;
    private static final int MAX_IMPORT_COLUMNS = 100;
    private static final Duration IMPORT_LEASE = Duration.ofMinutes(2);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> CUSTOM_TYPES =
            Set.of("TEXT", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "EMAIL", "URL");
    private static final Set<String> TABLES = Set.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_opportunities", "crm_activities", "crm_import_jobs",
            "crm_custom_field_definitions");
    private static final Map<String, String> ENTITY_TABLES = Map.of(
            "ACCOUNT", "crm_accounts",
            "CONTACT", "crm_contacts",
            "LEAD", "crm_leads",
            "OPPORTUNITY", "crm_opportunities",
            "ACTIVITY", "crm_activities");
    private static final Map<String, Set<String>> IMPORT_FIELDS = Map.of(
            "ACCOUNT", Set.of("displayName", "accountType", "primaryCurrencyCode",
                    "preferredLocale", "timeZone", "source", "ownerUserId", "parentAccountId"),
            "CONTACT", Set.of("accountId", "givenName", "familyName", "primaryEmail",
                    "primaryPhone", "preferredLocale", "timeZone", "ownerUserId", "consentSummary"),
            "LEAD", Set.of("displayName", "companyName", "email", "phone", "source",
                    "ownerUserId", "queueId", "score"),
            "OPPORTUNITY", Set.of("accountId", "contactId", "pipelineId", "stageId",
                    "name", "amount", "currencyCode", "expectedCloseDate", "ownerUserId"),
            "ACTIVITY", Set.of("activityType", "subject", "body", "relatedType",
                    "relatedId", "ownerUserId", "priority", "startAt", "dueAt"));
    private static final Map<String, Set<String>> REQUIRED_IMPORT_FIELDS = Map.of(
            "ACCOUNT", Set.of("displayName"),
            "CONTACT", Set.of("givenName"),
            "LEAD", Set.of("displayName"),
            "OPPORTUNITY", Set.of("accountId", "pipelineId", "stageId", "name", "currencyCode"),
            "ACTIVITY", Set.of("subject"));

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate requiresNew;
    private final boolean importWorkerEnabled;
    private final String workerId = UUID.randomUUID().toString();
    private final SecretKeySpec customFieldKey;

    public LegacyCrmInfrastructureService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${sanad.crm.import-worker-enabled:true}") boolean importWorkerEnabled,
            @Value("${sanad.crm.custom-field-encryption-key:}") String encryptionKey) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.importWorkerEnabled = importWorkerEnabled;
        this.customFieldKey = decodeEncryptionKey(encryptionKey);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", scalarLong(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'",
                params));
        result.put("contacts", scalarLong(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'",
                params));
        result.put("openLeads", scalarLong(
                "SELECT COUNT(*) FROM crm_leads WHERE tenant_id=:tenantId AND status NOT IN ('CONVERTED','DISQUALIFIED','ARCHIVED')",
                params));
        result.put("openOpportunities", scalarLong(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'",
                params));
        BigDecimal weighted = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(amount,0) * probability / 100),0) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'",
                params, BigDecimal.class);
        result.put("weightedPipeline", weighted == null ? BigDecimal.ZERO : weighted);
        result.put("overdueActivities", scalarLong(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id=:tenantId AND status IN ('OPEN','IN_PROGRESS') AND due_at IS NOT NULL AND due_at<CURRENT_TIMESTAMP",
                params));
        result.put("recentActivity", jdbc.queryForList(
                "SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId ORDER BY occurred_at DESC,id LIMIT 10",
                params));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customer360(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("account", one("crm_accounts", tenantId, accountId, "CRM account not found"));
        result.put("customFields", readCustomFieldValuesInternal(tenantId, "ACCOUNT", accountId, false));
        MapSqlParameterSource params = p().addValue("tenantId", tenantId).addValue("accountId", accountId);
        result.put("contacts", jdbc.queryForList(
                "SELECT * FROM crm_contacts WHERE tenant_id=:tenantId AND account_id=:accountId AND lifecycle_status<>'ARCHIVED' ORDER BY updated_at DESC,id",
                params));
        result.put("opportunities", jdbc.queryForList(
                "SELECT opportunity.*,pipeline.name AS pipeline_name,stage.name AS stage_name " +
                        "FROM crm_opportunities opportunity " +
                        "JOIN crm_pipelines pipeline ON pipeline.tenant_id=opportunity.tenant_id AND pipeline.id=opportunity.pipeline_id " +
                        "JOIN crm_pipeline_stages stage ON stage.tenant_id=opportunity.tenant_id AND stage.id=opportunity.stage_id " +
                        "WHERE opportunity.tenant_id=:tenantId AND opportunity.account_id=:accountId " +
                        "ORDER BY opportunity.updated_at DESC,opportunity.id",
                params));
        result.put("activities", jdbc.queryForList(
                "SELECT * FROM crm_activities WHERE tenant_id=:tenantId AND related_type='ACCOUNT' AND related_id=:accountId ORDER BY created_at DESC,id LIMIT 100",
                params));
        result.put("timeline", jdbc.queryForList(
                "SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_type='ACCOUNT' AND subject_id=:accountId ORDER BY occurred_at DESC,id LIMIT 200",
                params));
        return result;
    }

    @Transactional
    public Map<String, Object> restoreAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Instant now = Instant.now();
        int changed = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status='ACTIVE',archived_at=NULL,updated_at=:now,updated_by=:actorId,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status='ARCHIVED'",
                context(tenantId, actorId, accountId, now));
        if (changed != 1) throw conflict("CRM account is not archived or does not exist");
        timeline(tenantId, "ACCOUNT", accountId, "crm.account.restored",
                "Account restored", "CRM_ACCOUNT", accountId, actorId, now);
        return one("crm_accounts", tenantId, accountId, "CRM account not found");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getContact(Authentication authentication, UUID contactId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(one("crm_contacts", tenantId, contactId, "CRM contact not found"));
        result.put("custom_fields", readCustomFieldValuesInternal(tenantId, "CONTACT", contactId, false));
        return result;
    }

    @Transactional
    public Map<String, Object> updateContact(
            Authentication authentication, UUID contactId, UpdateContactRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> existing =
                one("crm_contacts", tenantId, contactId, "CRM contact not found");
        if ("ARCHIVED".equals(existing.get("lifecycle_status"))) {
            throw conflict("Archived CRM contact cannot be updated");
        }
        if (request.accountId() != null) {
            one("crm_accounts", tenantId, request.accountId(), "CRM account not found");
        }
        validateOwner(tenantId, request.ownerUserId());
        String given = optional(request.givenName(), 120, "givenName");
        String family = optional(request.familyName(), 120, "familyName");
        String displayName = null;
        if (given != null || family != null) {
            String actualGiven = given == null ? String.valueOf(existing.get("given_name")) : given;
            Object currentFamily = existing.get("family_name");
            String actualFamily =
                    family == null && currentFamily != null ? currentFamily.toString() : family;
            displayName = actualFamily == null || actualFamily.isBlank()
                    ? actualGiven : actualGiven + " " + actualFamily;
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE crm_contacts SET account_id=COALESCE(:accountId,account_id)," +
                        "given_name=COALESCE(:givenName,given_name),family_name=COALESCE(:familyName,family_name)," +
                        "display_name=COALESCE(:displayName,display_name),normalized_name=COALESCE(:normalizedName,normalized_name)," +
                        "primary_email=COALESCE(:email,primary_email),normalized_email=COALESCE(:normalizedEmail,normalized_email)," +
                        "primary_phone=COALESCE(:phone,primary_phone),preferred_locale=COALESCE(:locale,preferred_locale)," +
                        "time_zone=COALESCE(:timeZone,time_zone),owner_user_id=COALESCE(:ownerUserId,owner_user_id)," +
                        "consent_summary=COALESCE(:consent,consent_summary),updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("accountId", request.accountId()).addValue("givenName", given)
                        .addValue("familyName", family).addValue("displayName", displayName)
                        .addValue("normalizedName", displayName == null ? null : normalize(displayName))
                        .addValue("email", optional(request.primaryEmail(), 255, "primaryEmail"))
                        .addValue("normalizedEmail", normalizeEmail(request.primaryEmail()))
                        .addValue("phone", optional(request.primaryPhone(), 64, "primaryPhone"))
                        .addValue("locale", optional(request.preferredLocale(), 35, "preferredLocale"))
                        .addValue("timeZone", optional(request.timeZone(), 64, "timeZone"))
                        .addValue("ownerUserId", request.ownerUserId())
                        .addValue("consent", upper(request.consentSummary()))
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        timeline(tenantId, "CONTACT", contactId, "crm.contact.updated",
                "Contact updated", "CRM_CONTACT", contactId, actorId, now);
        return getContact(authentication, contactId);
    }

    @Transactional
    public Map<String, Object> archiveContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, true);
    }

    @Transactional
    public Map<String, Object> restoreContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, false);
    }

    private Map<String, Object> changeContactArchive(
            Authentication authentication, UUID contactId, boolean archive) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Instant now = Instant.now();
        String expected = archive ? "ACTIVE" : "ARCHIVED";
        String next = archive ? "ARCHIVED" : "ACTIVE";
        int changed = jdbc.update(
                "UPDATE crm_contacts SET lifecycle_status=:nextStatus,archived_at=:archivedAt," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status=:expectedStatus",
                p().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("nextStatus", next).addValue("expectedStatus", expected)
                        .addValue("archivedAt", archive ? Timestamp.from(now) : null)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (changed != 1) throw conflict("CRM contact lifecycle transition is not allowed");
        timeline(tenantId, "CONTACT", contactId,
                archive ? "crm.contact.archived" : "crm.contact.restored",
                archive ? "Contact archived" : "Contact restored",
                "CRM_CONTACT", contactId, actorId, now);
        return one("crm_contacts", tenantId, contactId, "CRM contact not found");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLead(Authentication authentication, UUID leadId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(one("crm_leads", tenantId, leadId, "CRM lead not found"));
        result.put("custom_fields", readCustomFieldValuesInternal(tenantId, "LEAD", leadId, false));
        return result;
    }

    @Transactional
    public Map<String, Object> changeLeadStatus(
            Authentication authentication, UUID leadId, UpdateLeadStatusRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> lead = one("crm_leads", tenantId, leadId, "CRM lead not found");
        String current = String.valueOf(lead.get("status"));
        String next = request.status().trim().toUpperCase(Locale.ROOT);
        if (!leadTransitionAllowed(current, next)) {
            throw conflict("Invalid CRM lead status transition: " + current + " -> " + next);
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE crm_leads SET status=:status,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                context(tenantId, actorId, leadId, now).addValue("status", next));
        timeline(tenantId, "LEAD", leadId, "crm.lead.status_changed",
                "Lead status changed to " + next, "CRM_LEAD", leadId, actorId, now);
        return getLead(authentication, leadId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPipelineStages(
            Authentication authentication, UUID pipelineId) {
        UUID tenantId = tenantId(authentication);
        one("crm_pipelines", tenantId, pipelineId, "CRM pipeline not found");
        return jdbc.queryForList(
                "SELECT * FROM crm_pipeline_stages WHERE tenant_id=:tenantId AND pipeline_id=:pipelineId AND active=TRUE ORDER BY sequence,id",
                p().addValue("tenantId", tenantId).addValue("pipelineId", pipelineId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOpportunity(Authentication authentication, UUID opportunityId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(one("crm_opportunities", tenantId, opportunityId,
                        "CRM opportunity not found"));
        result.put("stageHistory", jdbc.queryForList(
                "SELECT history.*,from_stage.name AS from_stage_name,to_stage.name AS to_stage_name " +
                        "FROM crm_opportunity_stage_history history " +
                        "LEFT JOIN crm_pipeline_stages from_stage ON from_stage.tenant_id=history.tenant_id AND from_stage.id=history.from_stage_id " +
                        "JOIN crm_pipeline_stages to_stage ON to_stage.tenant_id=history.tenant_id AND to_stage.id=history.to_stage_id " +
                        "WHERE history.tenant_id=:tenantId AND history.opportunity_id=:opportunityId " +
                        "ORDER BY history.changed_at DESC,history.id",
                p().addValue("tenantId", tenantId).addValue("opportunityId", opportunityId)));
        result.put("custom_fields",
                readCustomFieldValuesInternal(tenantId, "OPPORTUNITY", opportunityId, false));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActivities(
            Authentication authentication, int requestedLimit,
            String relatedType, UUID relatedId, String status) {
        UUID tenantId = tenantId(authentication);
        StringBuilder sql =
                new StringBuilder("SELECT * FROM crm_activities WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (relatedType != null && !relatedType.isBlank()) {
            sql.append(" AND related_type=:relatedType");
            params.addValue("relatedType", relatedType.trim().toUpperCase(Locale.ROOT));
        }
        if (relatedId != null) {
            sql.append(" AND related_id=:relatedId");
            params.addValue("relatedId", relatedId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=:status");
            params.addValue("status", status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit");
        params.addValue("limit", limit(requestedLimit));
        return jdbc.queryForList(sql.toString(), params);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActivity(Authentication authentication, UUID activityId) {
        UUID tenantId = tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(one("crm_activities", tenantId, activityId,
                        "CRM activity not found"));
        result.put("custom_fields",
                readCustomFieldValuesInternal(tenantId, "ACTIVITY", activityId, false));
        return result;
    }

    @Transactional
    public Map<String, Object> completeActivity(
            Authentication authentication, UUID activityId, CompleteActivityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        Map<String, Object> activity =
                one("crm_activities", tenantId, activityId, "CRM activity not found");
        String status = String.valueOf(activity.get("status"));
        if (!Set.of("OPEN", "IN_PROGRESS").contains(status)) {
            throw conflict("CRM activity cannot be completed from status " + status);
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE crm_activities SET status='COMPLETED',completed_at=:now,body=COALESCE(:result,body)," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                context(tenantId, actorId, activityId, now)
                        .addValue("result", optional(request.result(), 4000, "result")));
        Object relatedType = activity.get("related_type");
        Object relatedId = activity.get("related_id");
        if (relatedType != null && relatedId != null) {
            timeline(tenantId, relatedType.toString(), asUuid(relatedId),
                    "crm.activity.completed", "Activity completed",
                    "CRM_ACTIVITY", activityId, actorId, now);
        }
        return getActivity(authentication, activityId);
    }

    @Transactional
    public Map<String, Object> uploadImport(
            Authentication authentication, String requestedEntityType,
            String mappingJson, MultipartFile file) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        String entityType = importEntityType(requestedEntityType);
        byte[] content = importFileBytes(file);
        ParsedTable table = parseImportFile(file.getOriginalFilename(), file.getContentType(), content);
        Map<String, String> mapping = resolveMapping(tenantId, entityType, table.headers(), mappingJson);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String canonicalMapping = writeJson(mapping);
        jdbc.update(
                "INSERT INTO crm_import_jobs " +
                        "(id,tenant_id,entity_type,status,total_rows,processed_rows,succeeded_rows,failed_rows," +
                        "requested_by,created_at,updated_at,original_filename,content_type,file_size_bytes,file_sha256,mapping_json) " +
                        "VALUES (:id,:tenantId,:entityType,'READY',:totalRows,0,0,0,:actorId,:now,:now," +
                        ":filename,:contentType,:fileSize,:sha256,:mappingJson)",
                p().addValue("id", id).addValue("tenantId", tenantId)
                        .addValue("entityType", entityType).addValue("totalRows", table.rows().size())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now))
                        .addValue("filename", safeFilename(file.getOriginalFilename()))
                        .addValue("contentType", optional(file.getContentType(), 120, "contentType"))
                        .addValue("fileSize", content.length).addValue("sha256", sha256(content))
                        .addValue("mappingJson", canonicalMapping));
        jdbc.update(
                "INSERT INTO crm_import_files (id,tenant_id,import_job_id,content_base64,created_at) " +
                        "VALUES (:id,:tenantId,:jobId,:content,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("jobId", id)
                        .addValue("content", Base64.getEncoder().encodeToString(content))
                        .addValue("now", Timestamp.from(now)));
        return getImportJobInternal(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImportJobs(
            Authentication authentication, int requestedLimit) {
        return jdbc.queryForList(
                "SELECT job.*,(SELECT COUNT(*) FROM crm_import_errors error " +
                        "WHERE error.tenant_id=job.tenant_id AND error.import_job_id=job.id) AS error_count " +
                        "FROM crm_import_jobs job WHERE job.tenant_id=:tenantId " +
                        "ORDER BY job.created_at DESC,job.id LIMIT :limit",
                p().addValue("tenantId", tenantId(authentication))
                        .addValue("limit", limit(requestedLimit)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getImportJob(Authentication authentication, UUID jobId) {
        return getImportJobInternal(tenantId(authentication), jobId);
    }

    @Transactional
    public Map<String, Object> runImport(Authentication authentication, UUID jobId) {
        UUID tenantId = tenantId(authentication);
        Map<String, Object> job = one("crm_import_jobs", tenantId, jobId, "CRM import job not found");
        String status = String.valueOf(job.get("status"));
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw conflict("CRM import job cannot be queued from status " + status);
        }
        if ("RUNNING".equals(status) && !leaseExpired(job.get("lease_expires_at"))) {
            return getImportJobInternal(tenantId, jobId);
        }
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET status='READY',worker_id=NULL,lease_expires_at=NULL," +
                        "completed_at=NULL,last_error=NULL,updated_at=:now " +
                        "WHERE tenant_id=:tenantId AND id=:id AND status IN ('UPLOADED','READY','FAILED','RUNNING')",
                p().addValue("tenantId", tenantId).addValue("id", jobId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (changed != 1) throw conflict("CRM import job cannot be queued");
        return getImportJobInternal(tenantId, jobId);
    }

    @Transactional
    public Map<String, Object> cancelImport(Authentication authentication, UUID jobId) {
        UUID tenantId = tenantId(authentication);
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET status='CANCELLED',worker_id=NULL,lease_expires_at=NULL," +
                        "completed_at=:now,updated_at=:now " +
                        "WHERE tenant_id=:tenantId AND id=:id AND status IN ('UPLOADED','READY','FAILED')",
                p().addValue("tenantId", tenantId).addValue("id", jobId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (changed != 1) {
            throw conflict("Only queued or failed CRM import jobs can be cancelled");
        }
        return getImportJobInternal(tenantId, jobId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImportErrors(
            Authentication authentication, UUID jobId, int requestedLimit) {
        UUID tenantId = tenantId(authentication);
        one("crm_import_jobs", tenantId, jobId, "CRM import job not found");
        return jdbc.queryForList(
                "SELECT * FROM crm_import_errors WHERE tenant_id=:tenantId AND import_job_id=:jobId " +
                        "ORDER BY row_number,id LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("jobId", jobId)
                        .addValue("limit", Math.max(1, Math.min(requestedLimit, 5000))));
    }

    @Transactional(readOnly = true)
    public String importErrorsCsv(Authentication authentication, UUID jobId) {
        List<Map<String, Object>> errors = listImportErrors(authentication, jobId, 5000);
        StringBuilder csv = new StringBuilder();
        csv.append("row_number,field_name,error_code,message,raw_row\r\n");
        for (Map<String, Object> error : errors) {
            csv.append(csv(error.get("row_number"))).append(',')
                    .append(csv(error.get("field_name"))).append(',')
                    .append(csv(error.get("error_code"))).append(',')
                    .append(csv(error.get("message"))).append(',')
                    .append(csv(error.get("raw_row"))).append("\r\n");
        }
        return csv.toString();
    }

    @Scheduled(
            initialDelayString = "${sanad.crm.import-worker-initial-delay-ms:5000}",
            fixedDelayString = "${sanad.crm.import-worker-delay-ms:5000}")
    public void processNextImport() {
        if (!importWorkerEnabled) return;
        try {
            processNextImportNow();
        } catch (RuntimeException exception) {
            log.error("CRM import worker iteration failed", exception);
        }
    }

    public boolean processNextImportNow() {
        UUID jobId = transaction.execute(status -> claimNextImport());
        if (jobId == null) return false;
        try {
            processClaimedImport(jobId);
        } catch (RuntimeException exception) {
            markImportFailed(jobId, exception);
        }
        return true;
    }

    private UUID claimNextImport() {
        List<UUID> candidates = jdbc.queryForList(
                "SELECT id FROM crm_import_jobs " +
                        "WHERE status='READY' OR (status='RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at<CURRENT_TIMESTAMP)) " +
                        "ORDER BY created_at,id LIMIT 1",
                p(), UUID.class);
        if (candidates.isEmpty()) return null;
        UUID jobId = candidates.get(0);
        Instant now = Instant.now();
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET status='RUNNING',worker_id=:workerId," +
                        "lease_expires_at=:lease,started_at=COALESCE(started_at,:now)," +
                        "attempt_count=attempt_count+1,updated_at=:now " +
                        "WHERE id=:id AND (status='READY' OR (status='RUNNING' AND " +
                        "(lease_expires_at IS NULL OR lease_expires_at<CURRENT_TIMESTAMP)))",
                p().addValue("workerId", workerId)
                        .addValue("lease", Timestamp.from(now.plus(IMPORT_LEASE)))
                        .addValue("now", Timestamp.from(now)).addValue("id", jobId));
        return changed == 1 ? jobId : null;
    }

    private void processClaimedImport(UUID jobId) {
        ImportPayload payload = transaction.execute(status -> loadImportPayload(jobId));
        if (payload == null) throw new IllegalStateException("CRM import payload not found");
        if (!sha256(payload.content()).equals(payload.sha256())) {
            throw new IllegalStateException("CRM import file checksum mismatch");
        }
        ParsedTable table = parseImportFile(payload.filename(), payload.contentType(), payload.content());
        if (table.rows().size() != payload.totalRows()) {
            throw new IllegalStateException("CRM import row count changed after upload");
        }
        Map<String, String> mapping = readMapping(payload.mappingJson());
        long processed = payload.processedRows();
        for (int index = Math.toIntExact(processed); index < table.rows().size(); index++) {
            Map<String, String> sourceRow = table.rows().get(index);
            long rowNumber = index + 2L;
            try {
                requiresNew.executeWithoutResult(status ->
                        processImportRow(payload, sourceRow, mapping, rowNumber));
            } catch (ImportLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                requiresNew.executeWithoutResult(status ->
                        recordImportError(payload, sourceRow, rowNumber, exception));
            }
        }
        transaction.executeWithoutResult(status -> completeImport(jobId));
    }

    private ImportPayload loadImportPayload(UUID jobId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT job.*,file.content_base64 FROM crm_import_jobs job " +
                            "JOIN crm_import_files file ON file.tenant_id=job.tenant_id AND file.import_job_id=job.id " +
                            "WHERE job.id=:id AND job.status='RUNNING' AND job.worker_id=:workerId",
                    p().addValue("id", jobId).addValue("workerId", workerId));
            return new ImportPayload(
                    asUuid(row.get("id")), asUuid(row.get("tenant_id")),
                    String.valueOf(row.get("entity_type")), asUuid(row.get("requested_by")),
                    String.valueOf(row.get("original_filename")),
                    row.get("content_type") == null ? null : row.get("content_type").toString(),
                    String.valueOf(row.get("file_sha256")),
                    String.valueOf(row.get("mapping_json")),
                    ((Number) row.get("total_rows")).intValue(),
                    ((Number) row.get("processed_rows")).longValue(),
                    Base64.getDecoder().decode(String.valueOf(row.get("content_base64"))));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void processImportRow(
            ImportPayload payload, Map<String, String> sourceRow,
            Map<String, String> mapping, long rowNumber) {
        assertImportLease(payload.id());
        Map<String, String> values = mappedValues(sourceRow, mapping);
        UUID entityId = switch (payload.entityType()) {
            case "ACCOUNT" -> importAccount(payload, values);
            case "CONTACT" -> importContact(payload, values);
            case "LEAD" -> importLead(payload, values);
            case "OPPORTUNITY" -> importOpportunity(payload, values);
            case "ACTIVITY" -> importActivity(payload, values);
            default -> throw bad("Unsupported CRM import entityType");
        };
        Map<String, Object> customValues = customImportValues(values);
        if (!customValues.isEmpty() || hasRequiredCustomFields(payload.tenantId(), payload.entityType())) {
            upsertCustomFieldValuesInternal(
                    payload.tenantId(), payload.actorId(), payload.entityType(),
                    entityId, customValues, true);
        }
        Instant now = Instant.now();
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET processed_rows=processed_rows+1," +
                        "succeeded_rows=succeeded_rows+1,updated_at=:now,lease_expires_at=:lease " +
                        "WHERE id=:id AND status='RUNNING' AND worker_id=:workerId",
                p().addValue("id", payload.id()).addValue("workerId", workerId)
                        .addValue("now", Timestamp.from(now))
                        .addValue("lease", Timestamp.from(now.plus(IMPORT_LEASE))));
        if (changed != 1) throw new ImportLeaseLostException();
    }

    private void recordImportError(
            ImportPayload payload, Map<String, String> sourceRow,
            long rowNumber, RuntimeException exception) {
        assertImportLease(payload.id());
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_import_errors " +
                        "(id,tenant_id,import_job_id,row_number,field_name,error_code,message,raw_row,created_at) " +
                        "VALUES (:id,:tenantId,:jobId,:rowNumber,:fieldName,:errorCode,:message,:rawRow,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", payload.tenantId())
                        .addValue("jobId", payload.id()).addValue("rowNumber", rowNumber)
                        .addValue("fieldName", null).addValue("errorCode", errorCode(exception))
                        .addValue("message", errorMessage(exception))
                        .addValue("rawRow", truncate(writeJson(sourceRow), 8000))
                        .addValue("now", Timestamp.from(now)));
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET processed_rows=processed_rows+1," +
                        "failed_rows=failed_rows+1,last_error=:message,updated_at=:now,lease_expires_at=:lease " +
                        "WHERE id=:id AND status='RUNNING' AND worker_id=:workerId",
                p().addValue("id", payload.id()).addValue("workerId", workerId)
                        .addValue("message", errorMessage(exception))
                        .addValue("now", Timestamp.from(now))
                        .addValue("lease", Timestamp.from(now.plus(IMPORT_LEASE))));
        if (changed != 1) throw new ImportLeaseLostException();
    }

    private void completeImport(UUID jobId) {
        Instant now = Instant.now();
        int changed = jdbc.update(
                "UPDATE crm_import_jobs SET status='COMPLETED',completed_at=:now,updated_at=:now," +
                        "worker_id=NULL,lease_expires_at=NULL," +
                        "last_error=CASE WHEN failed_rows>0 THEN CONCAT(failed_rows,' row(s) failed') ELSE NULL END " +
                        "WHERE id=:id AND status='RUNNING' AND worker_id=:workerId AND processed_rows=total_rows",
                p().addValue("id", jobId).addValue("workerId", workerId)
                        .addValue("now", Timestamp.from(now)));
        if (changed != 1) throw new ImportLeaseLostException();
    }

    private void markImportFailed(UUID jobId, RuntimeException exception) {
        if (exception instanceof ImportLeaseLostException) {
            log.info("CRM import lease was lost for job {}", jobId);
            return;
        }
        try {
            transaction.executeWithoutResult(status -> jdbc.update(
                    "UPDATE crm_import_jobs SET status='FAILED',last_error=:message,completed_at=:now," +
                            "updated_at=:now,worker_id=NULL,lease_expires_at=NULL " +
                            "WHERE id=:id AND worker_id=:workerId",
                    p().addValue("id", jobId).addValue("workerId", workerId)
                            .addValue("message", errorMessage(exception))
                            .addValue("now", Timestamp.from(Instant.now()))));
        } catch (RuntimeException persistenceFailure) {
            log.error("Unable to persist CRM import failure for job {}", jobId, persistenceFailure);
        }
    }

    private void assertImportLease(UUID jobId) {
        if (scalarLong(
                "SELECT COUNT(*) FROM crm_import_jobs WHERE id=:id AND status='RUNNING' " +
                        "AND worker_id=:workerId AND lease_expires_at>CURRENT_TIMESTAMP",
                p().addValue("id", jobId).addValue("workerId", workerId)) != 1) {
            throw new ImportLeaseLostException();
        }
    }

    private UUID importAccount(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = required(values, "displayName", 240);
        String accountType = value(values.get("accountType"), "BUSINESS").toUpperCase(Locale.ROOT);
        if (!Set.of("BUSINESS", "PERSON", "PARTNER", "PROSPECT", "OTHER").contains(accountType)) {
            throw bad("Invalid accountType");
        }
        UUID ownerId = uuid(values.get("ownerUserId"), "ownerUserId", false);
        UUID parentId = uuid(values.get("parentAccountId"), "parentAccountId", false);
        validateOwner(payload.tenantId(), ownerId);
        if (parentId != null) {
            one("crm_accounts", payload.tenantId(), parentId, "Parent CRM account not found");
        }
        jdbc.update(
                "INSERT INTO crm_accounts " +
                        "(id,tenant_id,display_name,normalized_name,account_type,lifecycle_status,parent_account_id," +
                        "owner_user_id,primary_currency_code,preferred_locale,time_zone,source,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:displayName,:normalizedName,:accountType,'ACTIVE',:parentId," +
                        ":ownerId,:currency,:locale,:timeZone,:source,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("displayName", displayName).addValue("normalizedName", normalize(displayName))
                        .addValue("accountType", accountType).addValue("parentId", parentId)
                        .addValue("ownerId", ownerId)
                        .addValue("currency", currency(values.get("primaryCurrencyCode")))
                        .addValue("locale", value(values.get("preferredLocale"), "ar-SA"))
                        .addValue("timeZone", value(values.get("timeZone"), "Asia/Riyadh"))
                        .addValue("source", optional(values.get("source"), 80, "source"))
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        timeline(payload.tenantId(), "ACCOUNT", id, "crm.account.imported",
                "Account imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        return id;
    }

    private UUID importContact(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String given = required(values, "givenName", 120);
        String family = optional(values.get("familyName"), 120, "familyName");
        String display = family == null ? given : given + " " + family;
        UUID accountId = uuid(values.get("accountId"), "accountId", false);
        UUID ownerId = uuid(values.get("ownerUserId"), "ownerUserId", false);
        if (accountId != null) {
            one("crm_accounts", payload.tenantId(), accountId, "CRM account not found");
        }
        validateOwner(payload.tenantId(), ownerId);
        String email = optional(values.get("primaryEmail"), 255, "primaryEmail");
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Invalid primaryEmail");
        String consent = value(values.get("consentSummary"), "UNKNOWN").toUpperCase(Locale.ROOT);
        if (!Set.of("UNKNOWN", "GRANTED", "DENIED", "WITHDRAWN").contains(consent)) {
            throw bad("Invalid consentSummary");
        }
        jdbc.update(
                "INSERT INTO crm_contacts " +
                        "(id,tenant_id,account_id,given_name,family_name,display_name,normalized_name,primary_email," +
                        "normalized_email,primary_phone,preferred_locale,time_zone,lifecycle_status,owner_user_id," +
                        "consent_summary,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:accountId,:givenName,:familyName,:displayName,:normalizedName,:email," +
                        ":normalizedEmail,:phone,:locale,:timeZone,'ACTIVE',:ownerId,:consent,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("accountId", accountId).addValue("givenName", given)
                        .addValue("familyName", family).addValue("displayName", display)
                        .addValue("normalizedName", normalize(display)).addValue("email", email)
                        .addValue("normalizedEmail", normalizeEmail(email))
                        .addValue("phone", optional(values.get("primaryPhone"), 64, "primaryPhone"))
                        .addValue("locale", value(values.get("preferredLocale"), "ar-SA"))
                        .addValue("timeZone", value(values.get("timeZone"), "Asia/Riyadh"))
                        .addValue("ownerId", ownerId).addValue("consent", consent)
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        timeline(payload.tenantId(), "CONTACT", id, "crm.contact.imported",
                "Contact imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        if (accountId != null) {
            timeline(payload.tenantId(), "ACCOUNT", accountId, "crm.contact.linked",
                    "Imported contact linked", "CRM_CONTACT", id, payload.actorId(), now);
        }
        return id;
    }

    private UUID importLead(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = required(values, "displayName", 240);
        UUID ownerId = uuid(values.get("ownerUserId"), "ownerUserId", false);
        UUID queueId = uuid(values.get("queueId"), "queueId", false);
        validateOwner(payload.tenantId(), ownerId);
        String email = optional(values.get("email"), 255, "email");
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Invalid email");
        BigDecimal score = decimal(values.get("score"), "score", false);
        if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw bad("score must be between 0 and 100");
        }
        jdbc.update(
                "INSERT INTO crm_leads " +
                        "(id,tenant_id,display_name,normalized_name,company_name,email,normalized_email,phone,source," +
                        "status,owner_user_id,queue_id,score,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:displayName,:normalizedName,:companyName,:email,:normalizedEmail,:phone," +
                        ":source,'NEW',:ownerId,:queueId,:score,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("displayName", displayName).addValue("normalizedName", normalize(displayName))
                        .addValue("companyName", optional(values.get("companyName"), 240, "companyName"))
                        .addValue("email", email).addValue("normalizedEmail", normalizeEmail(email))
                        .addValue("phone", optional(values.get("phone"), 64, "phone"))
                        .addValue("source", optional(values.get("source"), 120, "source"))
                        .addValue("ownerId", ownerId).addValue("queueId", queueId)
                        .addValue("score", score).addValue("actorId", payload.actorId())
                        .addValue("now", Timestamp.from(now)));
        timeline(payload.tenantId(), "LEAD", id, "crm.lead.imported",
                "Lead imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        return id;
    }

    private UUID importOpportunity(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UUID accountId = uuid(values.get("accountId"), "accountId", true);
        UUID contactId = uuid(values.get("contactId"), "contactId", false);
        UUID pipelineId = uuid(values.get("pipelineId"), "pipelineId", true);
        UUID stageId = uuid(values.get("stageId"), "stageId", true);
        UUID ownerId = uuid(values.get("ownerUserId"), "ownerUserId", false);
        one("crm_accounts", payload.tenantId(), accountId, "CRM account not found");
        if (contactId != null) {
            one("crm_contacts", payload.tenantId(), contactId, "CRM contact not found");
        }
        validateOwner(payload.tenantId(), ownerId);
        Map<String, Object> stage;
        try {
            stage = jdbc.queryForMap(
                    "SELECT stage.* FROM crm_pipeline_stages stage " +
                            "JOIN crm_pipelines pipeline ON pipeline.tenant_id=stage.tenant_id AND pipeline.id=stage.pipeline_id " +
                            "WHERE stage.tenant_id=:tenantId AND stage.id=:stageId AND stage.pipeline_id=:pipelineId " +
                            "AND stage.active=TRUE AND pipeline.active=TRUE",
                    p().addValue("tenantId", payload.tenantId()).addValue("stageId", stageId)
                            .addValue("pipelineId", pipelineId));
        } catch (EmptyResultDataAccessException exception) {
            throw missing("CRM pipeline stage not found");
        }
        String terminal = stage.get("terminal_state") == null
                ? null : stage.get("terminal_state").toString();
        String status = "WON".equals(terminal) ? "WON" : "LOST".equals(terminal) ? "LOST" : "OPEN";
        BigDecimal probability = (BigDecimal) stage.get("probability");
        BigDecimal amount = decimal(values.get("amount"), "amount", false);
        if (amount != null && amount.signum() < 0) throw bad("amount cannot be negative");
        String currencyCode = currency(required(values, "currencyCode", 3));
        LocalDate closeDate = localDate(values.get("expectedCloseDate"), "expectedCloseDate", false);
        jdbc.update(
                "INSERT INTO crm_opportunities " +
                        "(id,tenant_id,account_id,contact_id,pipeline_id,stage_id,name,amount,currency_code,probability," +
                        "status,expected_close_date,owner_user_id,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:accountId,:contactId,:pipelineId,:stageId,:name,:amount,:currency," +
                        ":probability,:status,:closeDate,:ownerId,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("accountId", accountId).addValue("contactId", contactId)
                        .addValue("pipelineId", pipelineId).addValue("stageId", stageId)
                        .addValue("name", required(values, "name", 240)).addValue("amount", amount)
                        .addValue("currency", currencyCode).addValue("probability", probability)
                        .addValue("status", status).addValue("closeDate", closeDate)
                        .addValue("ownerId", ownerId).addValue("actorId", payload.actorId())
                        .addValue("now", Timestamp.from(now)));
        jdbc.update(
                "INSERT INTO crm_opportunity_stage_history " +
                        "(id,tenant_id,opportunity_id,from_stage_id,to_stage_id,changed_by,changed_at,reason) " +
                        "VALUES (:id,:tenantId,:opportunityId,NULL,:stageId,:actorId,:now,'Imported')",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", payload.tenantId())
                        .addValue("opportunityId", id).addValue("stageId", stageId)
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        timeline(payload.tenantId(), "OPPORTUNITY", id, "crm.opportunity.imported",
                "Opportunity imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        timeline(payload.tenantId(), "ACCOUNT", accountId, "crm.opportunity.created",
                "Imported opportunity created", "CRM_OPPORTUNITY", id, payload.actorId(), now);
        return id;
    }

    private UUID importActivity(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String type = value(values.get("activityType"), "TASK").toUpperCase(Locale.ROOT);
        if (!Set.of("TASK", "CALL", "MEETING", "EMAIL", "NOTE", "MESSAGE", "OTHER").contains(type)) {
            throw bad("Invalid activityType");
        }
        String relatedType = upper(values.get("relatedType"));
        UUID relatedId = uuid(values.get("relatedId"), "relatedId", false);
        validateRelated(payload.tenantId(), relatedType, relatedId);
        UUID ownerId = uuid(values.get("ownerUserId"), "ownerUserId", false);
        validateOwner(payload.tenantId(), ownerId);
        Integer priority = integer(values.get("priority"), "priority", false);
        if (priority == null) priority = 50;
        if (priority < 0 || priority > 100) throw bad("priority must be between 0 and 100");
        OffsetDateTime startAt = offsetDateTime(values.get("startAt"), "startAt", false);
        OffsetDateTime dueAt = offsetDateTime(values.get("dueAt"), "dueAt", false);
        jdbc.update(
                "INSERT INTO crm_activities " +
                        "(id,tenant_id,activity_type,subject,body,related_type,related_id,owner_user_id,status,priority," +
                        "start_at,due_at,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:type,:subject,:body,:relatedType,:relatedId,:ownerId,'OPEN',:priority," +
                        ":startAt,:dueAt,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("type", type).addValue("subject", required(values, "subject", 240))
                        .addValue("body", optional(values.get("body"), 4000, "body"))
                        .addValue("relatedType", relatedType).addValue("relatedId", relatedId)
                        .addValue("ownerId", ownerId).addValue("priority", priority)
                        .addValue("startAt", startAt == null ? null : Timestamp.from(startAt.toInstant()))
                        .addValue("dueAt", dueAt == null ? null : Timestamp.from(dueAt.toInstant()))
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        if (relatedType != null && relatedId != null) {
            timeline(payload.tenantId(), relatedType, relatedId, "crm.activity.imported",
                    "Activity imported", "CRM_ACTIVITY", id, payload.actorId(), now);
        }
        return id;
    }

    @Transactional
    public Map<String, Object> createCustomField(
            Authentication authentication, CreateCustomFieldRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String entityType = importEntityType(request.entityType());
        String dataType = customType(request.dataType());
        boolean sensitive = Boolean.TRUE.equals(request.sensitive());
        boolean searchable = Boolean.TRUE.equals(request.searchable());
        if (sensitive && searchable) {
            throw bad("Sensitive CRM custom fields cannot be searchable");
        }
        try {
            jdbc.update(
                    "INSERT INTO crm_custom_field_definitions " +
                            "(id,tenant_id,entity_type,field_key,label_ar,label_en,data_type,sensitive,searchable," +
                            "required,active,created_at) " +
                            "VALUES (:id,:tenantId,:entityType,:fieldKey,:labelAr,:labelEn,:dataType,:sensitive," +
                            ":searchable,:required,TRUE,:now)",
                    p().addValue("id", id).addValue("tenantId", tenantId)
                            .addValue("entityType", entityType)
                            .addValue("fieldKey", request.fieldKey().trim())
                            .addValue("labelAr", request.labelAr().trim())
                            .addValue("labelEn", request.labelEn().trim())
                            .addValue("dataType", dataType).addValue("sensitive", sensitive)
                            .addValue("searchable", searchable)
                            .addValue("required", Boolean.TRUE.equals(request.required()))
                            .addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CRM custom field key already exists or violates its constraints");
        }
        return one("crm_custom_field_definitions", tenantId, id, "CRM custom field not found");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCustomFields(
            Authentication authentication, String entityType) {
        UUID tenantId = tenantId(authentication);
        if (entityType == null || entityType.isBlank()) {
            return jdbc.queryForList(
                    "SELECT * FROM crm_custom_field_definitions " +
                            "WHERE tenant_id=:tenantId AND active=TRUE ORDER BY entity_type,field_key",
                    p().addValue("tenantId", tenantId));
        }
        return activeCustomDefinitions(tenantId, importEntityType(entityType));
    }

    @Transactional
    public Map<String, Object> upsertCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            UUID entityId, UpdateCustomFieldValuesRequest request) {
        UUID tenantId = tenantId(authentication);
        String entityType = importEntityType(requestedEntityType);
        upsertCustomFieldValuesInternal(
                tenantId, userId(authentication), entityType, entityId, request.values(), true);
        return readCustomFieldValuesInternal(tenantId, entityType, entityId, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            UUID entityId, boolean includeSensitive) {
        UUID tenantId = tenantId(authentication);
        String entityType = importEntityType(requestedEntityType);
        assertEntityExists(tenantId, entityType, entityId);
        return readCustomFieldValuesInternal(tenantId, entityType, entityId, includeSensitive);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            String fieldKey, String query, int requestedLimit) {
        UUID tenantId = tenantId(authentication);
        String entityType = importEntityType(requestedEntityType);
        String normalized = normalizeSearch(query);
        if (normalized.isBlank()) throw bad("CRM custom-field search query is required");
        return jdbc.queryForList(
                "SELECT value.entity_id,definition.field_key,definition.data_type,value.searchable_value " +
                        "FROM crm_custom_field_values value " +
                        "JOIN crm_custom_field_definitions definition " +
                        "ON definition.tenant_id=value.tenant_id AND definition.id=value.definition_id " +
                        "WHERE value.tenant_id=:tenantId AND value.entity_type=:entityType " +
                        "AND definition.field_key=:fieldKey AND definition.active=TRUE " +
                        "AND definition.searchable=TRUE AND definition.sensitive=FALSE " +
                        "AND value.searchable_value LIKE :query " +
                        "ORDER BY value.updated_at DESC,value.entity_id LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("fieldKey", fieldKey.trim())
                        .addValue("query", "%" + normalized + "%")
                        .addValue("limit", limit(requestedLimit)));
    }

    private void upsertCustomFieldValuesInternal(
            UUID tenantId, UUID actorId, String entityType, UUID entityId,
            Map<String, ?> requestedValues, boolean enforceRequired) {
        assertEntityExists(tenantId, entityType, entityId);
        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Map<String, Object> definition : activeCustomDefinitions(tenantId, entityType)) {
            definitions.put(String.valueOf(definition.get("field_key")), definition);
        }
        for (Map.Entry<String, ?> entry : requestedValues.entrySet()) {
            Map<String, Object> definition = definitions.get(entry.getKey());
            if (definition == null) {
                throw bad("Unknown active CRM custom field: " + entry.getKey());
            }
            UUID definitionId = asUuid(definition.get("id"));
            Object raw = entry.getValue();
            if (raw == null || raw instanceof String text && text.isBlank()) {
                jdbc.update(
                        "DELETE FROM crm_custom_field_values " +
                                "WHERE tenant_id=:tenantId AND definition_id=:definitionId AND entity_id=:entityId",
                        p().addValue("tenantId", tenantId).addValue("definitionId", definitionId)
                                .addValue("entityId", entityId));
                continue;
            }
            CustomValue value = customValue(String.valueOf(definition.get("data_type")), raw);
            boolean sensitive = Boolean.TRUE.equals(definition.get("sensitive"));
            boolean searchable = Boolean.TRUE.equals(definition.get("searchable"));
            String textValue = value.text();
            BigDecimal numberValue = value.number();
            Boolean booleanValue = value.bool();
            Date dateValue = value.date();
            Timestamp timestampValue = value.timestamp();
            if (sensitive) {
                textValue = encryptSensitive(value.display());
                numberValue = null;
                booleanValue = null;
                dateValue = null;
                timestampValue = null;
            }
            String searchableValue =
                    searchable && !sensitive ? normalizeSearch(value.display()) : null;
            jdbc.update(
                    "DELETE FROM crm_custom_field_values " +
                            "WHERE tenant_id=:tenantId AND definition_id=:definitionId AND entity_id=:entityId",
                    p().addValue("tenantId", tenantId).addValue("definitionId", definitionId)
                            .addValue("entityId", entityId));
            Instant now = Instant.now();
            jdbc.update(
                    "INSERT INTO crm_custom_field_values " +
                            "(id,tenant_id,definition_id,entity_type,entity_id,value_text,value_number,value_boolean," +
                            "value_date,value_timestamp,searchable_value,created_by,updated_by,created_at,updated_at) " +
                            "VALUES (:id,:tenantId,:definitionId,:entityType,:entityId,:text,:number,:bool,:date," +
                            ":timestamp,:searchable,:actorId,:actorId,:now,:now)",
                    p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("definitionId", definitionId).addValue("entityType", entityType)
                            .addValue("entityId", entityId).addValue("text", textValue)
                            .addValue("number", numberValue).addValue("bool", booleanValue)
                            .addValue("date", dateValue).addValue("timestamp", timestampValue)
                            .addValue("searchable", searchableValue).addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
        }
        if (enforceRequired) assertRequiredCustomFields(tenantId, entityType, entityId);
    }

    private Map<String, Object> readCustomFieldValuesInternal(
            UUID tenantId, String entityType, UUID entityId, boolean includeSensitive) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT definition.field_key,definition.data_type,definition.sensitive,definition.required," +
                        "value.value_text,value.value_number,value.value_boolean,value.value_date,value.value_timestamp " +
                        "FROM crm_custom_field_definitions definition " +
                        "LEFT JOIN crm_custom_field_values value " +
                        "ON value.tenant_id=definition.tenant_id AND value.definition_id=definition.id " +
                        "AND value.entity_id=:entityId " +
                        "WHERE definition.tenant_id=:tenantId AND definition.entity_type=:entityType " +
                        "AND definition.active=TRUE ORDER BY definition.field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("entityId", entityId));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object value = firstNonNull(
                    row.get("value_text"), row.get("value_number"), row.get("value_boolean"),
                    row.get("value_date"), row.get("value_timestamp"));
            if (Boolean.TRUE.equals(row.get("sensitive")) && value != null) {
                value = includeSensitive ? decryptSensitive(String.valueOf(value)) : "[REDACTED]";
            }
            result.put(String.valueOf(row.get("field_key")), value);
        }
        return result;
    }

    private List<Map<String, Object>> activeCustomDefinitions(UUID tenantId, String entityType) {
        return jdbc.queryForList(
                "SELECT * FROM crm_custom_field_definitions " +
                        "WHERE tenant_id=:tenantId AND entity_type=:entityType AND active=TRUE ORDER BY field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType));
    }

    private boolean hasRequiredCustomFields(UUID tenantId, String entityType) {
        return scalarLong(
                "SELECT COUNT(*) FROM crm_custom_field_definitions " +
                        "WHERE tenant_id=:tenantId AND entity_type=:entityType AND active=TRUE AND required=TRUE",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)) > 0;
    }

    private void assertRequiredCustomFields(UUID tenantId, String entityType, UUID entityId) {
        List<String> missingFields = jdbc.queryForList(
                "SELECT definition.field_key FROM crm_custom_field_definitions definition " +
                        "LEFT JOIN crm_custom_field_values value " +
                        "ON value.tenant_id=definition.tenant_id AND value.definition_id=definition.id " +
                        "AND value.entity_id=:entityId " +
                        "WHERE definition.tenant_id=:tenantId AND definition.entity_type=:entityType " +
                        "AND definition.active=TRUE AND definition.required=TRUE AND value.id IS NULL " +
                        "ORDER BY definition.field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("entityId", entityId), String.class);
        if (!missingFields.isEmpty()) {
            throw bad("Missing required CRM custom fields: " + String.join(", ", missingFields));
        }
    }

    private void assertEntityExists(UUID tenantId, String entityType, UUID entityId) {
        String table = ENTITY_TABLES.get(entityType);
        if (table == null) throw bad("Unsupported CRM entityType");
        if (scalarLong(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=:tenantId AND id=:entityId",
                p().addValue("tenantId", tenantId).addValue("entityId", entityId)) != 1) {
            throw missing("CRM entity not found");
        }
    }

    public void validateOwner(UUID tenantId, UUID ownerId) {
        if (ownerId == null) return;
        if (scalarLong(
                "SELECT COUNT(*) FROM users WHERE tenant_id=:tenantId AND id=:ownerId AND status='ACTIVE'",
                p().addValue("tenantId", tenantId).addValue("ownerId", ownerId)) != 1) {
            throw bad("CRM owner must be an active user in the same tenant");
        }
    }

    public void validateRelated(UUID tenantId, String relatedType, UUID relatedId) {
        if (relatedType == null && relatedId == null) return;
        if (relatedType == null || relatedId == null) {
            throw bad("relatedType and relatedId must be supplied together");
        }
        String table = switch (relatedType.trim().toUpperCase(Locale.ROOT)) {
            case "ACCOUNT" -> "crm_accounts";
            case "CONTACT" -> "crm_contacts";
            case "LEAD" -> "crm_leads";
            case "OPPORTUNITY" -> "crm_opportunities";
            default -> throw bad("Unsupported CRM relatedType");
        };
        one(table, tenantId, relatedId, "Related CRM record not found");
    }

    private ParsedTable parseImportFile(String filename, String contentType, byte[] bytes) {
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".csv") || lowerContentType.contains("csv")) {
            return parseCsv(bytes);
        }
        if (lowerFilename.endsWith(".xlsx")
                || lowerContentType.contains("spreadsheetml")) {
            return parseXlsx(bytes);
        }
        throw bad("CRM import supports CSV and XLSX files only");
    }

    private ParsedTable parseCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return tableFromMatrix(csvRecords(text));
    }

    private List<List<String>> csvRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    value.append(current);
                }
            } else if (current == '"' && value.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(value.toString());
                value.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(value.toString());
                value.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                value.append(current);
            }
        }
        if (quoted) throw bad("CRM CSV contains an unterminated quoted field");
        if (value.length() > 0 || !row.isEmpty()) {
            row.add(value.toString());
            records.add(row);
        }
        return records;
    }

    private ParsedTable parseXlsx(byte[] bytes) {
        try {
            Map<String, byte[]> entries = readXlsxEntries(bytes);
            List<String> sharedStrings = xlsxSharedStrings(entries.get("xl/sharedStrings.xml"));
            String sheetName = entries.keySet().stream()
                    .filter(name -> name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> bad("XLSX does not contain a worksheet"));
            Document sheet = safeXml(entries.get(sheetName));
            NodeList rowNodes = sheet.getElementsByTagNameNS("*", "row");
            List<List<String>> matrix = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
                Element rowElement = (Element) rowNodes.item(rowIndex);
                NodeList cells = rowElement.getElementsByTagNameNS("*", "c");
                List<String> row = new ArrayList<>();
                for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                    Element cell = (Element) cells.item(cellIndex);
                    int column = xlsxColumn(cell.getAttribute("r"));
                    if (column >= MAX_IMPORT_COLUMNS) {
                        throw bad("XLSX exceeds the 100-column limit");
                    }
                    while (row.size() <= column) row.add("");
                    row.set(column, xlsxCellValue(cell, sharedStrings));
                }
                matrix.add(row);
                if (matrix.size() > MAX_IMPORT_ROWS + 1) {
                    throw bad("CRM import exceeds the 10000-row limit");
                }
            }
            return tableFromMatrix(matrix);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw bad("Invalid or unsupported XLSX file");
        }
    }

    private Map<String, byte[]> readXlsxEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || entries.size() >= 500) {
                    throw new IOException("Unsafe XLSX archive");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    expanded += read;
                    if (expanded > MAX_EXPANDED_XLSX_BYTES) {
                        throw new IOException("XLSX expanded size limit exceeded");
                    }
                    output.write(buffer, 0, read);
                }
                entries.put(name, output.toByteArray());
            }
        }
        return entries;
    }

    private Document safeXml(byte[] bytes) throws Exception {
        if (bytes == null) throw new IOException("Required XLSX XML is missing");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private List<String> xlsxSharedStrings(byte[] bytes) throws Exception {
        if (bytes == null) return List.of();
        NodeList items = safeXml(bytes).getElementsByTagNameNS("*", "si");
        List<String> values = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            NodeList texts = item.getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int textIndex = 0; textIndex < texts.getLength(); textIndex++) {
                value.append(texts.item(textIndex).getTextContent());
            }
            values.add(value.toString());
        }
        return values;
    }

    private int xlsxColumn(String reference) {
        int result = 0;
        int letters = 0;
        while (letters < reference.length() && Character.isLetter(reference.charAt(letters))) {
            result = result * 26 + Character.toUpperCase(reference.charAt(letters)) - 'A' + 1;
            letters++;
        }
        if (letters == 0) throw bad("Invalid XLSX cell reference");
        return result - 1;
    }

    private String xlsxCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        NodeList inlineText = cell.getElementsByTagNameNS("*", "t");
        if ("inlineStr".equals(type) && inlineText.getLength() > 0) {
            return inlineText.item(0).getTextContent();
        }
        NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) return "";
        String value = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int index;
            try {
                index = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw bad("Invalid XLSX shared string index");
            }
            if (index < 0 || index >= sharedStrings.size()) {
                throw bad("Invalid XLSX shared string index");
            }
            return sharedStrings.get(index);
        }
        if ("b".equals(type)) return "1".equals(value) ? "true" : "false";
        return value;
    }

    private ParsedTable tableFromMatrix(List<List<String>> matrix) {
        if (matrix.isEmpty()) throw bad("CRM import file is empty");
        List<String> headers = matrix.get(0).stream().map(String::trim).toList();
        if (headers.isEmpty() || headers.size() > MAX_IMPORT_COLUMNS
                || headers.stream().anyMatch(String::isBlank)
                || new LinkedHashSet<>(headers).size() != headers.size()) {
            throw bad("CRM import headers must be unique, non-empty, and limited to 100");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < matrix.size(); index++) {
            List<String> source = matrix.get(index);
            if (source.stream().allMatch(String::isBlank)) continue;
            if (source.size() > headers.size()) {
                throw bad("CRM import row has more columns than the header");
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column),
                        column < source.size() ? source.get(column).trim() : "");
            }
            rows.add(row);
            if (rows.size() > MAX_IMPORT_ROWS) {
                throw bad("CRM import exceeds the 10000-row limit");
            }
        }
        return new ParsedTable(headers, rows);
    }

    private Map<String, String> resolveMapping(
            UUID tenantId, String entityType, List<String> headers, String mappingJson) {
        Map<String, String> requested = new LinkedHashMap<>();
        if (mappingJson != null && !mappingJson.isBlank()) {
            if (mappingJson.length() > 50_000) throw bad("CRM import mapping is too large");
            try {
                requested.putAll(objectMapper.readValue(
                        mappingJson, new TypeReference<Map<String, String>>() { }));
            } catch (IOException exception) {
                throw bad("CRM import mapping must be a JSON object of source header to target field");
            }
        } else {
            for (String header : headers) {
                requested.put(header, resolveImportTarget(entityType, header));
            }
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Set<String> targets = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String source = entry.getKey().trim();
            if (!headers.contains(source)) throw bad("CRM import mapping references an unknown header: " + source);
            String target = resolveImportTarget(entityType, entry.getValue());
            if (!targets.add(target)) throw bad("CRM import mapping contains a duplicate target: " + target);
            if (target.startsWith("custom.")) {
                String key = target.substring("custom.".length());
                if (scalarLong(
                        "SELECT COUNT(*) FROM crm_custom_field_definitions " +
                                "WHERE tenant_id=:tenantId AND entity_type=:entityType AND field_key=:fieldKey AND active=TRUE",
                        p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                                .addValue("fieldKey", key)) != 1) {
                    throw bad("CRM import mapping references an unknown custom field: " + key);
                }
            }
            result.put(source, target);
        }
        for (String required : REQUIRED_IMPORT_FIELDS.get(entityType)) {
            if (!targets.contains(required)) {
                throw bad("CRM import mapping is missing required target: " + required);
            }
        }
        return result;
    }

    private String resolveImportTarget(String entityType, String requested) {
        if (requested == null || requested.isBlank()) throw bad("CRM import target cannot be blank");
        String trimmed = requested.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("custom.")) {
            String key = trimmed.substring(trimmed.indexOf('.') + 1).trim();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{1,119}")) {
                throw bad("Invalid CRM custom-field import target");
            }
            return "custom." + key;
        }
        String canonical = canonical(trimmed);
        for (String allowed : IMPORT_FIELDS.get(entityType)) {
            if (canonical(allowed).equals(canonical)) return allowed;
        }
        String alias = switch (entityType + ":" + canonical) {
            case "ACCOUNT:name", "LEAD:name" -> "displayName";
            case "CONTACT:name", "CONTACT:firstname" -> "givenName";
            case "CONTACT:lastname", "CONTACT:surname" -> "familyName";
            case "CONTACT:email" -> "primaryEmail";
            case "CONTACT:phone" -> "primaryPhone";
            case "OPPORTUNITY:opportunityname" -> "name";
            case "ACTIVITY:type" -> "activityType";
            default -> null;
        };
        if (alias == null) throw bad("Unsupported CRM import target: " + requested);
        return alias;
    }

    private Map<String, String> mappedValues(
            Map<String, String> sourceRow, Map<String, String> mapping) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            result.put(entry.getValue(), sourceRow.getOrDefault(entry.getKey(), ""));
        }
        return result;
    }

    private Map<String, Object> customImportValues(Map<String, String> values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("custom.")) {
                result.put(entry.getKey().substring("custom.".length()), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, String> readMapping(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("Stored CRM import mapping is invalid", exception);
        }
    }

    private Map<String, Object> getImportJobInternal(UUID tenantId, UUID jobId) {
        try {
            return jdbc.queryForMap(
                    "SELECT job.*,(SELECT COUNT(*) FROM crm_import_errors error " +
                            "WHERE error.tenant_id=job.tenant_id AND error.import_job_id=job.id) AS error_count " +
                            "FROM crm_import_jobs job WHERE job.tenant_id=:tenantId AND job.id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", jobId));
        } catch (EmptyResultDataAccessException exception) {
            throw missing("CRM import job not found");
        }
    }

    private CustomValue customValue(String requestedType, Object raw) {
        String dataType = customType(requestedType);
        String text = String.valueOf(raw).trim();
        try {
            return switch (dataType) {
                case "TEXT" -> new CustomValue(requireText(text, 4000), null, null, null, null, text);
                case "EMAIL" -> {
                    if (text.length() > 255 || !EMAIL.matcher(text).matches()) {
                        throw bad("Invalid CRM custom-field email");
                    }
                    yield new CustomValue(
                            text.toLowerCase(Locale.ROOT), null, null, null, null, text);
                }
                case "URL" -> {
                    java.net.URI uri = java.net.URI.create(text);
                    if (text.length() > 1000 || uri.getScheme() == null
                            || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                        throw bad("Invalid CRM custom-field URL");
                    }
                    yield new CustomValue(text, null, null, null, null, text);
                }
                case "NUMBER" -> {
                    BigDecimal number = new BigDecimal(text);
                    yield new CustomValue(null, number, null, null, null, number.toPlainString());
                }
                case "BOOLEAN" -> {
                    String normalized = text.toLowerCase(Locale.ROOT);
                    if (!Set.of("true", "false").contains(normalized)) {
                        throw bad("Invalid CRM custom-field boolean");
                    }
                    Boolean bool = Boolean.valueOf(normalized);
                    yield new CustomValue(null, null, bool, null, null, bool.toString());
                }
                case "DATE" -> {
                    LocalDate date = LocalDate.parse(text);
                    yield new CustomValue(null, null, null, Date.valueOf(date), null, date.toString());
                }
                case "DATETIME" -> {
                    Instant instant = OffsetDateTime.parse(text).toInstant();
                    yield new CustomValue(
                            null, null, null, null, Timestamp.from(instant), instant.toString());
                }
                default -> throw bad("Unsupported CRM custom-field dataType");
            };
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw bad("Invalid value for CRM custom-field type " + dataType);
        }
    }

    private SecretKeySpec decodeEncryptionKey(String configured) {
        if (configured == null || configured.isBlank()) return null;
        try {
            byte[] key = Base64.getDecoder().decode(configured.trim());
            if (!Set.of(16, 24, 32).contains(key.length)) {
                throw new IllegalArgumentException("key length");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "sanad.crm.custom-field-encryption-key must be base64 AES-128/192/256", exception);
        }
    }

    private String encryptSensitive(String plaintext) {
        if (customFieldKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CRM custom-field encryption key is not configured");
        }
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, customFieldKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return "enc:v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt CRM custom field", exception);
        }
    }

    private String decryptSensitive(String encoded) {
        if (customFieldKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CRM custom-field encryption key is not configured");
        }
        if (!encoded.startsWith("enc:v1:")) {
            throw new IllegalStateException("Unsupported CRM custom-field ciphertext");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring("enc:v1:".length()));
            if (payload.length < 13) throw new GeneralSecurityException("ciphertext too short");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, customFieldKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt CRM custom field", exception);
        }
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
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM " + table + " WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", id));
        } catch (EmptyResultDataAccessException exception) {
            throw missing(message);
        }
    }

    private long scalarLong(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private void timeline(
            UUID tenantId, String subjectType, UUID subjectId, String eventType,
            String summary, String sourceType, UUID sourceId, UUID actorId, Instant now) {
        jdbc.update(
                "INSERT INTO crm_timeline_events " +
                        "(id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) " +
                        "VALUES (:id,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("subjectType", subjectType).addValue("subjectId", subjectId)
                        .addValue("eventType", eventType).addValue("summary", summary)
                        .addValue("sourceType", sourceType).addValue("sourceId", sourceId)
                        .addValue("now", Timestamp.from(now)).addValue("actorId", actorId));
    }

    private byte[] importFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("CRM import file is required");
        if (file.getSize() > MAX_IMPORT_BYTES) throw bad("CRM import file exceeds 10 MB");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_IMPORT_BYTES) {
                throw bad("CRM import file size is invalid");
            }
            return bytes;
        } catch (IOException exception) {
            throw bad("Unable to read CRM import file");
        }
    }

    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "crm-import" : filename.trim();
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        return truncate(value, 255);
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize CRM data", exception);
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return switch (statusException.getStatusCode().value()) {
                case 400 -> "VALIDATION_ERROR";
                case 404 -> "REFERENCE_NOT_FOUND";
                case 409 -> "CONFLICT";
                default -> "ROW_REJECTED";
            };
        }
        if (exception instanceof DataIntegrityViolationException) return "DATABASE_CONSTRAINT";
        return "ROW_PROCESSING_ERROR";
    }

    private String errorMessage(RuntimeException exception) {
        String message;
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null) {
            message = statusException.getReason();
        } else {
            message = exception.getMessage();
        }
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return truncate(message.replaceAll("[\\r\\n\\t]+", " "), 1000);
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private boolean leaseExpired(Object value) {
        if (value == null) return true;
        Instant instant;
        if (value instanceof Timestamp timestamp) instant = timestamp.toInstant();
        else if (value instanceof OffsetDateTime offsetDateTime) instant = offsetDateTime.toInstant();
        else instant = Instant.parse(value.toString());
        return instant.isBefore(Instant.now());
    }

    private MapSqlParameterSource context(
            UUID tenantId, UUID actorId, UUID id, Instant now) {
        return p().addValue("tenantId", tenantId).addValue("actorId", actorId)
                .addValue("id", id).addValue("now", Timestamp.from(now));
    }

    private UUID tenantId(Authentication authentication) {
        return contextValue(authentication, "tenant_id");
    }

    private UUID userId(Authentication authentication) {
        return contextValue(authentication, "user_id");
    }

    private UUID contextValue(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }

    private String importEntityType(String value) {
        String normalized = upper(value);
        if (normalized == null || !ENTITY_TABLES.containsKey(normalized)) {
            throw bad("Unsupported CRM entityType");
        }
        return normalized;
    }

    private String customType(String value) {
        String normalized = upper(value);
        if (normalized == null || !CUSTOM_TYPES.contains(normalized)) {
            throw bad("Unsupported CRM custom-field dataType");
        }
        return normalized;
    }

    private String canonical(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String required(Map<String, String> values, String key, int max) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw bad(key + " is required");
        return requireText(value.trim(), max);
    }

    private String requireText(String value, int max) {
        if (value.isBlank() || value.length() > max) {
            throw bad("CRM field length is invalid");
        }
        return value;
    }

    private UUID uuid(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw bad(field + " must be a UUID");
        }
    }

    private BigDecimal decimal(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw bad(field + " must be numeric");
        }
    }

    private Integer integer(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw bad(field + " must be an integer");
        }
    }

    private LocalDate localDate(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw bad(field + " must use ISO date format");
        }
    }

    private OffsetDateTime offsetDateTime(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw bad(field + " must use ISO offset date-time format");
        }
    }

    private String currency(String value) {
        String currency = value(value, "SAR").trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw bad("currency must contain three letters");
        return currency;
    }

    private UUID asUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private static MapSqlParameterSource p() {
        return new MapSqlParameterSource();
    }

    private static int limit(int requested) {
        return Math.max(1, Math.min(requested, 200));
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw bad(field + " exceeds " + max);
        return result;
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ParsedTable(List<String> headers, List<Map<String, String>> rows) { }

    private record ImportPayload(
            UUID id, UUID tenantId, String entityType, UUID actorId,
            String filename, String contentType, String sha256,
            String mappingJson, int totalRows, long processedRows, byte[] content) { }

    private record CustomValue(
            String text, BigDecimal number, Boolean bool,
            Date date, Timestamp timestamp, String display) { }

    private static final class ImportLeaseLostException extends RuntimeException {
        private ImportLeaseLostException() {
            super("CRM import worker lease was lost");
        }
    }

    @Transactional
    public Map<String, Object> updateOpportunity(Authentication authentication, UUID opportunityId, java.math.BigDecimal amount, String name, UUID ownerUserId, long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        java.time.Instant now = java.time.Instant.now();
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", opportunityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("amount", amount)
                .addValue("name", name)
                .addValue("ownerUserId", ownerUserId);
        StringBuilder sql = new StringBuilder("UPDATE crm_opportunities SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (amount != null) { sql.append(", amount = :amount"); }
        if (name != null) { sql.append(", name = :name"); }
        if (ownerUserId != null) { sql.append(", owner_user_id = :ownerUserId"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new com.sanad.platform.crm.error.CrmContractException(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return one("crm_opportunities", tenantId, opportunityId, "CRM opportunity not found");
    }

    @Transactional
    public Map<String, Object> updateActivity(Authentication authentication, UUID activityId, String subject, String body, Integer priority, long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        java.time.Instant now = java.time.Instant.now();
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", activityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("subject", subject)
                .addValue("body", body)
                .addValue("priority", priority);
        StringBuilder sql = new StringBuilder("UPDATE crm_activities SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (subject != null) { sql.append(", subject = :subject"); }
        if (body != null) { sql.append(", body = :body"); }
        if (priority != null) { sql.append(", priority = :priority"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new com.sanad.platform.crm.error.CrmContractException(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return one("crm_activities", tenantId, activityId, "CRM activity not found");
    }

    @Transactional
    public Map<String, Object> updatePipeline(Authentication authentication, UUID pipelineId, String name, String currencyCode, long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        java.time.Instant now = java.time.Instant.now();
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", pipelineId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("name", name)
                .addValue("currencyCode", currencyCode);
        StringBuilder sql = new StringBuilder("UPDATE crm_pipelines SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (name != null) { sql.append(", name = :name"); }
        if (currencyCode != null) { sql.append(", currency_code = :currencyCode"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new com.sanad.platform.crm.error.CrmContractException(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return one("crm_pipelines", tenantId, pipelineId, "CRM pipeline not found");
    }

    @Transactional
    public Map<String, Object> updateCustomField(Authentication authentication, UUID customFieldId, String labelAr, String labelEn, Boolean required, Boolean searchable, Boolean sensitive, long expectedVersion) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        java.time.Instant now = java.time.Instant.now();
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", customFieldId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("labelAr", labelAr)
                .addValue("labelEn", labelEn)
                .addValue("required", required)
                .addValue("searchable", searchable)
                .addValue("sensitive", sensitive);
        StringBuilder sql = new StringBuilder("UPDATE crm_custom_field_definitions SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (labelAr != null) { sql.append(", label_ar = :labelAr"); }
        if (labelEn != null) { sql.append(", label_en = :labelEn"); }
        if (required != null) { sql.append(", required = :required"); }
        if (searchable != null) { sql.append(", searchable = :searchable"); }
        if (sensitive != null) { sql.append(", sensitive = :sensitive"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new com.sanad.platform.crm.error.CrmContractException(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return jdbc.queryForMap("SELECT * FROM crm_custom_field_definitions WHERE tenant_id = :tenantId AND id = :id",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", customFieldId));
    }
}
