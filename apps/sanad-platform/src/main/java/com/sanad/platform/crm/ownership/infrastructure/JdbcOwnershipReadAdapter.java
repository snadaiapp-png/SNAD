package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

/** Read-side adapter implementing OwnershipReadPort. */
@Component
public class JdbcOwnershipReadAdapter implements OwnershipReadPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnershipReadAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Assignment> findActiveAssignment(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_assignments WHERE tenant_id=:tenantId AND record_type=:recordType AND record_id=:recordId AND status='ACTIVE'",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("recordType", recordType.name())
                            .addValue("recordId", recordId),
                    assignmentMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public OwnershipHistoryPage findOwnershipHistory(UUID tenantId, AssignmentRecordType recordType, UUID recordId,
                                                      UUID cursor, int pageSize) {
        int limit = Math.min(pageSize, 500);
        String sql = """
            SELECT * FROM crm_ownership_history
            WHERE tenant_id=:tenantId AND record_type=:recordType AND record_id=:recordId
            """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordType", recordType.name())
                .addValue("recordId", recordId);
        if (cursor != null) {
            sql += " AND id < :cursor";
            params.addValue("cursor", cursor);
        }
        sql += " ORDER BY recorded_at DESC, id DESC LIMIT :limit";
        params.addValue("limit", limit + 1); // fetch one extra to check hasMore
        List<OwnershipHistory> entries = jdbc.query(sql, params, ownershipHistoryMapper());
        boolean hasMore = entries.size() > limit;
        if (hasMore) {
            entries = entries.subList(0, limit);
        }
        UUID nextCursor = hasMore && !entries.isEmpty() ? entries.get(entries.size() - 1).id() : null;
        return new OwnershipHistoryPage(entries, nextCursor, hasMore);
    }

    @Override
    public WorkloadSummary findUserWorkload(UUID tenantId, UUID userId) {
        Long activeAssignments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND owner_user_id=:userId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Long.class);
        Long activeQueueItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND owner_user_id=:userId AND owner_type='QUEUE' AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Long.class);
        Long activeTeamMemberships = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_team_memberships WHERE tenant_id=:tenantId AND user_id=:userId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Long.class);
        Long overdueTasks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_tasks WHERE tenant_id=:tenantId AND owner_user_id=:userId AND status='OPEN' AND due_at < CURRENT_TIMESTAMP",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Long.class);
        return new WorkloadSummary(
                tenantId, userId, OwnerType.USER,
                activeAssignments != null ? activeAssignments : 0,
                activeQueueItems != null ? activeQueueItems : 0,
                activeTeamMemberships != null ? activeTeamMemberships : 0,
                overdueTasks != null ? overdueTasks : 0
        );
    }

    @Override
    public int findUserQueueClaimCount(UUID tenantId, UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND owner_user_id=:userId AND owner_type='QUEUE' AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Integer.class);
        return count != null ? count : 0;
    }
}
