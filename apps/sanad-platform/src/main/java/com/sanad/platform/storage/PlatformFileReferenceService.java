package com.sanad.platform.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * R1 GATE R1.14 — minimum platform-shared file REFERENCE boundary.
 *
 * <p>Forensic finding (R1.1): SNAD has NO canonical platform file/media/object
 * storage service. R1 therefore builds the smallest SHARED boundary — a
 * tenant-scoped file-reference registry any module can use — NOT a
 * Workflow-specific binary silo. Storage bytes stay outside the platform
 * database; {@code storage_reference} is an opaque platform-storage pointer.
 * Exact infrastructure blocker for real byte storage: no object storage /
 * file service exists in the deployment (reported in the R1 closure,
 * FOUNDATION_ONLY scope — contract/mocked storage proof per R1.33).</p>
 *
 * <p>Workflow relational tables never hold file bytes — only references into
 * this registry.</p>
 */
@Service
public class PlatformFileReferenceService {

    private final JdbcTemplate jdbc;

    public PlatformFileReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FileReference(UUID id, UUID tenantId, String sourceModule, String mimeType,
                                Long sizeBytes, String checksumSha256, String classification,
                                String storageReference) {}

    @Transactional
    public FileReference register(UUID tenantId, String sourceModule, String sourceEntityType,
                                  UUID sourceEntityId, String mimeType, Long sizeBytes,
                                  String checksumSha256, String classification,
                                  String storageReference, UUID uploadedBy) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (sourceModule == null || sourceModule.isBlank())
            throw new IllegalArgumentException("sourceModule is required");
        if (storageReference == null || storageReference.isBlank())
            throw new IllegalArgumentException("storageReference is required");
        String cls = classification == null || classification.isBlank() ? "INTERNAL" : classification;
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO platform_files (id, tenant_id, source_module, source_entity_type,
                    source_entity_id, mime_type, size_bytes, checksum_sha256, classification,
                    storage_reference, uploaded_by, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, tenantId, sourceModule.trim().toUpperCase(), sourceEntityType, sourceEntityId,
                mimeType, sizeBytes, checksumSha256, cls, storageReference, uploadedBy,
                Timestamp.from(Instant.now()));
        return new FileReference(id, tenantId, sourceModule, mimeType, sizeBytes,
                checksumSha256, cls, storageReference);
    }

    /** Tenant-scoped read (fail closed: other tenants' references are invisible). */
    @Transactional(readOnly = true)
    public FileReference require(UUID tenantId, UUID fileReferenceId) {
        return jdbc.query("""
                SELECT id, tenant_id, source_module, mime_type, size_bytes, checksum_sha256,
                       classification, storage_reference
                  FROM platform_files
                 WHERE tenant_id = ? AND id = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException(
                    "File reference not found for this tenant: " + fileReferenceId);
            return new FileReference(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getString("source_module"), rs.getString("mime_type"),
                    rs.getObject("size_bytes", Long.class), rs.getString("checksum_sha256"),
                    rs.getString("classification"), rs.getString("storage_reference"));
        }, tenantId, fileReferenceId);
    }
}
