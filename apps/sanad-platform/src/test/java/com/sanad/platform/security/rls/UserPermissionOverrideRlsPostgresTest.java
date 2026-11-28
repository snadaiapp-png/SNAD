package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.sanad.platform.test.TenantGucDataSource;
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

class UserPermissionOverrideRlsPostgresTest {

    private static final UUID CONTROL_PLANE_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "UserPermissionOverrideRlsPostgresTest");
        Assumptions.assumeTrue(
                available,
                "PostgreSQL Direct is required for UserPermissionOverrideRlsPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void enforcesCapabilityWideDenyTenantIsolationAndFailClosedAuthorizationEvents() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
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
        UUID actor = UUID.randomUUID();
        UUID team = UUID.randomUUID();

        try (Connection connection = connect()) {
            // Deliberately first: before Task 1 implementation this must fail with
            // SQLSTATE 42P01 / relation "user_permission_overrides" does not exist.
            try (Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT 1 FROM user_permission_overrides LIMIT 0");
            }

            assertThat(regclass(connection, "subject_relationships")).isEqualTo("subject_relationships");
            assertThat(regclass(connection, "authorization_change_events")).isEqualTo("authorization_change_events");
            assertForceRls(connection, "user_permission_overrides");
            assertForceRls(connection, "subject_relationships");
            assertForceRls(connection, "authorization_change_events");

            insertTenant(connection, tenantA, "uac-a-" + tenantA.toString().substring(0, 8));
            insertTenant(connection, tenantB, "uac-b-" + tenantB.toString().substring(0, 8));
            UUID capabilityId = firstActiveCapability(connection);

            // G1 signed-context contract (V20261124_1/_2): the enforced policies
            // validate public.app_tenant_ctx_text() — a raw app.tenant_id GUC no
            // longer authorizes anything. Every tenant-scoped operation below
            // runs inside ONE signed-bound explicit transaction (bind → probe →
            // commit; expected denials abort → rollback), the exact per-
            // transaction protocol the production application executes. No RLS
            // bypass, no policy weakening; the 23514 CHECK-contract denial and
            // the 42501 policy denial both stay mandatory.
            SQLException scopedDeny = assertThrows(SQLException.class, () -> inTenantScope(
                    connection,
                    tenantA,
                    () -> {
                        insertOverride(connection, tenantA, userA, capabilityId, "DENY", "TEAM", team,
                                "test: scoped deny", actor);
                        return null;
                    }));
            assertThat(scopedDeny.getSQLState()).isEqualTo("23514");

            inTenantScope(connection, tenantA, () -> {
                insertOverride(connection, tenantA, userA, capabilityId, "ALLOW", "TEAM", team,
                        "test: scoped allow", actor);
                assertThat(countOverrides(connection, tenantA)).isEqualTo(1L);
                return null;
            });

            inTenantScope(connection, tenantB, () -> {
                insertOverride(connection, tenantB, userB, capabilityId, "ALLOW", "TEAM", team,
                        "test: tenant b allow", actor);
                return null;
            });

            assertThat(inTenantScope(connection, tenantA,
                    () -> countOverrides(connection, tenantB))).isZero();

            SQLException crossTenantInsert = assertThrows(SQLException.class, () -> inTenantScope(
                    connection,
                    tenantA,
                    () -> {
                        insertOverride(connection, tenantB, UUID.randomUUID(), capabilityId, "ALLOW", "TEAM", team,
                                "test: cross-tenant insert", actor);
                        return null;
                    }));
            assertThat(crossTenantInsert.getSQLState()).isEqualTo("42501");

            inTenantScope(connection, tenantA, () -> {
                insertAuthorizationEvent(connection, tenantA, "OVERRIDE_CREATED", actor);
                assertThat(countAuthorizationEvents(connection, "tenant_id = ?", tenantA)).isEqualTo(1L);
                assertThat(countAuthorizationEvents(connection, "tenant_id IS NULL")).isZero();
                return null;
            });
        }

        try (Connection controlPlane = connect()) {
            inTenantScope(controlPlane, CONTROL_PLANE_TENANT_ID, () -> {
                insertAuthorizationEvent(controlPlane, null, "PLATFORM_AUTHORIZATION_CHANGED", actor);
                assertThat(countAuthorizationEvents(controlPlane, "tenant_id IS NULL")).isEqualTo(1L);
                return null;
            });
        }

        // A fresh connection has no signed tenant context bound at all.
        // FORCE RLS must therefore hide every authorization-change event.
        try (Connection noContext = connect()) {
            assertThat(countAuthorizationEvents(noContext, "TRUE")).isZero();
        }

        try (Connection tenantContext = connect()) {
            assertThat(inTenantScope(tenantContext, tenantA,
                    () -> countAuthorizationEvents(tenantContext, "tenant_id = ?", tenantA))).isEqualTo(1L);
            assertThat(inTenantScope(tenantContext, tenantA,
                    () -> countAuthorizationEvents(tenantContext, "tenant_id IS NULL"))).isZero();
        }

        try (Connection inspection = connect()) {
            try (PreparedStatement ps = inspection.prepareStatement("""
                    SELECT p.polname,
                           pg_get_expr(p.polqual, p.polrelid) AS using_expr,
                           pg_get_expr(p.polwithcheck, p.polrelid) AS check_expr
                      FROM pg_policy p
                      JOIN pg_class c ON c.oid = p.polrelid
                     WHERE c.relname = 'authorization_change_events'
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("polname")).isEqualTo("authorization_change_events_isolation");
                    assertThat(rs.getString("using_expr")).isNotBlank();
                    assertThat(rs.getString("check_expr")).isNotBlank();
                    assertThat(rs.next()).isFalse();
                }
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static void insertTenant(Connection connection, UUID tenantId, String subdomain) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, "UAC Test " + subdomain);
            ps.setString(3, subdomain);
            ps.executeUpdate();
        }
    }

    private static UUID firstActiveCapability(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id
                  FROM access_capabilities
                 WHERE status = 'ACTIVE'
                 ORDER BY code
                 LIMIT 1
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("at least one ACTIVE capability must exist").isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** One tenant-scoped probe step. */
    private interface ScopeAction<T> {
        T run() throws SQLException;
    }

    /**
     * Execute the action inside ONE signed-bound explicit transaction
     * (V20261124_1/_2 contract): autocommit off → HMAC signed bind for the
     * given tenant → action → commit. Any failure (including the expected
     * 23514/42501 denials) rolls the aborted transaction back so the next
     * segment starts clean. The transaction-local signed context covers
     * exactly this segment — connections are never left in a bound state,
     * and no raw tenant GUC is written.
     */
    private static <T> T inTenantScope(Connection connection, UUID tenantId, ScopeAction<T> action)
            throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            TenantGucDataSource.bindSignedContext(connection, tenantId);
            T result = action.run();
            connection.commit();
            return result;
        } catch (Throwable failure) {
            try {
                connection.rollback();
            } catch (SQLException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void insertOverride(
            Connection connection,
            UUID tenantId,
            UUID userId,
            UUID capabilityId,
            String effect,
            String scopeType,
            UUID scopeReference,
            String reason,
            UUID createdBy) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO user_permission_overrides (
                    tenant_id, user_id, capability_id, effect,
                    scope_type, scope_reference, reason, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.setObject(3, capabilityId);
            ps.setString(4, effect);
            ps.setString(5, scopeType);
            ps.setObject(6, scopeReference);
            ps.setString(7, reason);
            ps.setObject(8, createdBy);
            ps.executeUpdate();
        }
    }

    private static long countOverrides(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM user_permission_overrides WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertAuthorizationEvent(
            Connection connection,
            UUID tenantId,
            String eventType,
            UUID actor) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO authorization_change_events (
                    id, tenant_id, event_type, actor_user_id, target_type,
                    target_id, payload, correlation_id, created_at
                ) VALUES (?, ?, ?, ?, 'USER', ?, '{}'::jsonb, ?, CURRENT_TIMESTAMP)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, eventType);
            ps.setObject(4, actor);
            ps.setObject(5, UUID.randomUUID());
            ps.setObject(6, UUID.randomUUID());
            ps.executeUpdate();
        }
    }

    private static long countAuthorizationEvents(
            Connection connection,
            String predicate,
            Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM authorization_change_events WHERE " + predicate)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
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
}
