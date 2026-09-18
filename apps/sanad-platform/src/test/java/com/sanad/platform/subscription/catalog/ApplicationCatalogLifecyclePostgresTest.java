package com.sanad.platform.subscription.catalog;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * PATH-B G1-B — PostgreSQL Direct migration acceptance for the application
 * catalog status lifecycle (design spec §12.4).
 *
 * <p>Proves on a real PostgreSQL 17 chain that the CHECK widening in
 * V20260914_1 is purely additive: legacy ACTIVE/INACTIVE/DEPRECATED rows are
 * preserved verbatim (no rewrite), DRAFT/ARCHIVED become insertable, unknown
 * statuses are still rejected by the database (SQLSTATE 23514), and an
 * upgrade simulation from the pre-widening head (20260912.6) keeps legacy
 * data byte-identical.
 */
class ApplicationCatalogLifecyclePostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    private static final String PRE_WIDENING_HEAD = "20260912.6";
    private static final String WIDENING_HEAD = "20260914.1";

    private static DriverManagerDataSource dataSource;
    private Connection connection;

    @BeforeAll
    static void migrateFreshPostgreSqlChain() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ApplicationCatalogLifecyclePostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is required for G1-B catalog lifecycle acceptance.");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
    }

    @AfterAll
    static void releaseDataSource() {
        // DriverManagerDataSource opens a connection per use; nothing pooled to release.
    }

    @BeforeEach
    void openTransaction() throws SQLException {
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    private static final List<String> LEGACY_STATUSES = List.of("ACTIVE", "INACTIVE", "DEPRECATED");

    private void insertApplication(Connection target, String code, String status) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "INSERT INTO applications (id, code, name, category, status, display_order, "
                        + "provisioning_mode, supported_countries, dependencies, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'MODULE', ?, 10, 'IMMEDIATE', '[\"GLOBAL\"]'::jsonb, '[]'::jsonb, NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, code);
            ps.setString(3, "Application " + code);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("fresh Flyway chain includes the application lifecycle widening migration")
    void freshChainIncludesApplicationLifecycleMigration() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version = ?")) {
            ps.setString(1, WIDENING_HEAD);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("widened CHECK admits every governed lifecycle value including DRAFT and ARCHIVED")
    void widenedCheckAdmitsDraftAndArchived() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_applications_status'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            String definition = rs.getString(1);
            for (String status : List.of("ACTIVE", "INACTIVE", "DEPRECATED", "DRAFT", "ARCHIVED")) {
                assertThat(definition).as("constraint must admit " + status).contains(status);
            }
        }
    }

    @Test
    @DisplayName("legacy statuses remain insertable and round-trip verbatim (no rewrite)")
    void legacyStatusesRemainInsertable() throws SQLException {
        for (String status : LEGACY_STATUSES) {
            insertApplication(connection, "LEGACY_" + status, status);
        }
        for (String status : LEGACY_STATUSES) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT status FROM applications WHERE code = ?")) {
                ps.setString(1, "LEGACY_" + status);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo(status);
                }
            }
        }
    }

    @Test
    @DisplayName("DRAFT and ARCHIVED rows are insertable after the widening")
    void draftAndArchivedAreInsertable() throws SQLException {
        insertApplication(connection, "G1B_DRAFT", "DRAFT");
        insertApplication(connection, "G1B_ARCHIVED", "ARCHIVED");

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM applications WHERE code IN ('G1B_DRAFT', 'G1B_ARCHIVED') ORDER BY code");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("ARCHIVED");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("DRAFT");
        }
    }

    @Test
    @DisplayName("unknown lifecycle status is still rejected by the database (SQLSTATE 23514)")
    void unknownStatusRejectedByCheck() throws SQLException {
        insertApplication(connection, "G1B_BASE", "ACTIVE");
        connection.commit();
        Connection second = dataSource.getConnection();
        second.setAutoCommit(true);
        try {
            Throwable thrown = catchThrowable(() -> insertApplication(second, "G1B_DELETED", "DELETED"));
            assertThat(thrown).isInstanceOf(SQLException.class);
            assertThat(((SQLException) thrown).getSQLState()).isEqualTo("23514");
        } finally {
            // remove the committed base row so later tests stay deterministic
            JdbcTemplate cleanup = new JdbcTemplate(dataSource);
            cleanup.update("DELETE FROM applications WHERE code = 'G1B_BASE'");
            second.close();
        }
    }

    @Test
    @DisplayName("upgrade simulation: widening preserves pre-widening rows byte-identically")
    void upgradeSimulationPreservesLegacyRows() throws SQLException {
        Flyway preWidening = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .target(MigrationVersion.fromVersion(PRE_WIDENING_HEAD))
                .load();
        preWidening.clean();
        preWidening.migrate();

        Connection writer = dataSource.getConnection();
        writer.setAutoCommit(true);
        try (PreparedStatement ps = writer.prepareStatement(
                "INSERT INTO applications (id, code, name, category, status, version, display_order, "
                        + "icon_key, provisioning_mode, supported_countries, dependencies, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'MODULE', ?, '1.0', 10, 'erp', 'IMMEDIATE', "
                        + "'[\"SA\"]'::jsonb, '[]'::jsonb, NOW(), NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "UPGSIM_LEGACY");
            ps.setString(3, "Upgrade simulation application");
            ps.setString(4, "DEPRECATED");
            ps.executeUpdate();
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "UPGSIM_ACTIVE");
            ps.setString(3, "Upgrade simulation active application");
            ps.setString(4, "ACTIVE");
            ps.executeUpdate();
        } finally {
            writer.close();
        }

        Flyway widening = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        widening.migrate();
        widening.validate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var legacyRow = jdbc.queryForMap(
                "SELECT status, name, version, icon_key FROM applications WHERE code = 'UPGSIM_LEGACY'");
        assertThat(legacyRow.get("status")).isEqualTo("DEPRECATED");
        assertThat(legacyRow.get("name")).isEqualTo("Upgrade simulation application");
        assertThat(legacyRow.get("version")).isEqualTo("1.0");
        assertThat(legacyRow.get("icon_key")).isEqualTo("erp");
        var activeRow = jdbc.queryForMap(
                "SELECT status FROM applications WHERE code = 'UPGSIM_ACTIVE'");
        assertThat(activeRow.get("status")).isEqualTo("ACTIVE");

        // Post-upgrade the widened values are accepted.
        Connection prober = dataSource.getConnection();
        prober.setAutoCommit(true);
        try {
            insertApplication(prober, "UPGSIM_ARCHIVED_PROBE", "ARCHIVED");
        } finally {
            prober.close();
        }

        jdbc.update("DELETE FROM applications WHERE code LIKE 'UPGSIM_%'");
    }
}
