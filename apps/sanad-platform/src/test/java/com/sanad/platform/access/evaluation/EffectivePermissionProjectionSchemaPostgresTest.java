package com.sanad.platform.access.evaluation;

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
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EffectivePermissionProjectionSchemaPostgresTest {

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
                    "EffectivePermissionProjectionSchemaPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(
                available,
                "PostgreSQL Direct is required for EffectivePermissionProjectionSchemaPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void createsAuthorizationVersionAndTwoEffectProjectionWithCanonicalReason() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();

        flyway.clean();
        flyway.migrate();
        flyway.validate();

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID capability = UUID.randomUUID();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "users", "authorization_version")).isTrue();
            assertThat(columnType(connection, "users", "authorization_version")).isEqualTo("bigint");
            assertThat(columnDefault(connection, "users", "authorization_version")).contains("0");
            assertThat(columnNullable(connection, "users", "authorization_version")).isFalse();

            assertThat(regclass(connection, "effective_permission_projection"))
                    .isEqualTo("effective_permission_projection");
            assertForceRls(connection, "effective_permission_projection");
            assertUniqueNullsNotDistinctIndex(connection);
            assertProjectionChecks(connection);

            setTenant(connection, tenantA);
            insertProjection(connection, tenantA, userA, capability, "ALLOW", "ROLE", "ROLE_CAPABILITY_MATCH");
            assertThat(countProjectionRows(connection, tenantA)).isEqualTo(1L);

            // Phase 7: the projection is a two-effect explanation model. An
            // active DENY override row is representable with the canonical
            // direct-deny reason (deny dominance is enforced by the projection
            // service, not by this schema-level insert probe).
            insertProjection(connection, tenantA, UUID.randomUUID(), capability, "DENY", "ROLE",
                    "EXPLICIT_DIRECT_DENY");
            assertThat(countProjectionRows(connection, tenantA)).isEqualTo(2L);
            assertThat(reasonOfLastRow(connection, tenantA)).isEqualTo("EXPLICIT_DIRECT_DENY");

            SQLException invalidSource = assertThrows(SQLException.class, () -> insertProjection(
                    connection, tenantA, UUID.randomUUID(), capability, "ALLOW", "DELEGATION",
                    "ROLE_CAPABILITY_MATCH"));
            assertThat(invalidSource.getSQLState()).isEqualTo("23514");

            SQLException missingReason = assertThrows(SQLException.class, () -> insertProjection(
                    connection, tenantA, UUID.randomUUID(), capability, "ALLOW", "ROLE", null));
            assertThat(missingReason.getSQLState()).isEqualTo("23502");

            setTenant(connection, tenantB);
            insertProjection(connection, tenantB, userB, capability, "ALLOW", "OVERRIDE", "EXPLICIT_ALLOW_MATCH");

            setTenant(connection, tenantA);
            assertThat(countProjectionRows(connection, tenantB)).isZero();
            SQLException crossTenant = assertThrows(SQLException.class, () -> insertProjection(
                    connection, tenantB, UUID.randomUUID(), capability, "ALLOW", "BREAK_GLASS",
                    "EXPLICIT_ALLOW_MATCH"));
            assertThat(crossTenant.getSQLState()).isEqualTo("42501");
        }

        try (Connection noContext = connect()) {
            assertThat(countAllProjectionRows(noContext)).isZero();
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }

    private static void insertProjection(
            Connection connection,
            UUID tenantId,
            UUID userId,
            UUID capabilityId,
            String effect,
            String source,
            String reason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO effective_permission_projection (
                    id, tenant_id, user_id, capability_id, effect,
                    scope_type, scope_reference, source, matched_role_id,
                    authorization_version, computed_at, reason
                ) VALUES (?, ?, ?, ?, ?, 'TENANT_ALL', NULL, ?, NULL, 1, CURRENT_TIMESTAMP, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setObject(4, capabilityId);
            ps.setString(5, effect);
            ps.setString(6, source);
            ps.setString(7, reason);
            ps.executeUpdate();
        }
    }

    /** Reads the reason of the most recently computed row for a tenant (order by computed_at). */
    private static String reasonOfLastRow(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reason FROM effective_permission_projection WHERE tenant_id = ? "
                + "ORDER BY computed_at DESC, id LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static long countProjectionRows(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long countAllProjectionRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM effective_permission_projection")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String regclass(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT to_regclass('public.' || ?)::text")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                      FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = ?
                       AND column_name = ?
                )
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static String columnType(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private static String columnDefault(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private static boolean columnNullable(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT is_nullable = 'YES'
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getBoolean(1);
            }
        }
    }

    private static void assertForceRls(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT relrowsecurity, relforcerowsecurity
                  FROM pg_class
                 WHERE oid = to_regclass('public.' || ?)
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
            }
        }
    }

    private static void assertUniqueNullsNotDistinctIndex(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT indexdef
                  FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND tablename = 'effective_permission_projection'
                   AND indexname = 'uq_epp'
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String indexDef = rs.getString(1).toUpperCase();
                assertThat(indexDef).contains("UNIQUE");
                assertThat(indexDef).contains("NULLS NOT DISTINCT");
                assertThat(indexDef).contains("TENANT_ID");
                assertThat(indexDef).contains("USER_ID");
                assertThat(indexDef).contains("CAPABILITY_ID");
                assertThat(indexDef).contains("SCOPE_TYPE");
                assertThat(indexDef).contains("SCOPE_REFERENCE");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    private static void assertProjectionChecks(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT pg_get_constraintdef(c.oid) AS definition
                  FROM pg_constraint c
                  JOIN pg_class t ON t.oid = c.conrelid
                 WHERE t.relname = 'effective_permission_projection'
                   AND c.contype = 'c'
                """)) {
            StringBuilder definitions = new StringBuilder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.append(rs.getString("definition")).append('\n');
                }
            }
            String checks = definitions.toString();
            assertThat(checks).contains("ALLOW");
            assertThat(checks).contains("ROLE");
            assertThat(checks).contains("OVERRIDE");
            assertThat(checks).contains("BREAK_GLASS");
        }
    }
}
