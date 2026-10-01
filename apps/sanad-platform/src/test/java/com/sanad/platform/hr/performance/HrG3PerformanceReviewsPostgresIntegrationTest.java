package com.sanad.platform.hr.performance;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G3 Task 2 — PostgreSQL Direct contract for performance reviews.
 *
 * <p>Runs against host-native PostgreSQL only. Docker/Testcontainers are
 * prohibited. The fixture is self-contained: every test creates its own
 * tenant + canonical Person/Employment graph, so a missing external seed can
 * never turn a security assertion into SKIPPED.</p>
 *
 * <p>Contracts (execution order §10):
 * A) reviews persistence contract exists;
 * B) canonical subject integrity (Employment ↔ Person must match);
 * C) cross-tenant read isolation;
 * D) no-tenant-context fail-closed;
 * E) reviewer tenant isolation;
 * F) valid canonical review persists;
 * plus structural integrity: duplicate (subject, reviewer, source, period),
 * rating range and submission requirement, period ordering, canonical
 * lifecycle status values and reviewer/subject source rules.</p>
 *
 * <p>Constraint-order note: PostgreSQL evaluates row CHECK constraints before
 * foreign keys, so tests that target a specific FK violation use a source and
 * reviewer that satisfy every CHECK deterministically.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG3PerformanceReviewsPostgresIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    void requirePostgres() {
        Assumptions.assumeTrue(
                System.getenv("SPRING_DATASOURCE_URL") != null
                        || "sanad".equals(System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "")),
                "PostgreSQL Direct tests require SPRING_DATASOURCE_URL"
        );
    }

    // ==================== A. persistence contract exists ====================

    @Test
    void performanceReviewsPersistenceContractExists() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before review persistence can be certified")
                    .isTrue();
        }
    }

    // ==================== B. canonical subject integrity ====================

    @Test
    void reviewRequiresCanonicalSubjectEmployment() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before canonical subject enforcement can be certified")
                    .isTrue();

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID employmentOwner = seedPerson(conn, tenantId, "Employment", "Owner");
            UUID differentPerson = seedPerson(conn, tenantId, "Different", "Person");
            UUID reviewerPerson = seedPerson(conn, tenantId, "Canonical", "Reviewer");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID employmentId = seedEmployment(conn, tenantId, employmentOwner, legalEntityId);
            UUID reviewerEmploymentId = seedEmployment(conn, tenantId, reviewerPerson, legalEntityId);

            // Review binds employment E to a Person that is not the canonical
            // owner of E — PostgreSQL must reject with a foreign-key violation.
            // MANAGER source with a valid distinct reviewer keeps every CHECK
            // satisfied so the composite canonical FK is the failing contract.
            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    differentPerson, employmentId,
                    reviewerPerson, reviewerEmploymentId,
                    "MANAGER", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "manager assessment"))
                    .as("A review must not bind an Employment to a different canonical Person")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23503");
        }
    }

    // ==================== C. cross-tenant read isolation ====================

    @Test
    void foreignTenantCannotReadPerformanceReview() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before tenant isolation can be certified")
                    .isTrue();

            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            seedTenant(conn, tenantA);
            seedTenant(conn, tenantB);

            setTenant(conn, tenantA);
            UUID personId = seedPerson(conn, tenantA, "Tenant", "A");
            UUID legalEntityId = seedLegalEntity(conn, tenantA);
            UUID employmentId = seedEmployment(conn, tenantA, personId, legalEntityId);

            UUID reviewId = UUID.randomUUID();
            insertReview(
                    conn, reviewId, tenantA,
                    personId, employmentId,
                    personId, employmentId,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "self assessment");

            setTenant(conn, tenantB);
            assertThat(countReviews(conn, reviewId))
                    .as("A foreign tenant must never see another tenant's performance review")
                    .isZero();
        }
    }

    // ==================== D. no-context fail-closed ====================

    @Test
    void noTenantContextCannotReadPerformanceReview() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before no-context isolation can be certified")
                    .isTrue();

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID personId = seedPerson(conn, tenantId, "No", "Context");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID employmentId = seedEmployment(conn, tenantId, personId, legalEntityId);

            UUID reviewId = UUID.randomUUID();
            insertReview(
                    conn, reviewId, tenantId,
                    personId, employmentId,
                    personId, employmentId,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "self assessment");

            setTenant(conn, tenantId);
            assertThat(countReviews(conn, reviewId))
                    .as("Row must be visible under its own tenant before the no-context assertion")
                    .isEqualTo(1);

            resetTenant(conn);
            assertThat(countReviews(conn, reviewId))
                    .as("Missing tenant context must fail closed (0 rows)")
                    .isZero();
        }
    }

    // ==================== E. reviewer tenant isolation ====================

    @Test
    void foreignTenantReviewerIsRejected() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before reviewer isolation can be certified")
                    .isTrue();

            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            seedTenant(conn, tenantA);
            seedTenant(conn, tenantB);

            setTenant(conn, tenantA);
            UUID subjectPerson = seedPerson(conn, tenantA, "Subject", "A");
            UUID legalEntityA = seedLegalEntity(conn, tenantA);
            UUID subjectEmployment = seedEmployment(conn, tenantA, subjectPerson, legalEntityA);

            setTenant(conn, tenantB);
            UUID reviewerPersonB = seedPerson(conn, tenantB, "Reviewer", "B");
            UUID legalEntityB = seedLegalEntity(conn, tenantB);
            UUID reviewerEmploymentB = seedEmployment(conn, tenantB, reviewerPersonB, legalEntityB);

            // The row is written under tenant A, but references a reviewer
            // employment that exists only in tenant B — the canonical
            // tenant-safe reviewer FK must reject it. Source MANAGER and
            // distinct employments keep the row CHECKs satisfied.
            setTenant(conn, tenantA);
            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantA,
                    subjectPerson, subjectEmployment,
                    reviewerPersonB, reviewerEmploymentB,
                    "MANAGER", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 3, "manager note"))
                    .as("A reviewer from a foreign tenant must never be referenceable")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23503");
        }
    }

    // ==================== F. valid canonical review ====================

    @Test
    void validCanonicalReviewPersists() throws Exception {
        try (Connection conn = newConnection()) {
            assertThat(tableExists(conn, "hr_performance_reviews"))
                    .as("RED: G3 must create hr_performance_reviews before the valid-review contract can be certified")
                    .isTrue();

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID subjectPerson = seedPerson(conn, tenantId, "Valid", "Subject");
            UUID reviewerPerson = seedPerson(conn, tenantId, "Valid", "Reviewer");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID subjectEmployment = seedEmployment(conn, tenantId, subjectPerson, legalEntityId);
            UUID reviewerEmployment = seedEmployment(conn, tenantId, reviewerPerson, legalEntityId);

            // SELF review: reviewer is the subject.
            UUID selfReviewId = UUID.randomUUID();
            insertReview(
                    conn, selfReviewId, tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "self assessment");
            assertThat(countReviews(conn, selfReviewId)).isEqualTo(1);

            // MANAGER review: canonical reviewer distinct from subject.
            UUID managerReviewId = UUID.randomUUID();
            insertReview(
                    conn, managerReviewId, tenantId,
                    subjectPerson, subjectEmployment,
                    reviewerPerson, reviewerEmployment,
                    "MANAGER", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 5, "manager assessment");
            assertThat(countReviews(conn, managerReviewId)).isEqualTo(1);
        }
    }

    // ==================== structural integrity ====================

    @Test
    void duplicateReviewerSourcePeriodIsRejected() throws Exception {
        try (Connection conn = newConnection()) {
            requireTable(conn);

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID subjectPerson = seedPerson(conn, tenantId, "Dup", "Subject");
            UUID reviewerPerson = seedPerson(conn, tenantId, "Dup", "Reviewer");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID subjectEmployment = seedEmployment(conn, tenantId, subjectPerson, legalEntityId);
            UUID reviewerEmployment = seedEmployment(conn, tenantId, reviewerPerson, legalEntityId);

            insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    reviewerPerson, reviewerEmployment,
                    "MANAGER", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "first");

            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    reviewerPerson, reviewerEmployment,
                    "MANAGER", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 2, "duplicate"))
                    .as("One review per subject/reviewer/source/period combination must be enforced")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23505");
        }
    }

    @Test
    void ratingRangeAndSubmissionRequirementAreEnforced() throws Exception {
        try (Connection conn = newConnection()) {
            requireTable(conn);

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID subjectPerson = seedPerson(conn, tenantId, "Rating", "Subject");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID subjectEmployment = seedEmployment(conn, tenantId, subjectPerson, legalEntityId);

            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 0, "invalid rating"))
                    .as("Rating below 1 must be rejected by a check constraint")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");

            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 6, "invalid rating"))
                    .as("Rating above 5 must be rejected by a check constraint")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");

            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", null, "missing rating"))
                    .as("A SUBMITTED review must carry a rating")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");
        }
    }

    @Test
    void reviewPeriodMustBeOrdered() throws Exception {
        try (Connection conn = newConnection()) {
            requireTable(conn);

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID subjectPerson = seedPerson(conn, tenantId, "Period", "Subject");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID subjectEmployment = seedEmployment(conn, tenantId, subjectPerson, legalEntityId);

            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "SUBMITTED", "CYCLE-2026-H1",
                    "2026-06-30", "2026-01-01", 4, "inverted period"))
                    .as("Review period end must not precede period start")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");
        }
    }

    @Test
    void reviewSourceAndStatusValuesAreCanonical() throws Exception {
        try (Connection conn = newConnection()) {
            requireTable(conn);

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID subjectPerson = seedPerson(conn, tenantId, "Status", "Subject");
            UUID reviewerPerson = seedPerson(conn, tenantId, "Status", "Reviewer");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID subjectEmployment = seedEmployment(conn, tenantId, subjectPerson, legalEntityId);
            UUID reviewerEmployment = seedEmployment(conn, tenantId, reviewerPerson, legalEntityId);

            // Unknown lifecycle status must be rejected.
            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "SELF", "ARCHIVED", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", 4, "bad status"))
                    .as("Only canonical lifecycle statuses must be allowed")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");

            // SELF source requires the reviewer to be the subject.
            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    reviewerPerson, reviewerEmployment,
                    "SELF", "DRAFT", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", null, "self with foreign reviewer"))
                    .as("A SELF review must carry the subject as its reviewer")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");

            // MANAGER/PEER sources require a reviewer distinct from the subject.
            assertThatThrownBy(() -> insertReview(
                    conn, UUID.randomUUID(), tenantId,
                    subjectPerson, subjectEmployment,
                    subjectPerson, subjectEmployment,
                    "MANAGER", "DRAFT", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", null, "manager reviewing self"))
                    .as("A MANAGER review must not review the reviewer's own employment")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23514");

            // A DRAFT review may omit the rating (structural draft allowance).
            UUID draftId = UUID.randomUUID();
            insertReview(
                    conn, draftId, tenantId,
                    subjectPerson, subjectEmployment,
                    reviewerPerson, reviewerEmployment,
                    "MANAGER", "DRAFT", "CYCLE-2026-H1",
                    "2026-01-01", "2026-06-30", null, "draft without rating");
            assertThat(countReviews(conn, draftId)).isEqualTo(1);
        }
    }

    // ==================== helpers ====================

    private void requireTable(Connection conn) throws Exception {
        assertThat(tableExists(conn, "hr_performance_reviews"))
                .as("RED: G3 must create hr_performance_reviews before review integrity contracts can be certified")
                .isTrue();
    }

    private Connection newConnection() throws SQLException {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        return ds.getConnection();
    }

    private void seedTenant(Connection conn, UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, 'G3 Review Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "g3r-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedPerson(Connection conn, UUID tenantId, String firstName, String lastName) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people " +
                "(id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, NULL, ?, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.setString(3, firstName);
            ps.setString(4, lastName);
            ps.setString(5, firstName + " " + lastName);
            ps.executeUpdate();
        }
        return personId;
    }

    private UUID seedLegalEntity(Connection conn, UUID tenantId) throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        String code = "G3R-" + legalEntityId.toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities " +
                "(id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "G3 Review Legal Entity " + code);
            ps.executeUpdate();
        }
        return legalEntityId;
    }

    private UUID seedEmployment(
            Connection conn,
            UUID tenantId,
            UUID personId,
            UUID legalEntityId) throws Exception {
        UUID employmentId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees " +
                "(id, tenant_id, person_id, legal_entity_id, employee_number, first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 'G3', 'Reviewer', 'G3 Review Employee', 'FULL_TIME', 'ACTIVE', DATE '2026-01-01', 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "G3R-EMP-" + employmentId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return employmentId;
    }

    private void insertReview(
            Connection conn,
            UUID reviewId,
            UUID tenantId,
            UUID subjectPersonId,
            UUID subjectEmploymentId,
            UUID reviewerPersonId,
            UUID reviewerEmploymentId,
            String source,
            String status,
            String cycle,
            String periodStart,
            String periodEnd,
            Integer rating,
            String comments) throws Exception {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO hr_performance_reviews " +
                "(id, tenant_id, person_id, employment_id, reviewer_person_id, reviewer_employment_id, " +
                "source, status, cycle, period_start, period_end, rating, comments) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setObject(1, reviewId);
            insert.setObject(2, tenantId);
            insert.setObject(3, subjectPersonId);
            insert.setObject(4, subjectEmploymentId);
            insert.setObject(5, reviewerPersonId);
            insert.setObject(6, reviewerEmploymentId);
            insert.setString(7, source);
            insert.setString(8, status);
            insert.setString(9, cycle);
            insert.setObject(10, java.sql.Date.valueOf(periodStart));
            insert.setObject(11, java.sql.Date.valueOf(periodEnd));
            if (rating != null) {
                insert.setInt(12, rating);
            } else {
                insert.setNull(12, java.sql.Types.INTEGER);
            }
            insert.setString(13, comments);
            insert.executeUpdate();
        }
    }

    private int countReviews(Connection conn, UUID reviewId) throws Exception {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_performance_reviews WHERE id = ?")) {
            query.setObject(1, reviewId);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void setTenant(Connection conn, UUID tenantId) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
        }
    }

    private void resetTenant(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT set_config('app.tenant_id', '', false)");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = ?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
