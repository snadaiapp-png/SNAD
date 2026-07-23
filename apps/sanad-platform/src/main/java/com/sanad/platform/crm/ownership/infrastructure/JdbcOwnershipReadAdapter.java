package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipHistory;
import com.sanad.platform.crm.ownership.domain.OwnershipHistoryPage;
import com.sanad.platform.crm.ownership.domain.OwnershipReadPort;
import com.sanad.platform.crm.ownership.domain.QueueItemPage;
import com.sanad.platform.crm.ownership.domain.QueueItemSummary;
import com.sanad.platform.crm.ownership.domain.WorkloadSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.assignmentMapper;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.ownershipHistoryMapper;

/** Read-side adapter implementing tenant-scoped ownership projections. */
@Component
public class JdbcOwnershipReadAdapter implements OwnershipReadPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnershipReadAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Assignment> findActiveAssignment(UUID tenantId,
                                                      AssignmentRecordType recordType,
                                                      UUID recordId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_assignments
                     WHERE tenant_id=:tenantId
                       AND record_type=:recordType
                       AND record_id=:recordId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("recordType", recordType.name())
                    .addValue("recordId", recordId), assignmentMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public OwnershipHistoryPage findOwnershipHistory(UUID tenantId,
                                                       AssignmentRecordType recordType,
                                                       UUID recordId,
                                                       UUID cursor,
                                                       int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, 500));
        StringBuilder sql = new StringBuilder("""
                SELECT history.*
                  FROM crm_ownership_history history
                 WHERE history.tenant_id=:tenantId
                   AND history.record_type=:recordType
                   AND history.record_id=:recordId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordType", recordType.name())
                .addValue("recordId", recordId)
                .addValue("limit", limit + 1);

        if (cursor != null) {
            Instant cursorRecordedAt = findCursorRecordedAt(
                    tenantId, recordType, recordId, cursor);
            sql.append("""
                     AND (history.recorded_at, history.id)
                         < (:cursorRecordedAt, :cursorId)
                    """);
            parameters
                    .addValue("cursorRecordedAt", Timestamp.from(cursorRecordedAt))
                    .addValue("cursorId", cursor);
        }

        sql.append(" ORDER BY history.recorded_at DESC, history.id DESC LIMIT :limit");
        List<OwnershipHistory> entries = jdbc.query(
                sql.toString(), parameters, ownershipHistoryMapper());
        boolean hasMore = entries.size() > limit;
        if (hasMore) {
            entries = List.copyOf(entries.subList(0, limit));
        }
        UUID nextCursor = hasMore && !entries.isEmpty()
                ? entries.get(entries.size() - 1).id() : null;
        return new OwnershipHistoryPage(entries, nextCursor, hasMore);
    }

    private Instant findCursorRecordedAt(UUID tenantId,
                                         AssignmentRecordType recordType,
                                         UUID recordId,
                                         UUID cursor) {
        try {
            Timestamp timestamp = jdbc.queryForObject("""
                    SELECT recorded_at
                      FROM crm_ownership_history
                     WHERE tenant_id=:tenantId
                       AND record_type=:recordType
                       AND record_id=:recordId
                       AND id=:cursor
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("recordType", recordType.name())
                    .addValue("recordId", recordId)
                    .addValue("cursor", cursor), Timestamp.class);
            if (timestamp == null) {
                throw invalidCursor(cursor);
            }
            return timestamp.toInstant();
        } catch (EmptyResultDataAccessException missing) {
            throw invalidCursor(cursor);
        }
    }

    @Override
    public QueueItemPage findQueueItems(UUID tenantId,
                                        UUID queueId,
                                        UUID cursor,
                                        int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, 100));
        StringBuilder sql = new StringBuilder("""
                SELECT assignment.id,
                       assignment.tenant_id,
                       assignment.owner_queue_id,
                       assignment.record_type,
                       assignment.record_id,
                       assignment.effective_from,
                       assignment.reason,
                       assignment.correlation_id
                  FROM crm_assignments assignment
                 WHERE assignment.tenant_id=:tenantId
                   AND assignment.owner_type='QUEUE'
                   AND assignment.owner_queue_id=:queueId
                   AND assignment.status='ACTIVE'
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("queueId", queueId)
                .addValue("limit", limit + 1);

        if (cursor != null) {
            Instant cursorTime = findQueueCursorTime(tenantId, queueId, cursor);
            sql.append("""
                     AND (assignment.effective_from, assignment.id)
                         > (:cursorTime, :cursorId)
                    """);
            parameters
                    .addValue("cursorTime", Timestamp.from(cursorTime))
                    .addValue("cursorId", cursor);
        }

        sql.append(" ORDER BY assignment.effective_from ASC, assignment.id ASC LIMIT :limit");
        List<QueueItemSummary> items = jdbc.query(sql.toString(), parameters, (rs, rowNum) ->
                new QueueItemSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("owner_queue_id", UUID.class),
                        AssignmentRecordType.valueOf(rs.getString("record_type")),
                        rs.getObject("record_id", UUID.class),
                        rs.getTimestamp("effective_from").toInstant(),
                        rs.getString("reason"),
                        rs.getObject("correlation_id", UUID.class)));
        boolean hasMore = items.size() > limit;
        if (hasMore) {
            items = List.copyOf(items.subList(0, limit));
        }
        UUID nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).assignmentId() : null;
        return new QueueItemPage(items, nextCursor, hasMore);
    }

    private Instant findQueueCursorTime(UUID tenantId, UUID queueId, UUID cursor) {
        try {
            Timestamp timestamp = jdbc.queryForObject("""
                    SELECT effective_from
                      FROM crm_assignments
                     WHERE tenant_id=:tenantId
                       AND owner_type='QUEUE'
                       AND owner_queue_id=:queueId
                       AND status='ACTIVE'
                       AND id=:cursor
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("queueId", queueId)
                    .addValue("cursor", cursor), Timestamp.class);
            if (timestamp == null) {
                throw invalidQueueCursor(cursor);
            }
            return timestamp.toInstant();
        } catch (EmptyResultDataAccessException missing) {
            throw invalidQueueCursor(cursor);
        }
    }

    private OwnershipDomainException invalidCursor(UUID cursor) {
        return new OwnershipDomainException("Invalid ownership-history cursor: " + cursor);
    }

    private OwnershipDomainException invalidQueueCursor(UUID cursor) {
        return new OwnershipDomainException("Invalid queue-item cursor: " + cursor);
    }

    @Override
    public WorkloadSummary findUserWorkload(UUID tenantId, UUID userId) {
        long activeAssignments = countActiveUserAssignments(tenantId, userId);
        long activeQueueItems = countActiveQueueClaims(tenantId, userId);
        long activeTeamMemberships = count("""
                SELECT COUNT(*)
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId
                   AND user_id=:userId
                   AND status='ACTIVE'
                """, tenantId, userId);
        long overdueTasks = count("""
                SELECT COUNT(*)
                  FROM crm_tasks
                 WHERE tenant_id=:tenantId
                   AND owner_user_id=:userId
                   AND status='OPEN'
                   AND due_at<CURRENT_TIMESTAMP
                """, tenantId, userId);

        return new WorkloadSummary(
                tenantId, userId, OwnerType.USER,
                activeAssignments, activeQueueItems,
                activeTeamMemberships, overdueTasks);
    }

    @Override
    public int findUserQueueClaimCount(UUID tenantId, UUID userId) {
        return Math.toIntExact(countActiveQueueClaims(tenantId, userId));
    }

    private long countActiveUserAssignments(UUID tenantId, UUID userId) {
        return count("""
                SELECT COUNT(*)
                  FROM crm_assignments
                 WHERE tenant_id=:tenantId
                   AND owner_type='USER'
                   AND owner_user_id=:userId
                   AND status='ACTIVE'
                """, tenantId, userId);
    }

    private long countActiveQueueClaims(UUID tenantId, UUID userId) {
        return count("""
                SELECT COUNT(*)
                  FROM crm_assignments assignment
                  JOIN LATERAL (
                      SELECT history.change_type,
                             history.to_owner_user_id
                        FROM crm_ownership_history history
                       WHERE history.tenant_id=assignment.tenant_id
                         AND history.record_type=assignment.record_type
                         AND history.record_id=assignment.record_id
                       ORDER BY history.recorded_at DESC, history.id DESC
                       LIMIT 1
                  ) latest_history ON TRUE
                 WHERE assignment.tenant_id=:tenantId
                   AND assignment.owner_type='USER'
                   AND assignment.owner_user_id=:userId
                   AND assignment.status='ACTIVE'
                   AND latest_history.change_type='QUEUE_CLAIM'
                   AND latest_history.to_owner_user_id=:userId
                """, tenantId, userId);
    }

    private long count(String sql, UUID tenantId, UUID userId) {
        Long count = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("userId", userId),
                Long.class);
        return count != null ? count : 0L;
    }
}
