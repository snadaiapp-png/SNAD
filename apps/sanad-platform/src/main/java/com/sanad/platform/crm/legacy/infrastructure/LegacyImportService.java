package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.UpdateCustomFieldValuesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyImportService {

    private static final Logger log = LoggerFactory.getLogger(LegacyImportService.class);

    private final LegacySupport support;
    private final LegacyFileParserService fileParser;
    private final LegacyCustomFieldService customFieldService;
    private final boolean importWorkerEnabled;
    private final String workerId = UUID.randomUUID().toString();

    public LegacyImportService(
            LegacySupport support,
            LegacyFileParserService fileParser,
            LegacyCustomFieldService customFieldService,
            @Value("${sanad.crm.import-worker-enabled:true}") boolean importWorkerEnabled) {
        this.support = support;
        this.fileParser = fileParser;
        this.customFieldService = customFieldService;
        this.importWorkerEnabled = importWorkerEnabled;
    }

    @Transactional
    public Map<String, Object> uploadImport(
            Authentication authentication, String requestedEntityType,
            String mappingJson, MultipartFile file) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        String entityType = support.importEntityType(requestedEntityType);
        byte[] content = support.importFileBytes(file);
        ParsedTable table = fileParser.parseImportFile(file.getOriginalFilename(), file.getContentType(), content);
        Map<String, String> mapping = fileParser.resolveMapping(tenantId, entityType, table.headers(), mappingJson);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String canonicalMapping = support.writeJson(mapping);
        support.jdbc.update(
                "INSERT INTO crm_import_jobs " +
                        "(id,tenant_id,entity_type,status,total_rows,processed_rows,succeeded_rows,failed_rows," +
                        "requested_by,created_at,updated_at,original_filename,content_type,file_size_bytes,file_sha256,mapping_json) " +
                        "VALUES (:id,:tenantId,:entityType,'READY',:totalRows,0,0,0,:actorId,:now,:now," +
                        ":filename,:contentType,:fileSize,:sha256,:mappingJson)",
                p().addValue("id", id).addValue("tenantId", tenantId)
                        .addValue("entityType", entityType).addValue("totalRows", table.rows().size())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now))
                        .addValue("filename", support.safeFilename(file.getOriginalFilename()))
                        .addValue("contentType", optional(file.getContentType(), 120, "contentType"))
                        .addValue("fileSize", content.length).addValue("sha256", support.sha256(content))
                        .addValue("mappingJson", canonicalMapping));
        support.jdbc.update(
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
        return support.jdbc.queryForList(
                "SELECT job.*,(SELECT COUNT(*) FROM crm_import_errors error " +
                        "WHERE error.tenant_id=job.tenant_id AND error.import_job_id=job.id) AS error_count " +
                        "FROM crm_import_jobs job WHERE job.tenant_id=:tenantId " +
                        "ORDER BY job.created_at DESC,job.id LIMIT :limit",
                p().addValue("tenantId", support.tenantId(authentication))
                        .addValue("limit", limit(requestedLimit)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getImportJob(Authentication authentication, UUID jobId) {
        return getImportJobInternal(support.tenantId(authentication), jobId);
    }

    @Transactional
    public Map<String, Object> runImport(Authentication authentication, UUID jobId) {
        UUID tenantId = support.tenantId(authentication);
        Map<String, Object> job = support.one("crm_import_jobs", tenantId, jobId, "CRM import job not found");
        String status = String.valueOf(job.get("status"));
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw conflict("CRM import job cannot be queued from status " + status);
        }
        if ("RUNNING".equals(status) && !support.leaseExpired(job.get("lease_expires_at"))) {
            return getImportJobInternal(tenantId, jobId);
        }
        int changed = support.jdbc.update(
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
        UUID tenantId = support.tenantId(authentication);
        int changed = support.jdbc.update(
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
        UUID tenantId = support.tenantId(authentication);
        support.one("crm_import_jobs", tenantId, jobId, "CRM import job not found");
        return support.jdbc.queryForList(
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

    public void processNextImport() {
        if (!importWorkerEnabled) return;
        try {
            processNextImportNow();
        } catch (RuntimeException exception) {
            log.error("CRM import worker iteration failed", exception);
        }
    }

    public boolean processNextImportNow() {
        UUID jobId = support.transaction.execute(status -> claimNextImport());
        if (jobId == null) return false;
        try {
            processClaimedImport(jobId);
        } catch (RuntimeException exception) {
            markImportFailed(jobId, exception);
        }
        return true;
    }

    // ── Private import processing helpers ──────────────────────────────────

    private UUID claimNextImport() {
        List<UUID> candidates = support.jdbc.queryForList(
                "SELECT id FROM crm_import_jobs " +
                        "WHERE status='READY' OR (status='RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at<CURRENT_TIMESTAMP)) " +
                        "ORDER BY created_at,id LIMIT 1",
                p(), UUID.class);
        if (candidates.isEmpty()) return null;
        UUID jobId = candidates.get(0);
        Instant now = Instant.now();
        int changed = support.jdbc.update(
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
        ImportPayload payload = support.transaction.execute(status -> loadImportPayload(jobId));
        if (payload == null) throw new IllegalStateException("CRM import payload not found");
        if (!support.sha256(payload.content()).equals(payload.sha256())) {
            throw new IllegalStateException("CRM import file checksum mismatch");
        }
        ParsedTable table = fileParser.parseImportFile(payload.filename(), payload.contentType(), payload.content());
        if (table.rows().size() != payload.totalRows()) {
            throw new IllegalStateException("CRM import row count changed after upload");
        }
        Map<String, String> mapping = support.readMapping(payload.mappingJson());
        long processed = payload.processedRows();
        for (int index = Math.toIntExact(processed); index < table.rows().size(); index++) {
            Map<String, String> sourceRow = table.rows().get(index);
            long rowNumber = index + 2L;
            try {
                support.requiresNew.executeWithoutResult(status ->
                        processImportRow(payload, sourceRow, mapping, rowNumber));
            } catch (ImportLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                support.requiresNew.executeWithoutResult(status ->
                        recordImportError(payload, sourceRow, rowNumber, exception));
            }
        }
        support.transaction.executeWithoutResult(status -> completeImport(jobId));
    }

    private ImportPayload loadImportPayload(UUID jobId) {
        try {
            Map<String, Object> row = support.jdbc.queryForMap(
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
        Map<String, String> values = fileParser.mappedValues(sourceRow, mapping);
        UUID entityId = switch (payload.entityType()) {
            case "ACCOUNT" -> importAccount(payload, values);
            case "CONTACT" -> importContact(payload, values);
            case "LEAD" -> importLead(payload, values);
            case "OPPORTUNITY" -> importOpportunity(payload, values);
            case "ACTIVITY" -> importActivity(payload, values);
            default -> throw bad("Unsupported CRM import entityType");
        };
        Map<String, Object> customValues = fileParser.customImportValues(values);
        if (!customValues.isEmpty() || customFieldService.hasRequiredCustomFields(payload.tenantId(), payload.entityType())) {
            customFieldService.upsertCustomFieldValuesInternal(
                    payload.tenantId(), payload.actorId(), payload.entityType(),
                    entityId, customValues, true);
        }
        Instant now = Instant.now();
        int changed = support.jdbc.update(
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
        support.jdbc.update(
                "INSERT INTO crm_import_errors " +
                        "(id,tenant_id,import_job_id,row_number,field_name,error_code,message,raw_row,created_at) " +
                        "VALUES (:id,:tenantId,:jobId,:rowNumber,:fieldName,:errorCode,:message,:rawRow,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", payload.tenantId())
                        .addValue("jobId", payload.id()).addValue("rowNumber", rowNumber)
                        .addValue("fieldName", null).addValue("errorCode", support.errorCode(exception))
                        .addValue("message", support.errorMessage(exception))
                        .addValue("rawRow", truncate(support.writeJson(sourceRow), 8000))
                        .addValue("now", Timestamp.from(now)));
        int changed = support.jdbc.update(
                "UPDATE crm_import_jobs SET processed_rows=processed_rows+1," +
                        "failed_rows=failed_rows+1,last_error=:message,updated_at=:now,lease_expires_at=:lease " +
                        "WHERE id=:id AND status='RUNNING' AND worker_id=:workerId",
                p().addValue("id", payload.id()).addValue("workerId", workerId)
                        .addValue("message", support.errorMessage(exception))
                        .addValue("now", Timestamp.from(now))
                        .addValue("lease", Timestamp.from(now.plus(IMPORT_LEASE))));
        if (changed != 1) throw new ImportLeaseLostException();
    }

    private void completeImport(UUID jobId) {
        Instant now = Instant.now();
        int changed = support.jdbc.update(
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
            support.transaction.executeWithoutResult(status -> support.jdbc.update(
                    "UPDATE crm_import_jobs SET status='FAILED',last_error=:message,completed_at=:now," +
                            "updated_at=:now,worker_id=NULL,lease_expires_at=NULL " +
                            "WHERE id=:id AND worker_id=:workerId",
                    p().addValue("id", jobId).addValue("workerId", workerId)
                            .addValue("message", support.errorMessage(exception))
                            .addValue("now", Timestamp.from(Instant.now()))));
        } catch (RuntimeException persistenceFailure) {
            log.error("Unable to persist CRM import failure for job {}", jobId, persistenceFailure);
        }
    }

    private void assertImportLease(UUID jobId) {
        if (support.scalarLong(
                "SELECT COUNT(*) FROM crm_import_jobs WHERE id=:id AND status='RUNNING' " +
                        "AND worker_id=:workerId AND lease_expires_at>CURRENT_TIMESTAMP",
                p().addValue("id", jobId).addValue("workerId", workerId)) != 1) {
            throw new ImportLeaseLostException();
        }
    }

    // ── Entity importers ───────────────────────────────────────────────────

    private UUID importAccount(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = support.required(values, "displayName", 240);
        String accountType = value(values.get("accountType"), "BUSINESS").toUpperCase(Locale.ROOT);
        if (!Set.of("BUSINESS", "PERSON", "PARTNER", "PROSPECT", "OTHER").contains(accountType)) {
            throw bad("Invalid accountType");
        }
        UUID ownerId = support.uuid(values.get("ownerUserId"), "ownerUserId", false);
        UUID parentId = support.uuid(values.get("parentAccountId"), "parentAccountId", false);
        support.validateOwner(payload.tenantId(), ownerId);
        if (parentId != null) {
            support.one("crm_accounts", payload.tenantId(), parentId, "Parent CRM account not found");
        }
        support.jdbc.update(
                "INSERT INTO crm_accounts " +
                        "(id,tenant_id,display_name,normalized_name,account_type,lifecycle_status,parent_account_id," +
                        "owner_user_id,primary_currency_code,preferred_locale,time_zone,source,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:displayName,:normalizedName,:accountType,'ACTIVE',:parentId," +
                        ":ownerId,:currency,:locale,:timeZone,:source,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("displayName", displayName).addValue("normalizedName", normalize(displayName))
                        .addValue("accountType", accountType).addValue("parentId", parentId)
                        .addValue("ownerId", ownerId)
                        .addValue("currency", support.currency(values.get("primaryCurrencyCode")))
                        .addValue("locale", value(values.get("preferredLocale"), "ar-SA"))
                        .addValue("timeZone", value(values.get("timeZone"), "Asia/Riyadh"))
                        .addValue("source", optional(values.get("source"), 80, "source"))
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        support.timeline(payload.tenantId(), "ACCOUNT", id, "crm.account.imported",
                "Account imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        return id;
    }

    private UUID importContact(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String given = support.required(values, "givenName", 120);
        String family = optional(values.get("familyName"), 120, "familyName");
        String display = family == null ? given : given + " " + family;
        UUID accountId = support.uuid(values.get("accountId"), "accountId", false);
        UUID ownerId = support.uuid(values.get("ownerUserId"), "ownerUserId", false);
        if (accountId != null) {
            support.one("crm_accounts", payload.tenantId(), accountId, "CRM account not found");
        }
        support.validateOwner(payload.tenantId(), ownerId);
        String email = optional(values.get("primaryEmail"), 255, "primaryEmail");
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Invalid primaryEmail");
        String consent = value(values.get("consentSummary"), "UNKNOWN").toUpperCase(Locale.ROOT);
        if (!Set.of("UNKNOWN", "GRANTED", "DENIED", "WITHDRAWN").contains(consent)) {
            throw bad("Invalid consentSummary");
        }
        support.jdbc.update(
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
        support.timeline(payload.tenantId(), "CONTACT", id, "crm.contact.imported",
                "Contact imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        if (accountId != null) {
            support.timeline(payload.tenantId(), "ACCOUNT", accountId, "crm.contact.linked",
                    "Imported contact linked", "CRM_CONTACT", id, payload.actorId(), now);
        }
        return id;
    }

    private UUID importLead(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = support.required(values, "displayName", 240);
        UUID ownerId = support.uuid(values.get("ownerUserId"), "ownerUserId", false);
        UUID queueId = support.uuid(values.get("queueId"), "queueId", false);
        support.validateOwner(payload.tenantId(), ownerId);
        String email = optional(values.get("email"), 255, "email");
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Invalid email");
        BigDecimal score = support.decimal(values.get("score"), "score", false);
        if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw bad("score must be between 0 and 100");
        }
        support.jdbc.update(
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
        support.timeline(payload.tenantId(), "LEAD", id, "crm.lead.imported",
                "Lead imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        return id;
    }

    private UUID importOpportunity(ImportPayload payload, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UUID accountId = support.uuid(values.get("accountId"), "accountId", true);
        UUID contactId = support.uuid(values.get("contactId"), "contactId", false);
        UUID pipelineId = support.uuid(values.get("pipelineId"), "pipelineId", true);
        UUID stageId = support.uuid(values.get("stageId"), "stageId", true);
        UUID ownerId = support.uuid(values.get("ownerUserId"), "ownerUserId", false);
        support.one("crm_accounts", payload.tenantId(), accountId, "CRM account not found");
        if (contactId != null) {
            support.one("crm_contacts", payload.tenantId(), contactId, "CRM contact not found");
        }
        support.validateOwner(payload.tenantId(), ownerId);
        Map<String, Object> stage;
        try {
            stage = support.jdbc.queryForMap(
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
        BigDecimal amount = support.decimal(values.get("amount"), "amount", false);
        if (amount != null && amount.signum() < 0) throw bad("amount cannot be negative");
        String currencyCode = support.currency(support.required(values, "currencyCode", 3));
        LocalDate closeDate = support.localDate(values.get("expectedCloseDate"), "expectedCloseDate", false);
        support.jdbc.update(
                "INSERT INTO crm_opportunities " +
                        "(id,tenant_id,account_id,contact_id,pipeline_id,stage_id,name,amount,currency_code,probability," +
                        "status,expected_close_date,owner_user_id,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:accountId,:contactId,:pipelineId,:stageId,:name,:amount,:currency," +
                        ":probability,:status,:closeDate,:ownerId,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("accountId", accountId).addValue("contactId", contactId)
                        .addValue("pipelineId", pipelineId).addValue("stageId", stageId)
                        .addValue("name", support.required(values, "name", 240)).addValue("amount", amount)
                        .addValue("currency", currencyCode).addValue("probability", probability)
                        .addValue("status", status).addValue("closeDate", closeDate)
                        .addValue("ownerId", ownerId).addValue("actorId", payload.actorId())
                        .addValue("now", Timestamp.from(now)));
        support.jdbc.update(
                "INSERT INTO crm_opportunity_stage_history " +
                        "(id,tenant_id,opportunity_id,from_stage_id,to_stage_id,changed_by,changed_at,reason) " +
                        "VALUES (:id,:tenantId,:opportunityId,NULL,:stageId,:actorId,:now,'Imported')",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", payload.tenantId())
                        .addValue("opportunityId", id).addValue("stageId", stageId)
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        support.timeline(payload.tenantId(), "OPPORTUNITY", id, "crm.opportunity.imported",
                "Opportunity imported", "CRM_IMPORT", payload.id(), payload.actorId(), now);
        support.timeline(payload.tenantId(), "ACCOUNT", accountId, "crm.opportunity.created",
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
        UUID relatedId = support.uuid(values.get("relatedId"), "relatedId", false);
        support.validateRelated(payload.tenantId(), relatedType, relatedId);
        UUID ownerId = support.uuid(values.get("ownerUserId"), "ownerUserId", false);
        support.validateOwner(payload.tenantId(), ownerId);
        Integer priority = support.integer(values.get("priority"), "priority", false);
        if (priority == null) priority = 50;
        if (priority < 0 || priority > 100) throw bad("priority must be between 0 and 100");
        OffsetDateTime startAt = support.offsetDateTime(values.get("startAt"), "startAt", false);
        OffsetDateTime dueAt = support.offsetDateTime(values.get("dueAt"), "dueAt", false);
        support.jdbc.update(
                "INSERT INTO crm_activities " +
                        "(id,tenant_id,activity_type,subject,body,related_type,related_id,owner_user_id,status,priority," +
                        "start_at,due_at,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:type,:subject,:body,:relatedType,:relatedId,:ownerId,'OPEN',:priority," +
                        ":startAt,:dueAt,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", payload.tenantId())
                        .addValue("type", type).addValue("subject", support.required(values, "subject", 240))
                        .addValue("body", optional(values.get("body"), 4000, "body"))
                        .addValue("relatedType", relatedType).addValue("relatedId", relatedId)
                        .addValue("ownerId", ownerId).addValue("priority", priority)
                        .addValue("startAt", startAt == null ? null : Timestamp.from(startAt.toInstant()))
                        .addValue("dueAt", dueAt == null ? null : Timestamp.from(dueAt.toInstant()))
                        .addValue("actorId", payload.actorId()).addValue("now", Timestamp.from(now)));
        if (relatedType != null && relatedId != null) {
            support.timeline(payload.tenantId(), relatedType, relatedId, "crm.activity.imported",
                    "Activity imported", "CRM_ACTIVITY", id, payload.actorId(), now);
        }
        return id;
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private Map<String, Object> getImportJobInternal(UUID tenantId, UUID jobId) {
        try {
            return support.jdbc.queryForMap(
                    "SELECT job.*,(SELECT COUNT(*) FROM crm_import_errors error " +
                            "WHERE error.tenant_id=job.tenant_id AND error.import_job_id=job.id) AS error_count " +
                            "FROM crm_import_jobs job WHERE job.tenant_id=:tenantId AND job.id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", jobId));
        } catch (EmptyResultDataAccessException exception) {
            throw missing("CRM import job not found");
        }
    }
}
