package com.sanad.platform.workflow.application;

import com.sanad.platform.storage.PlatformFileReferenceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * R1 GATE R1.15 — Workflow attachment references over the shared platform
 * file boundary. References only — no bytes, no blobs, no shadow storage.
 * Supports REQUIRED_ATTACHMENT: task completion is denied until required
 * evidence exists.
 */
@Service
public class WorkflowAttachmentService {

    private static final Set<String> CLASSES = Set.of(
            "IMAGE", "PDF", "DOCUMENT", "SPREADSHEET", "VIDEO", "OTHER_FILE");
    private static final Set<String> SCOPES = Set.of(
            "PROCESS", "STEP", "WORK_ITEM", "APPROVAL", "EXTERNAL_ACTION");

    /** Completion-blocking signal carrying the machine reason code. */
    public static class RequiredAttachmentMissingException extends IllegalStateException {
        public RequiredAttachmentMissingException(String scope, UUID scopeId, long missing) {
            super("WORKFLOW_REQUIRED_ATTACHMENT_MISSING: " + missing + " required attachment(s) missing for "
                    + scope + " " + scopeId);
        }
    }

    private final JdbcTemplate jdbc;
    private final PlatformFileReferenceService fileReferences;

    public WorkflowAttachmentService(JdbcTemplate jdbc, PlatformFileReferenceService fileReferences) {
        this.jdbc = jdbc;
        this.fileReferences = fileReferences;
    }

    @Transactional
    public UUID attach(UUID tenantId, String scope, UUID scopeId, UUID fileReferenceId,
                       String attachmentClass, boolean required, UUID uploadedBy) {
        if (tenantId == null || scope == null || scopeId == null) {
            throw new IllegalArgumentException("tenantId, scope and scopeId are required");
        }
        String sc = scope.trim().toUpperCase();
        String cl = attachmentClass == null ? "OTHER_FILE" : attachmentClass.trim().toUpperCase();
        if (!SCOPES.contains(sc)) throw new IllegalArgumentException("Invalid attachment scope: " + scope);
        if (!CLASSES.contains(cl)) throw new IllegalArgumentException("Invalid attachment class: " + attachmentClass);
        // INVALID_REFERENCE_DENIED: a reference from another tenant (or absent)
        // is invisible to require() and fails here, before the FK would.
        fileReferences.require(tenantId, fileReferenceId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_attachments (id, tenant_id, scope, scope_id, file_reference_id,
                    attachment_class, required_flag, uploaded_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, tenantId, sc, scopeId, fileReferenceId, cl, required, uploadedBy);
        return id;
    }

    @Transactional(readOnly = true)
    public List<UUID> listReferences(UUID tenantId, String scope, UUID scopeId) {
        return jdbc.queryForList("""
                SELECT file_reference_id FROM workflow_attachments
                 WHERE tenant_id = ? AND scope = ? AND scope_id = ?
                 ORDER BY created_at, id
                """, UUID.class, tenantId, scope, scopeId);
    }

    /**
     * REQUIRED_ATTACHMENT gate: throws {@link RequiredAttachmentMissingException}
     * when any required attachment for the scope is not satisfied.
     * Called by the work-item completion path (and reusable for approvals /
     * external actions in later scope).
     */
    @Transactional(readOnly = true)
    public void requireAttachmentsSatisfied(UUID tenantId, String scope, UUID scopeId) {
        Long required = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_attachments
                 WHERE tenant_id = ? AND scope = ? AND scope_id = ? AND required_flag = true
                """, Long.class, tenantId, scope, scopeId);
        Long present = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_attachments
                 WHERE tenant_id = ? AND scope = ? AND scope_id = ? AND required_flag = true
                   AND file_reference_id IS NOT NULL
                """, Long.class, tenantId, scope, scopeId);
        long req = required == null ? 0 : required;
        long got = present == null ? 0 : present;
        if (req > got) {
            throw new RequiredAttachmentMissingException(scope, scopeId, req - got);
        }
    }
}
