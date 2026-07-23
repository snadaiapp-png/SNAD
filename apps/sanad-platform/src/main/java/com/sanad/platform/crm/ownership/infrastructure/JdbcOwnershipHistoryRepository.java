package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnershipHistory;
import com.sanad.platform.crm.ownership.domain.OwnershipHistoryRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.ownershipHistoryMapper;

/** Append-only repository — no UPDATE or DELETE path is exposed. */
@Repository
public class JdbcOwnershipHistoryRepository implements OwnershipHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnershipHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public OwnershipHistory append(OwnershipHistory history) {
        UUID id = history.id() != null ? history.id() : UUID.randomUUID();
        UUID correlationId = history.correlationId() != null
                ? history.correlationId() : UUID.randomUUID();
        Instant effectiveAt = history.effectiveAt() != null
                ? history.effectiveAt() : Instant.now();

        jdbc.update("""
                INSERT INTO crm_ownership_history
                  (id, tenant_id, record_type, record_id,
                   from_owner_type, from_owner_user_id, from_owner_team_id, from_owner_queue_id,
                   to_owner_type, to_owner_user_id, to_owner_team_id, to_owner_queue_id,
                   change_type, trigger_source, trigger_reference_id,
                   actor_user_id, reason, correlation_id, effective_at, recorded_at)
                VALUES
                  (:id, :tenantId, :recordType, :recordId,
                   :fromOwnerType, :fromOwnerUserId, :fromOwnerTeamId, :fromOwnerQueueId,
                   :toOwnerType, :toOwnerUserId, :toOwnerTeamId, :toOwnerQueueId,
                   :changeType, :triggerSource, :triggerReferenceId,
                   :actorUserId, :reason, :correlationId, :effectiveAt, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", history.tenantId())
                .addValue("recordType", history.recordType().name())
                .addValue("recordId", history.recordId())
                .addValue("fromOwnerType", history.fromOwnerType() != null
                        ? history.fromOwnerType().name() : null)
                .addValue("fromOwnerUserId", history.fromOwnerUserId())
                .addValue("fromOwnerTeamId", history.fromOwnerTeamId())
                .addValue("fromOwnerQueueId", history.fromOwnerQueueId())
                .addValue("toOwnerType", history.toOwnerType().name())
                .addValue("toOwnerUserId", history.toOwnerUserId())
                .addValue("toOwnerTeamId", history.toOwnerTeamId())
                .addValue("toOwnerQueueId", history.toOwnerQueueId())
                .addValue("changeType", history.changeType().name())
                .addValue("triggerSource", history.triggerSource().name())
                .addValue("triggerReferenceId", history.triggerReferenceId())
                .addValue("actorUserId", history.actorUserId())
                .addValue("reason", history.reason())
                .addValue("correlationId", correlationId)
                .addValue("effectiveAt", Timestamp.from(effectiveAt)));
        return findById(history.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<OwnershipHistory> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_ownership_history
                     WHERE tenant_id=:tenantId
                       AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), ownershipHistoryMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<OwnershipHistory> findByRecord(UUID tenantId,
                                                AssignmentRecordType recordType,
                                                UUID recordId,
                                                Instant before,
                                                int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder("""
                SELECT *
                  FROM crm_ownership_history
                 WHERE tenant_id=:tenantId
                   AND record_type=:recordType
                   AND record_id=:recordId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordType", recordType.name())
                .addValue("recordId", recordId)
                .addValue("limit", boundedLimit);
        if (before != null) {
            sql.append(" AND effective_at < :before");
            parameters.addValue("before", Timestamp.from(before));
        }
        sql.append(" ORDER BY effective_at DESC, recorded_at DESC, id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), parameters, ownershipHistoryMapper());
    }

    @Override
    public List<OwnershipHistory> findByCorrelation(UUID tenantId, UUID correlationId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_ownership_history
                 WHERE tenant_id=:tenantId
                   AND correlation_id=:correlationId
                 ORDER BY recorded_at, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("correlationId", correlationId), ownershipHistoryMapper());
    }

    @Override
    public long countByRecord(UUID tenantId,
                              AssignmentRecordType recordType,
                              UUID recordId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_ownership_history
                 WHERE tenant_id=:tenantId
                   AND record_type=:recordType
                   AND record_id=:recordId
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordType", recordType.name())
                .addValue("recordId", recordId), Long.class);
        return count != null ? count : 0L;
    }
}
