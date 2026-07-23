package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

/** Append-only repository — no UPDATE or DELETE path is ever exposed. */
@Repository
public class JdbcOwnershipHistoryRepository implements OwnershipHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcOwnershipHistoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public OwnershipHistory append(OwnershipHistory h) {
        UUID id = h.id() != null ? h.id() : UUID.randomUUID();
        UUID correlationId = h.correlationId() != null ? h.correlationId() : UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_ownership_history
              (id, tenant_id, record_type, record_id,
               from_owner_type, from_owner_user_id, from_owner_team_id, from_owner_queue_id,
               to_owner_type, to_owner_user_id, to_owner_team_id, to_owner_queue_id,
               change_type, trigger_source, trigger_reference_id, actor_user_id, reason, correlation_id,
               effective_at, recorded_at)
            VALUES
              (:id, :tenantId, :recordType, :recordId,
               :fromOwnerType, :fromOwnerUserId, :fromOwnerTeamId, :fromOwnerQueueId,
               :toOwnerType, :toOwnerUserId, :toOwnerTeamId, :toOwnerQueueId,
               :changeType, :triggerSource, :triggerReferenceId, :actorUserId, :reason, :correlationId,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", h.tenantId())
                .addValue("recordType", h.recordType().name()).addValue("recordId", h.recordId())
                .addValue("fromOwnerType", h.fromOwnerType() != null ? h.fromOwnerType().name() : null)
                .addValue("fromOwnerUserId", h.fromOwnerUserId()).addValue("fromOwnerTeamId", h.fromOwnerTeamId())
                .addValue("fromOwnerQueueId", h.fromOwnerQueueId())
                .addValue("toOwnerType", h.toOwnerType().name())
                .addValue("toOwnerUserId", h.toOwnerUserId()).addValue("toOwnerTeamId", h.toOwnerTeamId())
                .addValue("toOwnerQueueId", h.toOwnerQueueId())
                .addValue("changeType", h.changeType().name()).addValue("triggerSource", h.triggerSource().name())
                .addValue("triggerReferenceId", h.triggerReferenceId()).addValue("actorUserId", h.actorUserId())
                .addValue("reason", h.reason()).addValue("correlationId", correlationId));
        return findById(h.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<OwnershipHistory> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_ownership_history WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id), ownershipHistoryMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<OwnershipHistory> findByRecord(UUID tenantId, AssignmentRecordType recordType, UUID recordId, Instant before, int limit) {
        String sql = "SELECT * FROM crm_ownership_history WHERE tenant_id=:tenantId AND record_type=:recordType AND record_id=:recordId";
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("recordType", recordType.name()).addValue("recordId", recordId);
        if (before != null) {
            sql += " AND effective_at < :before";
            params.addValue("before", before);
        }
        sql += " ORDER BY effective_at DESC LIMIT :limit";
        params.addValue("limit", Math.min(limit, 500));
        return jdbc.query(sql, params, ownershipHistoryMapper());
    }

    @Override
    public List<OwnershipHistory> findByCorrelation(UUID tenantId, UUID correlationId) {
        return jdbc.query("SELECT * FROM crm_ownership_history WHERE tenant_id=:tenantId AND correlation_id=:correlationId ORDER BY recorded_at",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("correlationId", correlationId), ownershipHistoryMapper());
    }

    @Override
    public long countByRecord(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM crm_ownership_history WHERE tenant_id=:tenantId AND record_type=:recordType AND record_id=:recordId",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("recordType", recordType.name()).addValue("recordId", recordId), Long.class);
        return c != null ? c : 0L;
    }
}
