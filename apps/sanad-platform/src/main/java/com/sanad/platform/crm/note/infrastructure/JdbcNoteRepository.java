package com.sanad.platform.crm.note.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.note.domain.NoteRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link NoteRepository}.
 * <p>
 * Tenant isolation at SQL level, optimistic locking on archive.
 * <p>
 * Branch: feature/crm-notes
 */
@Repository
public class JdbcNoteRepository implements NoteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNoteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public NoteRecord findById(UUID tenantId, UUID noteId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_notes WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", noteId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_NOTE_NOT_FOUND);
        }
    }

    @Override
    public List<NoteRecord> findAllBySubject(UUID tenantId, String subjectType, UUID subjectId, int limit, boolean includeArchived) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_notes WHERE tenant_id = :t AND subject_type = :st AND subject_id = :si");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("st", subjectType.toUpperCase())
                .addValue("si", subjectId);
        if (!includeArchived) {
            sql.append(" AND archived = FALSE");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    @Override
    public NoteRecord create(UUID tenantId, UUID actorId, CreateNoteCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UUID authorId = cmd.authorUserId() == null ? actorId : cmd.authorUserId();

        jdbc.update(
                "INSERT INTO crm_notes (" +
                "  id, tenant_id, version, subject_type, subject_id, body, " +
                "  author_user_id, archived, created_by, updated_by, created_at, updated_at" +
                ") VALUES (" +
                "  :id, :t, 0, :st, :si, :body, " +
                "  :authorId, FALSE, :actorId, :actorId, :now, :now" +
                ")",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("t", tenantId)
                        .addValue("st", cmd.subjectType().toUpperCase())
                        .addValue("si", cmd.subjectId())
                        .addValue("body", cmd.body())
                        .addValue("authorId", authorId)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public NoteRecord archive(UUID tenantId, UUID actorId, UUID noteId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_notes SET archived = TRUE, version = version + 1, " +
                "  updated_by = :actorId, updated_at = :now " +
                "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion AND archived = FALSE",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", noteId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            NoteRecord current = findById(tenantId, noteId);
            if (current.archived()) {
                throw new CrmContractException(CrmErrorCode.CRM_NOTE_ALREADY_ARCHIVED);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, noteId);
    }

    private NoteRecord mapRow(Map<String, Object> r) {
        return new NoteRecord(
                (UUID) r.get("id"),
                asLong(r.get("version")),
                (String) r.get("subject_type"),
                (UUID) r.get("subject_id"),
                (String) r.get("body"),
                (UUID) r.get("author_user_id"),
                r.get("archived") != null && ((Boolean) r.get("archived")),
                asInstant(r.get("created_at")),
                asInstant(r.get("updated_at")));
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
