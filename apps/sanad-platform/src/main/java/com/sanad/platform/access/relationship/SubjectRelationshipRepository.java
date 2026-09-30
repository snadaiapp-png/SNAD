package com.sanad.platform.access.relationship;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tenant-jailed JDBC repository for the canonical subject_relationships table. */
@Repository
public class SubjectRelationshipRepository {

    private static final String ACTIVE_WINDOW =
            " AND (valid_from IS NULL OR valid_from <= NOW())"
                    + " AND (valid_until IS NULL OR valid_until > NOW())";

    private final JdbcTemplate jdbc;

    public SubjectRelationshipRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RelationshipRow insert(
            UUID tenantId, UUID subjectUserId, String relationshipType,
            String objectType, UUID objectId, Instant validFrom, Instant validUntil,
            String source, UUID createdBy) {
        scope(tenantId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO subject_relationships "
                        + "(id, tenant_id, subject_user_id, relationship_type, object_type, object_id, "
                        + "valid_from, valid_until, source, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, tenantId, subjectUserId, relationshipType, objectType, objectId,
                Timestamp.from(validFrom), validUntil == null ? null : Timestamp.from(validUntil),
                source, createdBy);
        return findById(tenantId, id);
    }

    public List<RelationshipRow> listForUser(UUID tenantId, UUID subjectUserId) {
        scope(tenantId);
        return jdbc.query("SELECT id, tenant_id, subject_user_id, relationship_type, object_type, "
                        + "object_id, valid_from, valid_until, source, created_by "
                        + "FROM subject_relationships WHERE tenant_id = ? AND subject_user_id = ?"
                        + ACTIVE_WINDOW + " ORDER BY created_at, id",
                (rs, rowNum) -> row(rs), tenantId, subjectUserId);
    }

    public boolean hasRelationship(
            UUID tenantId, UUID subjectUserId, String relationshipType,
            String objectType, UUID objectId) {
        scope(tenantId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM subject_relationships "
                        + "WHERE tenant_id = ? AND subject_user_id = ? AND relationship_type = ? "
                        + "AND object_type = ? AND object_id = ?" + ACTIVE_WINDOW,
                Integer.class, tenantId, subjectUserId, relationshipType, objectType, objectId);
        return count != null && count > 0;
    }

    public RelationshipRow findById(UUID tenantId, UUID id) {
        scope(tenantId);
        List<RelationshipRow> rows = jdbc.query("SELECT id, tenant_id, subject_user_id, relationship_type, "
                        + "object_type, object_id, valid_from, valid_until, source, created_by "
                        + "FROM subject_relationships WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> row(rs), tenantId, id);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("RELATIONSHIP_NOT_FOUND");
        }
        return rows.get(0);
    }

    public boolean revoke(UUID tenantId, UUID id) {
        scope(tenantId);
        return jdbc.update("UPDATE subject_relationships SET valid_until = CURRENT_TIMESTAMP, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND id = ? "
                        + "AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)",
                tenantId, id) == 1;
    }

    private void scope(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());
    }

    private static RelationshipRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp until = rs.getTimestamp("valid_until");
        return new RelationshipRow(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("subject_user_id", UUID.class), rs.getString("relationship_type"),
                rs.getString("object_type"), rs.getObject("object_id", UUID.class),
                rs.getTimestamp("valid_from").toInstant(), until == null ? null : until.toInstant(),
                rs.getString("source"), rs.getObject("created_by", UUID.class));
    }

    public record RelationshipRow(
            UUID id, UUID tenantId, UUID subjectUserId, String relationshipType,
            String objectType, UUID objectId, Instant validFrom, Instant validUntil,
            String source, UUID createdBy) {}
}
