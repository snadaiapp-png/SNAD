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
 * HRM-G3 PostgreSQL Direct contract.
 *
 * <p>Runs against host-native PostgreSQL only. The fixture is self-contained:
 * every test creates its own tenant + canonical Person/Employment graph, so a
 * missing external seed can never turn a security assertion into SKIPPED.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG3PostgresIntegrationTest {

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

    @Test
    void foreignTenantCannotReadPerformanceGoal() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection()) {
            assertThat(tableExists(conn, "hr_performance_goals"))
                    .as("G3 must create hr_performance_goals before tenant isolation can be certified")
                    .isTrue();

            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            seedTenant(conn, tenantA);
            seedTenant(conn, tenantB);

            setTenant(conn, tenantA);
            UUID personId = seedPerson(conn, tenantA, "Tenant", "A");
            UUID legalEntityId = seedLegalEntity(conn, tenantA);
            UUID employmentId = seedEmployment(conn, tenantA, personId, legalEntityId);

            UUID goalId = UUID.randomUUID();
            insertGoal(conn, goalId, tenantA, personId, employmentId);

            setTenant(conn, tenantB);
            try (PreparedStatement query = conn.prepareStatement(
                    "SELECT COUNT(*) FROM hr_performance_goals WHERE id = ?")) {
                query.setObject(1, goalId);
                try (ResultSet rs = query.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("A foreign tenant must never see another tenant's performance goal")
                            .isZero();
                }
            }
        }
    }

    @Test
    void goalRequiresCanonicalEmployment() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection()) {
            assertThat(tableExists(conn, "hr_performance_goals"))
                    .as("G3 goal persistence must exist before canonical-employment enforcement can be certified")
                    .isTrue();

            UUID tenantId = UUID.randomUUID();
            seedTenant(conn, tenantId);
            setTenant(conn, tenantId);

            UUID employmentOwner = seedPerson(conn, tenantId, "Employment", "Owner");
            UUID differentPerson = seedPerson(conn, tenantId, "Different", "Person");
            UUID legalEntityId = seedLegalEntity(conn, tenantId);
            UUID employmentId = seedEmployment(conn, tenantId, employmentOwner, legalEntityId);

            assertThatThrownBy(() -> insertGoal(
                    conn, UUID.randomUUID(), tenantId, differentPerson, employmentId))
                    .as("A goal must not bind an Employment to a different canonical Person")
                    .isInstanceOf(SQLException.class)
                    .extracting(t -> ((SQLException) t).getSQLState())
                    .isEqualTo("23503");
        }
    }

    private void seedTenant(Connection conn, UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, 'G3 Test', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "g3-" + tenantId.toString().substring(0, 8));
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
        String code = "G3-" + legalEntityId.toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities " +
                "(id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "G3 Legal Entity " + code);
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
                "VALUES (?, ?, ?, ?, ?, 'G3', 'Employee', 'G3 Employee', 'FULL_TIME', 'ACTIVE', DATE '2026-01-01', 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "G3-EMP-" + employmentId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return employmentId;
    }

    private void insertGoal(
            Connection conn,
            UUID goalId,
            UUID tenantId,
            UUID personId,
            UUID employmentId) throws Exception {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO hr_performance_goals " +
                "(id, tenant_id, person_id, employment_id, title, metric, target_value, progress, status, starts_on, ends_on) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, CURRENT_DATE + 30)")) {
            insert.setObject(1, goalId);
            insert.setObject(2, tenantId);
            insert.setObject(3, personId);
            insert.setObject(4, employmentId);
            insert.setString(5, "G3 canonical goal");
            insert.setString(6, "percent");
            insert.setString(7, "100");
            insert.setInt(8, 10);
            insert.setString(9, "ACTIVE");
            insert.executeUpdate();
        }
    }

    private void setTenant(Connection conn, UUID tenantId) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
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
