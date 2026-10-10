package com.sanad.platform.access.override;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionOverrideJdbcRepositoryPostgresTest {
    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgres() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "UserPermissionOverrideJdbcRepositoryPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(
                available,
                "PostgreSQL Direct is required for UserPermissionOverrideJdbcRepositoryPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void insertBindsInstantValidityWindowWithoutDriverTypeInferenceFailure() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            UUID tenantId = firstUuid(connection, "SELECT id FROM tenants ORDER BY id LIMIT 1");
            setTenant(connection, tenantId);
            UUID userId = firstUuid(
                    connection,
                    "SELECT id FROM users WHERE tenant_id = '" + tenantId + "' ORDER BY id LIMIT 1");
            UUID capabilityId = firstUuid(
                    connection,
                    "SELECT id FROM access_capabilities WHERE status = 'ACTIVE' ORDER BY code LIMIT 1");

            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            UserPermissionOverrideJdbcRepository repository =
                    new UserPermissionOverrideJdbcRepository(jdbc);

            Instant validFrom = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant validUntil = validFrom.plus(30, ChronoUnit.DAYS);
            UUID id = UUID.randomUUID();

            repository.insert(new UserPermissionOverride(
                    id,
                    tenantId,
                    userId,
                    capabilityId,
                    "ALLOW",
                    "TENANT_ALL",
                    null,
                    "postgres temporal binding proof",
                    validFrom,
                    validUntil,
                    userId,
                    0));

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT valid_from, valid_until FROM user_permission_overrides WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getTimestamp("valid_from").toInstant()).isEqualTo(validFrom);
                    assertThat(rs.getTimestamp("valid_until").toInstant()).isEqualTo(validUntil);
                }
            }
        }
    }

    @Test
    void insertBindsNullValidUntilExplicitly() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            UUID tenantId = firstUuid(connection, "SELECT id FROM tenants ORDER BY id LIMIT 1");
            setTenant(connection, tenantId);
            UUID userId = firstUuid(
                    connection,
                    "SELECT id FROM users WHERE tenant_id = '" + tenantId + "' ORDER BY id LIMIT 1");
            UUID capabilityId = firstUuid(
                    connection,
                    "SELECT id FROM access_capabilities WHERE status = 'ACTIVE' ORDER BY code LIMIT 1");

            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            UserPermissionOverrideJdbcRepository repository =
                    new UserPermissionOverrideJdbcRepository(jdbc);

            Instant validFrom = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            UUID id = UUID.randomUUID();

            repository.insert(new UserPermissionOverride(
                    id,
                    tenantId,
                    userId,
                    capabilityId,
                    "ALLOW",
                    "TENANT_ALL",
                    null,
                    "postgres null validity proof",
                    validFrom,
                    null,
                    userId,
                    0));

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_permission_overrides "
                            + "WHERE tenant_id = ? AND id = ? AND valid_until IS NULL",
                    Integer.class,
                    tenantId,
                    id);
            assertThat(count).isEqualTo(1);
        }
    }

    private static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static void setTenant(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }

    private static UUID firstUuid(Connection connection, String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return rs.getObject(1, UUID.class);
        }
    }
}
