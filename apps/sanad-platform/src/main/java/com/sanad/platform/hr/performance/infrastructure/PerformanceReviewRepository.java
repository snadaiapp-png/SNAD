package com.sanad.platform.hr.performance.infrastructure;

import com.sanad.platform.hr.performance.domain.PerformanceReview;
import com.sanad.platform.hr.performance.domain.PerformanceReview.ReviewSource;
import com.sanad.platform.hr.performance.domain.PerformanceReview.ReviewStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Explicit-column PostgreSQL access for G3 performance reviews.
 *
 * <p>Every operation runs on a SINGLE transaction-scoped connection with
 * {@code set_config('app.tenant_id', ..., true)} so FORCE RLS remains fully
 * enforced (the canonical HR v2 pattern). Queries additionally carry explicit
 * tenant predicates; RLS stays the final fail-closed boundary. Updates are
 * optimistic: they must match the expected {@code version} exactly.</p>
 */
@Repository
public class PerformanceReviewRepository {

    private static final String COLUMNS =
            "id, tenant_id, person_id, employment_id, reviewer_person_id, reviewer_employment_id, "
            + "source, status, cycle, period_start, period_end, rating, comments, "
            + "version, created_at, created_by, updated_at, updated_by";

    private final DataSource dataSource;

    public PerformanceReviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserts a DRAFT review and re-reads it inside the same tenant-scoped transaction. */
    public PerformanceReview insert(PerformanceReview review, UUID actorUserId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, review.tenantId());
                insertWithinTx(connection, review, actorUserId);
                PerformanceReview persisted = findByIdWithinTx(connection, review.tenantId(), review.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "HRM_REVIEW_PERSISTENCE_FAILED: inserted review is not readable in the same transaction"));
                connection.commit();
                return persisted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private void insertWithinTx(Connection connection, PerformanceReview review, UUID actorUserId) throws SQLException {
        String duplicateGuard = "HRM_REVIEW_DUPLICATE_REVIEWER_SOURCE_PERIOD";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_performance_reviews "
                + "(id, tenant_id, person_id, employment_id, reviewer_person_id, reviewer_employment_id, "
                + "source, status, cycle, period_start, period_end, rating, comments, version, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)")) {
            ps.setObject(1, review.id());
            ps.setObject(2, review.tenantId());
            ps.setObject(3, review.subjectPersonId());
            ps.setObject(4, review.subjectEmploymentId());
            ps.setObject(5, review.reviewerPersonId());
            ps.setObject(6, review.reviewerEmploymentId());
            ps.setString(7, review.source().name());
            ps.setString(8, review.status().name());
            ps.setString(9, review.cycle());
            ps.setObject(10, review.periodStart());
            ps.setObject(11, review.periodEnd());
            if (review.rating() != null) {
                ps.setInt(12, review.rating());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            ps.setString(13, review.comments());
            ps.setObject(14, actorUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalStateException(duplicateGuard, e);
            }
            throw e;
        }
    }

    /** Tenant-scoped load (FORCE RLS applies; foreign-tenant ids resolve empty). */
    public Optional<PerformanceReview> findById(UUID tenantId, UUID reviewId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<PerformanceReview> review = findByIdWithinTx(connection, tenantId, reviewId);
                connection.commit();
                return review;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
        }
    }

    private Optional<PerformanceReview> findByIdWithinTx(Connection connection, UUID tenantId, UUID reviewId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM hr_performance_reviews WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    /** Reviews where the employment is the subject or the reviewer, newest period first. */
    public List<PerformanceReview> listForEmployment(UUID tenantId, UUID employmentId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                List<PerformanceReview> reviews = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM hr_performance_reviews "
                        + "WHERE tenant_id = ? AND (employment_id = ? OR reviewer_employment_id = ?) "
                        + "ORDER BY period_start DESC, period_end DESC, id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, employmentId);
                    ps.setObject(3, employmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            reviews.add(mapRow(rs));
                        }
                    }
                }
                connection.commit();
                return reviews;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Reviews of every active direct report of the authenticated manager,
     * derived exclusively from effective-dated PRIMARY assignment reporting
     * (no legacy manager columns).
     */
    public List<PerformanceReview> listTeamReviewsForManager(UUID tenantId, UUID managerUserId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                List<PerformanceReview> reviews = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT " + COLUMNS + " "
                        + "FROM hr_performance_reviews r "
                        + "JOIN hr_employee_assignments target_assignment "
                        + "  ON target_assignment.employment_id = r.employment_id "
                        + " AND target_assignment.tenant_id = r.tenant_id "
                        + "JOIN hr_employee_assignments manager_assignment "
                        + "  ON target_assignment.reports_to_assignment_id = manager_assignment.id "
                        + " AND manager_assignment.tenant_id = target_assignment.tenant_id "
                        + "JOIN hr_employees manager_employment "
                        + "  ON manager_employment.id = manager_assignment.employment_id "
                        + " AND manager_employment.tenant_id = manager_assignment.tenant_id "
                        + "JOIN hr_people manager_person "
                        + "  ON manager_person.id = manager_employment.person_id "
                        + " AND manager_person.tenant_id = manager_employment.tenant_id "
                        + "JOIN users manager_user "
                        + "  ON manager_user.id = manager_person.user_id "
                        + " AND manager_user.tenant_id = manager_person.tenant_id "
                        + "WHERE r.tenant_id = ? "
                        + "AND manager_person.user_id = ? "
                        + "AND manager_user.status = 'ACTIVE' "
                        + "AND manager_employment.status = 'ACTIVE' "
                        + "AND target_assignment.assignment_type = 'PRIMARY' "
                        + "AND target_assignment.status = 'ACTIVE' "
                        + "AND target_assignment.effective_from <= CURRENT_DATE "
                        + "AND (target_assignment.effective_to IS NULL OR target_assignment.effective_to >= CURRENT_DATE) "
                        + "AND manager_assignment.assignment_type = 'PRIMARY' "
                        + "AND manager_assignment.status = 'ACTIVE' "
                        + "AND manager_assignment.effective_from <= CURRENT_DATE "
                        + "AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE) "
                        + "ORDER BY r.period_start DESC, r.period_end DESC, r.id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, managerUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            reviews.add(mapRow(rs));
                        }
                    }
                }
                connection.commit();
                return reviews;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_READ_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Optimistic lifecycle transition. Returns the number of updated rows
     * (exactly 1 on success; 0 means the review moved concurrently).
     */
    public int transition(
            UUID tenantId,
            UUID reviewId,
            int expectedVersion,
            ReviewStatus newStatus,
            UUID actorUserId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_performance_reviews "
                        + "SET status = ?, updated_by = ?, updated_at = NOW(), version = version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND version = ?")) {
                    ps.setString(1, newStatus.name());
                    ps.setObject(2, actorUserId);
                    ps.setObject(3, reviewId);
                    ps.setObject(4, tenantId);
                    ps.setInt(5, expectedVersion);
                    updated = ps.executeUpdate();
                }
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_TRANSITION_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_TRANSITION_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Canonical employment anchor: resolves the owning Person of an ACTIVE
     * employment inside the tenant. Fails closed when the employment does not
     * exist, is not active, or belongs to another tenant.
     */
    public UUID requireCanonicalEmploymentPersonId(UUID tenantId, UUID employmentId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID personId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT person_id FROM hr_employees "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, employmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new AccessDeniedException(
                                    "Target employment is not an active canonical employment in this tenant");
                        }
                        personId = (UUID) rs.getObject(1);
                    }
                }
                connection.commit();
                return personId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_REVIEW_IDENTITY_RESOLUTION_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_REVIEW_IDENTITY_RESOLUTION_FAILED: " + e.getMessage(), e);
        }
    }

    private static PerformanceReview mapRow(ResultSet rs) throws SQLException {
        int version = rs.getInt("version");
        return new PerformanceReview(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("person_id"),
                (UUID) rs.getObject("employment_id"),
                (UUID) rs.getObject("reviewer_person_id"),
                (UUID) rs.getObject("reviewer_employment_id"),
                ReviewSource.valueOf(rs.getString("source")),
                ReviewStatus.valueOf(rs.getString("status")),
                rs.getString("cycle"),
                rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class),
                rs.getObject("rating") == null ? null : rs.getInt("rating"),
                rs.getString("comments"),
                version,
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                (UUID) rs.getObject("created_by"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                (UUID) rs.getObject("updated_by"));
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }
}
