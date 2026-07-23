package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueNotFoundException;
import com.sanad.platform.crm.ownership.domain.QueueRecordType;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.queueMapper;

@Repository
public class JdbcQueueRepository implements QueueRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcQueueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Queue save(Queue queue) {
        validateEscalationTarget(queue);
        if (queue.id() != null && findById(queue.tenantId(), queue.id()).isPresent()) {
            return update(queue);
        }
        return insert(queue);
    }

    private Queue insert(Queue queue) {
        UUID id = queue.id() != null ? queue.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_queues
                      (id, tenant_id, code, display_name, record_type, description, status,
                       max_items_per_user, sla_minutes, escalation_target_queue_id, default_owner_id,
                       created_at, updated_at, created_by, updated_by)
                    VALUES
                      (:id, :tenantId, :code, :displayName, :recordType, :description, :status,
                       :maxItems, :slaMinutes, :escalationQueueId, :defaultOwnerId,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                    """, parameters(id, queue));
        } catch (DuplicateKeyException conflict) {
            throw new OwnershipDomainException(
                    "Queue code already exists in tenant: " + queue.code());
        }
        return findById(queue.tenantId(), id).orElseThrow();
    }

    private Queue update(Queue queue) {
        Queue current = findById(queue.tenantId(), queue.id())
                .orElseThrow(() -> new QueueNotFoundException(queue.tenantId(), queue.id()));
        validateStatusTransition(current.status(), queue.status());

        int rows = jdbc.update("""
                UPDATE crm_queues
                   SET display_name=:displayName,
                       description=:description,
                       status=:status,
                       max_items_per_user=:maxItems,
                       sla_minutes=:slaMinutes,
                       escalation_target_queue_id=:escalationQueueId,
                       default_owner_id=:defaultOwnerId,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                """, parameters(queue.id(), queue));
        if (rows != 1) {
            throw new QueueNotFoundException(queue.tenantId(), queue.id());
        }
        return findById(queue.tenantId(), queue.id()).orElseThrow();
    }

    private MapSqlParameterSource parameters(UUID id, Queue queue) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", queue.tenantId())
                .addValue("code", queue.code())
                .addValue("displayName", queue.displayName())
                .addValue("recordType", queue.recordType().name())
                .addValue("description", queue.description())
                .addValue("status", queue.status().name())
                .addValue("maxItems", queue.maxItemsPerUser())
                .addValue("slaMinutes", queue.slaMinutes())
                .addValue("escalationQueueId", queue.escalationTargetQueueId())
                .addValue("defaultOwnerId", queue.defaultOwnerId())
                .addValue("createdBy", queue.createdBy())
                .addValue("updatedBy", queue.updatedBy());
    }

    private void validateEscalationTarget(Queue queue) {
        UUID targetId = queue.escalationTargetQueueId();
        if (targetId == null) {
            return;
        }
        if (targetId.equals(queue.id())) {
            throw new OwnershipDomainException("Queue cannot escalate to itself: " + targetId);
        }
        Queue target = findById(queue.tenantId(), targetId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Escalation queue must exist in the same tenant: " + targetId));
        if (target.status() == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Escalation queue is archived: " + targetId);
        }
        if (target.recordType() != queue.recordType()) {
            throw new OwnershipDomainException(
                    "Escalation queue record type mismatch: source=" + queue.recordType()
                            + " target=" + target.recordType());
        }
    }

    private void validateStatusTransition(QueueStatus current, QueueStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case ACTIVE -> target == QueueStatus.DRAINING || target == QueueStatus.ARCHIVED;
            case DRAINING -> target == QueueStatus.ACTIVE || target == QueueStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new OwnershipDomainException(
                    "Invalid queue status transition: " + current + " -> " + target);
        }
    }

    @Override
    public Optional<Queue> findById(UUID tenantId, UUID queueId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_queues
                     WHERE tenant_id=:tenantId
                       AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", queueId), queueMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Queue> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_queues
                     WHERE tenant_id=:tenantId
                       AND code=:code
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("code", code), queueMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<Queue> findByTenant(UUID tenantId, QueueStatus status) {
        return jdbc.query("""
                SELECT *
                  FROM crm_queues
                 WHERE tenant_id=:tenantId
                   AND status=:status
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", status.name()), queueMapper());
    }

    @Override
    public List<Queue> findByRecordType(UUID tenantId, QueueRecordType recordType) {
        return jdbc.query("""
                SELECT *
                  FROM crm_queues
                 WHERE tenant_id=:tenantId
                   AND record_type=:recordType
                   AND status='ACTIVE'
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordType", recordType.name()), queueMapper());
    }

    @Override
    @Transactional
    public void updateStatus(UUID tenantId,
                             UUID queueId,
                             QueueStatus status,
                             UUID updatedBy) {
        Queue current = findById(tenantId, queueId)
                .orElseThrow(() -> new QueueNotFoundException(tenantId, queueId));
        validateStatusTransition(current.status(), status);
        int rows = jdbc.update("""
                UPDATE crm_queues
                   SET status=:status,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status=:currentStatus
                """, new MapSqlParameterSource()
                .addValue("status", status.name())
                .addValue("currentStatus", current.status().name())
                .addValue("updatedBy", updatedBy)
                .addValue("tenantId", tenantId)
                .addValue("id", queueId));
        if (rows != 1) {
            throw new OwnershipDomainException(
                    "Concurrent queue status change: " + queueId);
        }
    }
}
