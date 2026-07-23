package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.ActiveMembershipExistsException;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.QueueMembershipRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.queueMembershipMapper;

@Repository
public class JdbcQueueMembershipRepository implements QueueMembershipRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcQueueMembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public QueueMembership save(QueueMembership membership) {
        UUID id = membership.id() != null ? membership.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_queue_memberships
                      (id, tenant_id, queue_id, user_id, status,
                       added_at, removed_at, removed_reason,
                       created_at, updated_at, created_by, updated_by)
                    VALUES
                      (:id, :tenantId, :queueId, :userId, :status,
                       COALESCE(:addedAt, CURRENT_TIMESTAMP), :removedAt, :removedReason,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", membership.tenantId())
                    .addValue("queueId", membership.queueId())
                    .addValue("userId", membership.userId())
                    .addValue("status", membership.status().name())
                    .addValue("addedAt", membership.addedAt() != null
                            ? Timestamp.from(membership.addedAt()) : null)
                    .addValue("removedAt", membership.removedAt() != null
                            ? Timestamp.from(membership.removedAt()) : null)
                    .addValue("removedReason", membership.removedReason())
                    .addValue("createdBy", membership.createdBy())
                    .addValue("updatedBy", membership.updatedBy()));
        } catch (DuplicateKeyException conflict) {
            throw new ActiveMembershipExistsException(
                    membership.tenantId(), membership.queueId(), membership.userId());
        }
        return findActive(membership.tenantId(), membership.queueId(), membership.userId())
                .orElseThrow();
    }

    @Override
    public Optional<QueueMembership> findActive(UUID tenantId, UUID queueId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_queue_memberships
                     WHERE tenant_id=:tenantId
                       AND queue_id=:queueId
                       AND user_id=:userId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("queueId", queueId)
                    .addValue("userId", userId), queueMembershipMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<QueueMembership> findActiveByQueue(UUID tenantId, UUID queueId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_queue_memberships
                 WHERE tenant_id=:tenantId
                   AND queue_id=:queueId
                   AND status='ACTIVE'
                 ORDER BY added_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("queueId", queueId), queueMembershipMapper());
    }

    @Override
    public List<QueueMembership> findActiveByUser(UUID tenantId, UUID userId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_queue_memberships
                 WHERE tenant_id=:tenantId
                   AND user_id=:userId
                   AND status='ACTIVE'
                 ORDER BY added_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), queueMembershipMapper());
    }

    @Override
    @Transactional
    public void remove(UUID tenantId,
                       UUID membershipId,
                       String reason,
                       UUID updatedBy) {
        int rows = jdbc.update("""
                UPDATE crm_queue_memberships
                   SET status='REMOVED',
                       removed_at=CURRENT_TIMESTAMP,
                       removed_reason=:reason,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", membershipId)
                .addValue("reason", reason)
                .addValue("updatedBy", updatedBy));
        if (rows != 1) {
            throw new OwnershipDomainException(
                    "Queue membership not found or already removed: " + membershipId);
        }
    }

    @Override
    public long countActiveByQueue(UUID tenantId, UUID queueId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_queue_memberships
                 WHERE tenant_id=:tenantId
                   AND queue_id=:queueId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("queueId", queueId), Long.class);
        return count != null ? count : 0L;
    }
}
