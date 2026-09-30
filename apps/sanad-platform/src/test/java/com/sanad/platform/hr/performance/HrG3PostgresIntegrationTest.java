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
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G3 PostgreSQL Direct RED contract.
 *
 * <p>This test intentionally lands before the G3 migrations. The first run
 * must fail because the performance tables/constraints do not exist yet.
 * It runs only against host-native PostgreSQL; Docker/Testcontainers are not
 * part of this contract.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG3PostgresIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static final UUID TENANT_A = UUID.fromString("33333333-3333-4333-8333-333333333331");
    private static final UUID TENANT_B = UUID.fromString("44444444-4444-4444-8444-444444444441");

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
                    .as("RED: G3 must create hr_performance_goals before tenant isolation can be certified")
                    .isTrue();

            UUID personId = firstUuid(conn, "SELECT id FROM hr_people WHERE tenant_id = ? ORDER BY id LIMIT 1", TENANT_A);
            UUID employmentId = firstUuid(conn, "SELECT id FROM hr_employees WHERE tenant_id = ? ORDER BY id LIMIT 1", TENANT_A);
            Assumptions.assumeTrue(personId != null && employmentId != null,
                    "Canonical HR seed must provide tenant A person/employment");

            UUID goalId = UUID.randomUUID();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT set_config('app.tenant_id', '" + TENANT_A + "', false)");
            }
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO hr_performance_goals " +
                    "(id, tenant_id, person_id, employment_id, title, metric, target_value, progress, status, starts_on, ends_on) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, CURRENT_DATE + 30)")) {
                insert.setObject(1, goalId);
                insert.setObject(2, TENANT_A);
                insert.setObject(3, personId);
                insert.setObject(4, employmentId);
                insert.setString(5, "G3 tenant isolation RED goal");
                insert.setString(6, "percent");
                insert.setString(7, "100");
                insert.setInt(8, 10);
                insert.setString(9, "ACTIVE");
                insert.executeUpdate();
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT set_config('app.tenant_id', '" + TENANT_B + "', false)");
            }
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
                    .as("RED: G3 goal persistence must exist before canonical-employment enforcement can be certified")
                    .isTrue();

            String fkDefinition = constraintDefinitionContaining(
                    conn,
                    "hr_performance_goals",
                    "employment_id"
            );
            assertThat(fkDefinition)
                    .as("Performance goals must have a database constraint bound to canonical employment_id")
                    .isNotBlank()
                    .containsIgnoringCase("FOREIGN KEY")
                    .containsIgnoringCase("employment_id");
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

    private UUID firstUuid(Connection conn, String sql, UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private String constraintDefinitionContaining(Connection conn, String tableName, String token) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_get_constraintdef(con.oid) " +
                "FROM pg_constraint con " +
                "JOIN pg_class rel ON rel.oid = con.conrelid " +
                "WHERE rel.relname = ? AND pg_get_constraintdef(con.oid) ILIKE ? " +
                "ORDER BY con.conname LIMIT 1")) {
            ps.setString(1, tableName);
            ps.setString(2, "%" + token + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }
}
