package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HRM-G1 T10 read model for recruitment API queries.
 *
 * <p>Every read uses a transaction-scoped connection with SET LOCAL
 * app.tenant_id so FORCE RLS remains authoritative. List endpoints use UUID
 * keyset pagination for deterministic ordering and never expose encrypted
 * candidate contact columns.</p>
 */
@Service
public class HrRecruitmentApiQueryService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final DataSource dataSource;

    public HrRecruitmentApiQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public CursorPage<OpeningView> openings(UUID tenantId, String cursor, Integer requestedLimit) {
        UUID after = parseCursor(cursor);
        int limit = limit(requestedLimit);
        return inTenant(tenantId, connection -> {
            String sql = """
                    SELECT id, opening_number, job_id, job_version_id, org_unit_id, position_id,
                           state, requested_headcount, filled_headcount, compliance_decision,
                           compliance_decided_at, opens_at, closes_at, version
                    FROM hr_job_openings
                    WHERE tenant_id = ? AND (?::uuid IS NULL OR id > ?::uuid)
                    ORDER BY id
                    LIMIT ?
                    """;
            List<OpeningView> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                setNullableUuid(ps, 2, after);
                setNullableUuid(ps, 3, after);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(opening(rs));
                }
            }
            return page(rows, limit, OpeningView::id);
        });
    }

    public OpeningView opening(UUID tenantId, UUID id) {
        return inTenant(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, opening_number, job_id, job_version_id, org_unit_id, position_id,
                           state, requested_headcount, filled_headcount, compliance_decision,
                           compliance_decided_at, opens_at, closes_at, version
                    FROM hr_job_openings
                    WHERE tenant_id = ? AND id = ?
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw hidden();
                    return opening(rs);
                }
            }
        });
    }

    public CursorPage<CandidateSummary> candidates(UUID tenantId, String cursor, Integer requestedLimit) {
        UUID after = parseCursor(cursor);
        int limit = limit(requestedLimit);
        return inTenant(tenantId, connection -> {
            List<CandidateSummary> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, candidate_number, display_name, pool_state, version
                    FROM hr_candidates
                    WHERE tenant_id = ? AND (?::uuid IS NULL OR id > ?::uuid)
                    ORDER BY id
                    LIMIT ?
                    """)) {
                ps.setObject(1, tenantId);
                setNullableUuid(ps, 2, after);
                setNullableUuid(ps, 3, after);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new CandidateSummary(
                                rs.getObject("id", UUID.class),
                                rs.getString("candidate_number"),
                                maskName(rs.getString("display_name")),
                                rs.getString("pool_state"),
                                rs.getLong("version")));
                    }
                }
            }
            return page(rows, limit, CandidateSummary::id);
        });
    }

    public CursorPage<ApplicationView> applications(UUID tenantId, String cursor, Integer requestedLimit) {
        UUID after = parseCursor(cursor);
        int limit = limit(requestedLimit);
        return inTenant(tenantId, connection -> {
            List<ApplicationView> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, candidate_id, job_opening_id, state, applied_at, version
                    FROM hr_applications
                    WHERE tenant_id = ? AND (?::uuid IS NULL OR id > ?::uuid)
                    ORDER BY id
                    LIMIT ?
                    """)) {
                ps.setObject(1, tenantId);
                setNullableUuid(ps, 2, after);
                setNullableUuid(ps, 3, after);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(application(rs));
                }
            }
            return page(rows, limit, ApplicationView::id);
        });
    }

    public ApplicationView application(UUID tenantId, UUID id) {
        return inTenant(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, candidate_id, job_opening_id, state, applied_at, version
                    FROM hr_applications
                    WHERE tenant_id = ? AND id = ?
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw hidden();
                    return application(rs);
                }
            }
        });
    }

    public List<FeedbackView> feedback(UUID tenantId, UUID interviewId) {
        return inTenant(tenantId, connection -> {
            List<FeedbackView> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT f.id, p.user_id, f.scorecard::text, f.outcome, f.version, f.updated_at
                    FROM hr_interview_feedback f
                    JOIN hr_interview_participants p
                      ON p.tenant_id=f.tenant_id AND p.id=f.participant_id
                    WHERE f.tenant_id=? AND f.interview_id=?
                    ORDER BY p.user_id, f.id
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, interviewId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JsonNode scorecard;
                        try {
                            scorecard = JSON.readTree(rs.getString("scorecard"));
                        } catch (Exception invalidJson) {
                            throw new IllegalStateException("HRM_INTERVIEW_FEEDBACK_INVALID: stored scorecard", invalidJson);
                        }
                        rows.add(new FeedbackView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("user_id", UUID.class),
                                scorecard,
                                rs.getString("outcome"),
                                rs.getLong("version"),
                                offset(rs.getTimestamp("updated_at"))));
                    }
                }
            }
            return List.copyOf(rows);
        });
    }

    private static OpeningView opening(ResultSet rs) throws SQLException {
        return new OpeningView(
                rs.getObject("id", UUID.class),
                rs.getString("opening_number"),
                rs.getObject("job_id", UUID.class),
                rs.getObject("job_version_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class),
                rs.getObject("position_id", UUID.class),
                rs.getString("state"),
                rs.getInt("requested_headcount"),
                rs.getInt("filled_headcount"),
                rs.getString("compliance_decision"),
                offset(rs.getTimestamp("compliance_decided_at")),
                offset(rs.getTimestamp("opens_at")),
                offset(rs.getTimestamp("closes_at")),
                rs.getLong("version"));
    }

    private static ApplicationView application(ResultSet rs) throws SQLException {
        return new ApplicationView(
                rs.getObject("id", UUID.class),
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("job_opening_id", UUID.class),
                rs.getString("state"),
                offset(rs.getTimestamp("applied_at")),
                rs.getLong("version"));
    }

    private static OffsetDateTime offset(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    private static String maskName(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= 1) return "*";
        return trimmed.substring(0, 1) + "***";
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
                throw new IllegalStateException("HRM_RECRUITMENT_QUERY_FAILED: " + failure.getMessage(), failure);
            }
        } catch (SQLException sql) {
            throw new IllegalStateException("HRM_RECRUITMENT_QUERY_FAILED: " + sql.getMessage(), sql);
        }
    }

    private static <T> CursorPage<T> page(List<T> rows, int limit, IdExtractor<T> ids) {
        String next = rows.size() == limit && !rows.isEmpty()
                ? ids.id(rows.get(rows.size() - 1)).toString()
                : null;
        return new CursorPage<>(List.copyOf(rows), next);
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws Exception; }

    @FunctionalInterface
    private interface IdExtractor<T> { UUID id(T value); }

    public record CursorPage<T>(List<T> items, String nextCursor) {}

    public record OpeningView(
            UUID id, String openingNumber, UUID jobId, UUID jobVersionId, UUID orgUnitId, UUID positionId,
            String state, int requestedHeadcount, int filledHeadcount, String complianceDecision,
            OffsetDateTime complianceDecidedAt, OffsetDateTime opensAt, OffsetDateTime closesAt, long version) {}

    public record CandidateSummary(
            UUID id, String candidateNumber, String displayName, String poolState, long version) {}

    public record ApplicationView(
            UUID id, UUID candidateId, UUID jobOpeningId, String state, OffsetDateTime appliedAt, long version) {}

    public record FeedbackView(
            UUID id, UUID participantUserId, JsonNode scorecard, String outcome, long version,
            OffsetDateTime updatedAt) {}
}
