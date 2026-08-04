package com.sanad.platform.crm.cases.infrastructure;

import com.sanad.platform.crm.cases.domain.CaseRepository;
import com.sanad.platform.crm.cases.domain.CaseStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link CaseRepository}.
 * Tenant isolation is enforced in every query and optimistic concurrency is
 * enforced with {@code WHERE version = :expectedVersion} on mutations.
 */
@Repository
public class JdbcCaseRepository implements CaseRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCaseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CaseRecord findById(UUID tenantId, UUID caseId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_cases WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", caseId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_CASE_NOT_FOUND);
        }
    }

    @Override
    public List<CaseRecord> findAll(UUID tenantId, int limit, String status,
                                     UUID assigneeUserId, UUID customerId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_cases WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status.toUpperCase());
        }
        if (assigneeUserId != null) {
            sql.append(" AND assignee_user_id = :assigneeUserId");
            params.addValue("assigneeUserId", assigneeUserId);
        }
        if (customerId != null) {
            sql.append(" AND customer_id = :customerId");
            params.addValue("customerId", customerId);
        }
        sql.append(" ORDER BY priority DESC, updated_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    @Override
    public CaseRecord create(UUID tenantId, UUID actorId, CreateCaseCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update(
                "INSERT INTO crm_cases (" +
                        "id, tenant_id, version, subject, description, " +
                        "case_type, status, priority, customer_id, " +
                        "assignee_user_id, owner_user_id, related_id, due_at, " +
                        "created_at, updated_at" +
                        ") VALUES (" +
                        ":id, :t, 0, :subject, :description, " +
                        ":caseType, 'OPEN', :priority, :customerId, " +
                        ":assigneeUserId, :ownerUserId, :relatedId, :dueAt, " +
                        ":now, :now" +
                        ")",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("t", tenantId)
                        .addValue("subject", cmd.subject())
                        .addValue("description", cmd.description())
                        .addValue("caseType", cmd.caseType() == null ? null : cmd.caseType().toUpperCase())
                        .addValue("priority", cmd.priority())
                        .addValue("customerId", cmd.customerId())
                        .addValue("assigneeUserId", cmd.assigneeUserId())
                        .addValue("ownerUserId", actorId)
                        .addValue("relatedId", cmd.relatedId())
                        .addValue("dueAt", cmd.dueAt())
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public CaseRecord update(UUID tenantId, UUID actorId, UUID caseId,
                              UpdateCaseCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_cases SET version = version + 1, updated_at = :now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("id", caseId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("now", Timestamp.from(Instant.now()));

        if (cmd.subject() != null) {
            sql.append(", subject = :subject");
            params.addValue("subject", cmd.subject());
        }
        if (cmd.description() != null) {
            sql.append(", description = :description");
            params.addValue("description", cmd.description());
        }
        if (cmd.caseType() != null) {
            sql.append(", case_type = :caseType");
            params.addValue("caseType", cmd.caseType().toUpperCase());
        }
        if (cmd.priority() != null) {
            sql.append(", priority = :priority");
            params.addValue("priority", cmd.priority());
        }
        if (cmd.customerId() != null) {
            sql.append(", customer_id = :customerId");
            params.addValue("customerId", cmd.customerId());
        }
        if (cmd.dueAt() != null) {
            sql.append(", due_at = :dueAt");
            params.addValue("dueAt", cmd.dueAt());
        }
        sql.append(" WHERE tenant_id = :t AND id = :id AND version = :expectedVersion");

        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    @Override
    public CaseRecord start(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_cases SET status = 'IN_PROGRESS', version = version + 1, " +
                        "updated_at = :now " +
                        "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion AND status = 'OPEN'",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", caseId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            CaseRecord current = findById(tenantId, caseId);
            if (!CaseStatus.OPEN.equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_CASE_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    @Override
    public CaseRecord resolve(UUID tenantId, UUID actorId, UUID caseId,
                               String resolution, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_cases SET status = 'RESOLVED', resolved_at = :now, " +
                        "version = version + 1, updated_at = :now " +
                        "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion " +
                        "AND status IN ('OPEN', 'IN_PROGRESS')",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", caseId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            CaseRecord current = findById(tenantId, caseId);
            if (CaseStatus.RESOLVED.equals(current.status()) || CaseStatus.CLOSED.equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_CASE_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    @Override
    public CaseRecord close(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_cases SET status = 'CLOSED', closed_at = :now, " +
                        "version = version + 1, updated_at = :now " +
                        "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion AND status = 'RESOLVED'",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", caseId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            CaseRecord current = findById(tenantId, caseId);
            if (!CaseStatus.RESOLVED.equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_CASE_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    @Override
    public CaseRecord reopen(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_cases SET status = 'IN_PROGRESS', closed_at = NULL, " +
                        "version = version + 1, updated_at = :now " +
                        "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion AND status = 'CLOSED'",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", caseId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            CaseRecord current = findById(tenantId, caseId);
            if (!CaseStatus.CLOSED.equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_CASE_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    @Override
    public CaseRecord assign(UUID tenantId, UUID actorId, UUID caseId,
                              UUID assigneeUserId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_cases SET assignee_user_id = :assigneeUserId, " +
                        "version = version + 1, updated_at = :now " +
                        "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", caseId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("assigneeUserId", assigneeUserId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, caseId);
    }

    private CaseRecord mapRow(Map<String, Object> r) {
        return new CaseRecord(
                (UUID) r.get("id"),
                asLong(r.get("version")),
                (String) r.get("subject"),
                (String) r.get("description"),
                (String) r.get("case_type"),
                (String) r.get("status"),
                r.get("priority") == null ? 50 : ((Number) r.get("priority")).intValue(),
                (UUID) r.get("customer_id"),
                (UUID) r.get("assignee_user_id"),
                (UUID) r.get("owner_user_id"),
                (UUID) r.get("related_id"),
                asOffsetDateTime(r.get("due_at")),
                asOffsetDateTime(r.get("resolved_at")),
                asOffsetDateTime(r.get("closed_at")),
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
                throw new IllegalArgumentException("Unsupported CRM case timestamp value: " + value.getClass(), invalidTemporalValue);
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
            throw new IllegalArgumentException("Unsupported CRM case instant value: " + value.getClass(), invalidTemporalValue);
        }
    }
}
