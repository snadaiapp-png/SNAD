package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcQueueMembershipRepository implements QueueMembershipRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcQueueMembershipRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public QueueMembership save(QueueMembership m) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_queue_memberships
              (id, tenant_id, queue_id, user_id, status, added_at, removed_at, removed_reason,
               created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :queueId, :userId, :status, CURRENT_TIMESTAMP, :removedAt, :removedReason,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", m.tenantId()).addValue("queueId", m.queueId())
                .addValue("userId", m.userId()).addValue("status", m.status().name())
                .addValue("removedAt", m.removedAt()).addValue("removedReason", m.removedReason())
                .addValue("createdBy", m.createdBy()).addValue("updatedBy", m.updatedBy()));
        return findActive(m.tenantId(), m.queueId(), m.userId()).orElseThrow();
    }

    @Override
    public Optional<QueueMembership> findActive(UUID tenantId, UUID queueId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_queue_memberships WHERE tenant_id=:tenantId AND queue_id=:queueId AND user_id=:userId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("queueId", queueId).addValue("userId", userId),
                queueMembershipMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<QueueMembership> findActiveByQueue(UUID tenantId, UUID queueId) {
        return jdbc.query("SELECT * FROM crm_queue_memberships WHERE tenant_id=:tenantId AND queue_id=:queueId AND status='ACTIVE' ORDER BY added_at DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("queueId", queueId), queueMembershipMapper());
    }

    @Override
    public List<QueueMembership> findActiveByUser(UUID tenantId, UUID userId) {
        return jdbc.query("SELECT * FROM crm_queue_memberships WHERE tenant_id=:tenantId AND user_id=:userId AND status='ACTIVE' ORDER BY added_at DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), queueMembershipMapper());
    }

    @Override @Transactional
    public void remove(UUID tenantId, UUID membershipId, String reason, UUID updatedBy) {
        int rows = jdbc.update("UPDATE crm_queue_memberships SET status='REMOVED', removed_at=CURRENT_TIMESTAMP, removed_reason=:reason, updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", membershipId).addValue("reason", reason).addValue("updatedBy", updatedBy));
        if (rows == 0) throw new OwnershipDomainException("Queue membership not found or already removed: " + membershipId);
    }

    @Override
    public long countActiveByQueue(UUID tenantId, UUID queueId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM crm_queue_memberships WHERE tenant_id=:tenantId AND queue_id=:queueId AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("queueId", queueId), Long.class);
        return c != null ? c : 0L;
    }
}
