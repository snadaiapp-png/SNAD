package com.sanad.platform.crm.tag.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.tag.domain.TagRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link TagRepository}.
 * <p>
 * Tenant isolation at SQL level. Tag definitions use optimistic locking;
 * assignments are idempotent via a UNIQUE constraint.
 * <p>
 * Branch: feature/crm-tags
 */
@Repository
public class JdbcTagRepository implements TagRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTagRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TagRecord findById(UUID tenantId, UUID tagId) {
        try {
            return mapTagRow(jdbc.queryForMap(
                    "SELECT * FROM crm_tags WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource().addValue("t", tenantId).addValue("id", tagId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
        }
    }

    @Override
    public List<TagRecord> findAll(UUID tenantId, int limit, String search) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_tags WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(name) LIKE :search");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }
        sql.append(" ORDER BY name ASC, id ASC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapTagRow).toList();
    }

    @Override
    public TagRecord create(UUID tenantId, UUID actorId, CreateTagCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_tags (id, tenant_id, version, name, color, created_by, updated_by, created_at, updated_at) "
                + "VALUES (:id, :t, 0, :name, :color, :actorId, :actorId, :now, :now)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("t", tenantId)
                        .addValue("name", cmd.name().trim())
                        .addValue("color", cmd.color())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public TagRecord update(UUID tenantId, UUID actorId, UUID tagId, UpdateTagCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_tags SET version = version + 1, updated_by = :actorId, updated_at = :now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("id", tagId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(Instant.now()));
        if (cmd.name() != null) {
            sql.append(", name = :name");
            params.addValue("name", cmd.name().trim());
        }
        if (cmd.color() != null) {
            sql.append(", color = :color");
            params.addValue("color", cmd.color());
        }
        sql.append(" WHERE tenant_id = :t AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, tagId);
    }

    @Override
    public void delete(UUID tenantId, UUID actorId, UUID tagId) {
        // Delete assignments first, then the tag
        jdbc.update("DELETE FROM crm_tag_assignments WHERE tenant_id = :t AND tag_id = :id",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", tagId));
        int deleted = jdbc.update("DELETE FROM crm_tags WHERE tenant_id = :t AND id = :id",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", tagId));
        if (deleted == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
        }
    }

    @Override
    public List<TagAssignmentRecord> findAssignmentsBySubject(UUID tenantId, String subjectType, UUID subjectId) {
        return jdbc.queryForList(
                "SELECT * FROM crm_tag_assignments WHERE tenant_id = :t AND subject_type = :st AND subject_id = :si "
                + "ORDER BY assigned_at DESC, id DESC",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("st", subjectType.toUpperCase())
                        .addValue("si", subjectId))
                .stream().map(this::mapAssignmentRow).toList();
    }

    @Override
    public List<TagAssignmentRecord> findAssignmentsByTag(UUID tenantId, UUID tagId, int limit) {
        return jdbc.queryForList(
                "SELECT * FROM crm_tag_assignments WHERE tenant_id = :t AND tag_id = :tagId "
                + "ORDER BY assigned_at DESC, id DESC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("tagId", tagId)
                        .addValue("limit", limit))
                .stream().map(this::mapAssignmentRow).toList();
    }

    @Override
    public TagAssignmentRecord assign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId) {
        // Verify tag exists (throws CRM_TAG_NOT_FOUND if not)
        findById(tenantId, tagId);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO crm_tag_assignments (id, tenant_id, tag_id, subject_type, subject_id, assigned_by, assigned_at) "
                    + "VALUES (:id, :t, :tagId, :st, :si, :actorId, :now)",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("t", tenantId)
                            .addValue("tagId", tagId)
                            .addValue("st", subjectType.toUpperCase())
                            .addValue("si", subjectId)
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Idempotent: assignment already exists — return the existing one
            return jdbc.queryForList(
                    "SELECT * FROM crm_tag_assignments WHERE tenant_id = :t AND tag_id = :tagId AND subject_type = :st AND subject_id = :si",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("tagId", tagId)
                            .addValue("st", subjectType.toUpperCase())
                            .addValue("si", subjectId))
                    .stream().map(this::mapAssignmentRow).findFirst()
                    .orElseThrow(() -> new CrmContractException(CrmErrorCode.INTERNAL_ERROR));
        }
        return mapAssignmentRow(jdbc.queryForMap(
                "SELECT * FROM crm_tag_assignments WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id)));
    }

    @Override
    public void unassign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId) {
        jdbc.update(
                "DELETE FROM crm_tag_assignments WHERE tenant_id = :t AND tag_id = :tagId AND subject_type = :st AND subject_id = :si",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("tagId", tagId)
                        .addValue("st", subjectType.toUpperCase())
                        .addValue("si", subjectId));
        // Idempotent: no error if assignment didn't exist
    }

    private TagRecord mapTagRow(Map<String, Object> r) {
        return new TagRecord(
                (UUID) r.get("id"),
                asLong(r.get("version")),
                (String) r.get("name"),
                (String) r.get("color"),
                asInstant(r.get("created_at")),
                asInstant(r.get("updated_at")));
    }

    private TagAssignmentRecord mapAssignmentRow(Map<String, Object> r) {
        return new TagAssignmentRecord(
                (UUID) r.get("id"),
                (UUID) r.get("tag_id"),
                (String) r.get("subject_type"),
                (UUID) r.get("subject_id"),
                (UUID) r.get("assigned_by"),
                asInstant(r.get("assigned_at")));
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }

    private static Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof Instant i) return i;
        return null;
    }
}
