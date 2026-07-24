package com.sanad.platform.crm.activity.infrastructure;

import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.error.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class JdbcActivityRepository implements ActivityRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcActivityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ActivityRecord findById(UUID t, UUID id) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_activities WHERE tenant_id=:t AND id=:id",
                    new MapSqlParameterSource().addValue("t", t).addValue("id", id)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_ACTIVITY_NOT_FOUND);
        }
    }

    public List<ActivityRecord> findAll(UUID t, int limit, String relatedType, UUID relatedId, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_activities WHERE tenant_id=:t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", t);
        if (relatedType != null) {
            sql.append(" AND related_type=:relatedType");
            params.addValue("relatedType", relatedType.toUpperCase());
        }
        if (relatedId != null) {
            sql.append(" AND related_id=:relatedId");
            params.addValue("relatedId", relatedId);
        }
        if (status != null) {
            sql.append(" AND status=:status");
            params.addValue("status", status.toUpperCase());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    public ActivityRecord create(UUID t, UUID actorId, CreateActivityCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_activities (id,tenant_id,version,activity_type,subject,body,related_type,related_id,owner_user_id,status,priority,start_at,due_at,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:t,0,:type,:subject,:body,:relatedType,:relatedId,:ownerUserId,'OPEN',:priority,:startAt,:dueAt,:actorId,:actorId,:now,:now)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("t", t)
                        .addValue("type", cmd.activityType().toUpperCase())
                        .addValue("subject", cmd.subject())
                        .addValue("body", cmd.body())
                        .addValue("relatedType", cmd.relatedType() == null ? null : cmd.relatedType().toUpperCase())
                        .addValue("relatedId", cmd.relatedId())
                        .addValue("ownerUserId", cmd.ownerUserId())
                        .addValue("priority", cmd.priority() == null ? 50 : cmd.priority())
                        .addValue("startAt", cmd.startAt())
                        .addValue("dueAt", cmd.dueAt())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(t, id);
    }

    public ActivityRecord update(UUID t, UUID actorId, UUID id, UpdateActivityCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_activities SET version=version+1,updated_by=:actorId,updated_at=:now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", t)
                .addValue("id", id)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(Instant.now()));
        if (cmd.subject() != null) {
            sql.append(",subject=:subject");
            params.addValue("subject", cmd.subject());
        }
        if (cmd.body() != null) {
            sql.append(",body=:body");
            params.addValue("body", cmd.body());
        }
        if (cmd.priority() != null) {
            sql.append(",priority=:priority");
            params.addValue("priority", cmd.priority());
        }
        if (cmd.startAt() != null) {
            sql.append(",start_at=:startAt");
            params.addValue("startAt", cmd.startAt());
        }
        if (cmd.dueAt() != null) {
            sql.append(",due_at=:dueAt");
            params.addValue("dueAt", cmd.dueAt());
        }
        sql.append(" WHERE tenant_id=:t AND id=:id AND version=:expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(t, id);
    }

    public ActivityRecord complete(UUID t, UUID actorId, UUID id, String result, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_activities SET status='COMPLETED',result=:result,completed_at=:now,version=version+1,updated_by=:actorId,updated_at=:now " +
                        "WHERE tenant_id=:t AND id=:id AND version=:expectedVersion",
                new MapSqlParameterSource()
                        .addValue("t", t)
                        .addValue("id", id)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("result", result)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(t, id);
    }

    private ActivityRecord mapRow(Map<String, Object> r) {
        return new ActivityRecord(
                (UUID) r.get("id"),
                asLong(r.get("version")),
                (String) r.get("activity_type"),
                (String) r.get("subject"),
                (String) r.get("body"),
                (String) r.get("related_type"),
                (UUID) r.get("related_id"),
                (UUID) r.get("owner_user_id"),
                (String) r.get("status"),
                r.get("priority") == null ? null : ((Number) r.get("priority")).intValue(),
                asOffsetDateTime(r.get("start_at")),
                asOffsetDateTime(r.get("due_at")),
                asOffsetDateTime(r.get("completed_at")),
                (String) r.get("result"),
                asInstant(r.get("created_at")),
                asInstant(r.get("updated_at")));
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * JDBC drivers are not consistent for TIMESTAMPTZ. PostgreSQL commonly
     * exposes it as Timestamp while H2 may expose OffsetDateTime directly.
     */
    private static OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (value instanceof LocalDateTime localDateTime) return localDateTime.atOffset(ZoneOffset.UTC);
        try {
            return OffsetDateTime.parse(String.valueOf(value));
        } catch (Exception ignored) {
            try {
                return Instant.parse(String.valueOf(value)).atOffset(ZoneOffset.UTC);
            } catch (Exception invalidTemporalValue) {
                throw new IllegalArgumentException("Unsupported CRM activity timestamp value: " + value.getClass(), invalidTemporalValue);
            }
        }
    }

    private static Instant asInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception invalidTemporalValue) {
            throw new IllegalArgumentException("Unsupported CRM activity instant value: " + value.getClass(), invalidTemporalValue);
        }
    }
}
