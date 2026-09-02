package com.sanad.platform.hr.compliance;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / WS3 / Task 1 RED contract for Country Pack persistence.
 * PostgreSQL Direct only.
 */
class HrCountryPackLifecycleIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
            // Assumption below records the environment limitation without substituting H2.
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
    }

    @Test
    void countryPackSchemaAndDraftGccShellsExist() throws Exception {
        assertThat(tableExists("hr_country_packs")).isTrue();
        assertThat(tableExists("hr_compliance_rules")).isTrue();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT country_code, status FROM hr_country_packs " +
                        "WHERE pack_code = 'HR_FOUNDATION' ORDER BY country_code")) {
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat(rs.getString("status")).isEqualTo("DRAFT");
                    assertThat(rs.getString("country_code"))
                            .isIn("SA", "AE", "QA", "BH", "KW", "OM");
                }
                assertThat(count).isEqualTo(6);
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM hr_compliance_rules")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).as("DRAFT GCC shells must not fabricate legal rules").isZero();
            }
        }
    }

    @Test
    void countryPackVersionsCannotOverlapWhileActive() throws Exception {
        insertPack("SA", "OVERLAP_TEST", UUID.randomUUID().toString(), "ACTIVE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> insertPack(
                "SA", "OVERLAP_TEST", UUID.randomUUID().toString(), "CERTIFIED",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31)))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23P01"));
    }

    private void insertPack(String countryCode, String packCode, String version, String status,
                            LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs " +
                        "(country_code, pack_code, pack_version, status, effective_from, effective_to) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, countryCode);
            ps.setString(2, packCode);
            ps.setString(3, version);
            ps.setString(4, status);
            ps.setObject(5, from);
            ps.setObject(6, to);
            ps.executeUpdate();
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = ?)")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
