package com.sanad.platform.hr.api.v2.onboarding;

import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import com.sanad.platform.hr.api.v2.HrDomainException;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G1 T10 tenant-scoped onboarding read model.
 */
@Service
public class HrOnboardingApiQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final DataSource dataSource;

    public HrOnboardingApiQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public CursorPage<PlanSummary> plans(UUID tenantId, String cursor, Integer requestedLimit) {
        UUID after = parseCursor(cursor);
        int limit = limit(requestedLimit);
        return inTenant(tenantId, connection -> {
            List<PlanSummary> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, plan_number, employment_id, state, workflow_linked, version, created_at, updated_at
                    FROM hr_onboarding_plans
                    WHERE tenant_id=? AND (?::uuid IS NULL OR id > ?::uuid)
                    ORDER BY id
                    LIMIT ?
                    """)) {
                ps.setObject(1, tenantId);
                setNullableUuid(ps, 2, after);
                setNullableUuid(ps, 3, after);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(planSummary(rs));
                }
            }
            String next = rows.size() == limit && !rows.isEmpty()
                    ? rows.get(rows.size() - 1).id().toString() : null;
            return new CursorPage<>(List.copyOf(rows), next);
        });
    }

    public PlanDetail plan(UUID tenantId, UUID planId) {
        return inTenant(tenantId, connection -> {
            PlanSummary plan;
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, plan_number, employment_id, state, workflow_linked, version, created_at, updated_at
                    FROM hr_onboarding_plans
                    WHERE tenant_id=? AND id=?
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw hidden();
                    plan = planSummary(rs);
                }
            }

            List<TaskView> tasks = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, seq, title, state, assignee_user_id, due_at, reason_code, resolved_at
                    FROM hr_onboarding_tasks
                    WHERE tenant_id=? AND plan_id=?
                    ORDER BY seq, id
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tasks.add(new TaskView(
                                rs.getObject("id", UUID.class),
                                rs.getInt("seq"),
                                rs.getString("title"),
                                rs.getString("state"),
                                rs.getObject("assignee_user_id", UUID.class),
                                offset(rs.getTimestamp("due_at")),
                                rs.getString("reason_code"),
                                offset(rs.getTimestamp("resolved_at"))));
                    }
                }
            }
            return new PlanDetail(plan, List.copyOf(tasks));
        });
    }

    private static PlanSummary planSummary(ResultSet rs) throws SQLException {
        return new PlanSummary(
                rs.getObject("id", UUID.class),
                rs.getString("plan_number"),
                rs.getObject("employment_id", UUID.class),
                rs.getString("state"),
                rs.getBoolean("workflow_linked"),
                rs.getLong("version"),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at")));
    }

    private static OffsetDateTime offset(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    private static UUID parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return UUID.fromString(cursor.trim());
        } catch (IllegalArgumentException invalid) {
            throw new HrDomainException(HrApiErrorCode.HRM_VALIDATION_FAILED, "Invalid cursor");
        }
    }

    private static int limit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new HrDomainException(HrApiErrorCode.HRM_VALIDATION_FAILED,
                    "limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }

    private static HrDomainException hidden() {
        return new HrDomainException(HrApiErrorCode.HRM_TENANT_CONTEXT_MISMATCH,
                "Resource is not visible in the current tenant context");
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.OTHER);
        else ps.setObject(index, value);
    }

    private <T> T inTenant(UUID tenantId, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Throwable failure) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("HRM_ONBOARDING_QUERY_FAILED: " + failure.getMessage(), failure);
            }
        } catch (SQLException sql) {
            throw new IllegalStateException("HRM_ONBOARDING_QUERY_FAILED: " + sql.getMessage(), sql);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws Exception; }

    public record CursorPage<T>(List<T> items, String nextCursor) {}

    public record PlanSummary(
            UUID id, String planNumber, UUID employmentId, String state, boolean workflowLinked,
            long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record TaskView(
            UUID id, int sequence, String title, String state, UUID assigneeUserId,
            OffsetDateTime dueAt, String reasonCode, OffsetDateTime resolvedAt) {}

    public record PlanDetail(PlanSummary plan, List<TaskView> tasks) {}
}
