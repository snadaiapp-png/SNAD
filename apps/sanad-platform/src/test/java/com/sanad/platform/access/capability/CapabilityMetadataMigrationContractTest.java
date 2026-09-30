package com.sanad.platform.access.capability;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityMetadataMigrationContractTest {

    private static final List<String> CODES = List.of(
            "AUTHORIZATION.OVERRIDE.MANAGE",
            "AUTHORIZATION.RELATIONSHIP.MANAGE",
            "AUTHORIZATION.RESYNC",
            "AUTHORIZATION.BREAK_GLASS",
            "AUTHORIZATION.RECOVER",
            "AUTHORIZATION.PLATFORM.MANAGE");

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CapabilityMetadataMigrationContractTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for CapabilityMetadataMigrationContractTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void addsCapabilityMetadataAndSeedsAuthorizationCapabilities() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            for (String column : List.of(
                    "application", "module", "resource", "action", "risk_level",
                    "supports_scope", "system_protected")) {
                assertThat(columnExists(connection, column)).as(column).isTrue();
            }

            SQLException invalidRisk = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE access_capabilities
                           SET risk_level = 'EXTREME'
                         WHERE id = (SELECT id FROM access_capabilities LIMIT 1)
                        """)) {
                    ps.executeUpdate();
                }
            });
            assertThat(invalidRisk.getSQLState()).isEqualTo("23514");

            for (String code : CODES) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT status, system_protected, risk_level
                          FROM access_capabilities
                         WHERE code = ?
                        """)) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as(code).isTrue();
                        assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                        assertThat(rs.getBoolean("system_protected")).isTrue();
                        assertThat(rs.getString("risk_level")).isEqualTo("CRITICAL");
                        assertThat(rs.next()).isFalse();
                    }
                }
            }
        }
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static boolean columnExists(Connection connection, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                      FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'access_capabilities'
                       AND column_name = ?
                )
                """)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
