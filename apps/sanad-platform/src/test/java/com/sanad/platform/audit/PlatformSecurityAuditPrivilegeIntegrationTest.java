package com.sanad.platform.audit;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Stage 05A.2.9.1 §13 — Verifies the actual PostgreSQL privilege
 * matrix on {@code platform_security_audit_events} for both the
 * runtime role ({@code sanad_runtime_app}) and the fixture role
 * ({@code sanad_fixture_ci}).
 *
 * <p>Two verification layers:</p>
 * <ol>
 *   <li><b>Metadata layer</b> — uses
 *       {@code has_table_privilege(role, table, privilege)} to check
 *       the granted privileges.</li>
 *   <li><b>Behavioral layer</b> — actually attempts each operation
 *       (INSERT, SELECT, UPDATE, DELETE, TRUNCATE) using a connection
 *       authenticated as each role, and asserts the operation
 *       succeeds or fails as expected.</li>
 * </ol>
 *
 * <p>Required matrix:</p>
 * <pre>
 *   Runtime role (sanad_runtime_app):
 *     INSERT   → PASS
 *     SELECT   → DENIED
 *     UPDATE   → DENIED
 *     DELETE   → DENIED
 *     TRUNCATE → DENIED
 *
 *   Fixture role (sanad_fixture_ci):
 *     INSERT   → DENIED
 *     SELECT   → PASS
 *     UPDATE   → DENIED
 *     DELETE   → DENIED
 *     TRUNCATE → DENIED
 * </pre>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class PlatformSecurityAuditPrivilegeIntegrationTest {

    private static final String TABLE = "platform_security_audit_events";

    @Autowired
    @Qualifier("tenantRuntimeDataSource")
    private DataSource runtimeDataSource;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    // === Metadata layer: has_table_privilege ===

    @Test
    @DisplayName("runtimeRole_privilegeMatrix_metadata: INSERT only")
    void runtimeRole_privilegeMatrix_metadata() throws Exception {
        // Use the fixture connection (BYPASSRLS) to query has_table_privilege
        // for the runtime role.
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThat(hasTablePrivilege(stmt, "sanad_runtime_app", "INSERT"))
                    .as("sanad_runtime_app must have INSERT on %s", TABLE).isTrue();
            assertThat(hasTablePrivilege(stmt, "sanad_runtime_app", "SELECT"))
                    .as("sanad_runtime_app must NOT have SELECT on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_runtime_app", "UPDATE"))
                    .as("sanad_runtime_app must NOT have UPDATE on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_runtime_app", "DELETE"))
                    .as("sanad_runtime_app must NOT have DELETE on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_runtime_app", "TRUNCATE"))
                    .as("sanad_runtime_app must NOT have TRUNCATE on %s", TABLE).isFalse();
        }
    }

    @Test
    @DisplayName("fixtureRole_privilegeMatrix_metadata: SELECT only")
    void fixtureRole_privilegeMatrix_metadata() throws Exception {
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThat(hasTablePrivilege(stmt, "sanad_fixture_ci", "SELECT"))
                    .as("sanad_fixture_ci must have SELECT on %s", TABLE).isTrue();
            assertThat(hasTablePrivilege(stmt, "sanad_fixture_ci", "INSERT"))
                    .as("sanad_fixture_ci must NOT have INSERT on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_fixture_ci", "UPDATE"))
                    .as("sanad_fixture_ci must NOT have UPDATE on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_fixture_ci", "DELETE"))
                    .as("sanad_fixture_ci must NOT have DELETE on %s", TABLE).isFalse();
            assertThat(hasTablePrivilege(stmt, "sanad_fixture_ci", "TRUNCATE"))
                    .as("sanad_fixture_ci must NOT have TRUNCATE on %s", TABLE).isFalse();
        }
    }

    // === Behavioral layer: actual operations ===

    @Test
    @DisplayName("runtimeRole_insert_passes: INSERT succeeds")
    void runtimeRole_insert_passes() {
        // The runtime role can INSERT (the platform denial audit service
        // relies on this).
        String sql = "INSERT INTO " + TABLE + " "
                + "(id, occurred_at, recorded_at, request_id, path, http_method, "
                + " source_ip, user_agent, failure_category, error_code, "
                + " token_fingerprint, metadata, created_at) "
                + "VALUES (?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (Connection conn = runtimeDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "test-req-id");
            ps.setString(3, "/test");
            ps.setString(4, "POST");
            ps.setString(5, "127.0.0.1");
            ps.setString(6, "test-agent");
            ps.setString(7, "MISSING_JWT");
            ps.setString(8, "SANAD-AUTH-001");
            ps.setString(9, null);
            ps.setString(10, null);
            int rows = ps.executeUpdate();
            assertThat(rows).as("runtime INSERT must succeed").isEqualTo(1);
        } catch (SQLException e) {
            fail("runtime INSERT must succeed, but got: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("runtimeRole_select_denied: SELECT fails")
    void runtimeRole_select_denied() {
        try (Connection conn = runtimeDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE);
            fail("runtime SELECT must be DENIED, but it succeeded");
        } catch (SQLException e) {
            // Expected — permission denied
            assertThat(e.getMessage().toLowerCase())
                    .as("runtime SELECT must fail with permission denied")
                    .containsAnyOf("permission denied", "must not appear", "denied");
        }
    }

    @Test
    @DisplayName("runtimeRole_update_denied: UPDATE fails")
    void runtimeRole_update_denied() {
        try (Connection conn = runtimeDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE " + TABLE + " SET failure_category = 'X' WHERE 1=0");
            fail("runtime UPDATE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("runtimeRole_delete_denied: DELETE fails")
    void runtimeRole_delete_denied() {
        try (Connection conn = runtimeDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM " + TABLE + " WHERE 1=0");
            fail("runtime DELETE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("runtimeRole_truncate_denied: TRUNCATE fails")
    void runtimeRole_truncate_denied() {
        try (Connection conn = runtimeDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE " + TABLE);
            fail("runtime TRUNCATE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("fixtureRole_select_passes: SELECT succeeds")
    void fixtureRole_select_passes() {
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            rs.next();
            // SELECT must succeed — we don't care about the count
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(0);
        } catch (SQLException e) {
            fail("fixture SELECT must succeed, but got: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("fixtureRole_insert_denied: INSERT fails")
    void fixtureRole_insert_denied() {
        String sql = "INSERT INTO " + TABLE + " "
                + "(id, occurred_at, recorded_at, failure_category, created_at) "
                + "VALUES (?, NOW(), NOW(), 'MISSING_JWT', NOW())";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.executeUpdate();
            fail("fixture INSERT must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("fixtureRole_update_denied: UPDATE fails")
    void fixtureRole_update_denied() {
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE " + TABLE + " SET failure_category = 'X' WHERE 1=0");
            fail("fixture UPDATE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("fixtureRole_delete_denied: DELETE fails")
    void fixtureRole_delete_denied() {
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM " + TABLE + " WHERE 1=0");
            fail("fixture DELETE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    @Test
    @DisplayName("fixtureRole_truncate_denied: TRUNCATE fails")
    void fixtureRole_truncate_denied() {
        try (Connection conn = fixtureDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE " + TABLE);
            fail("fixture TRUNCATE must be DENIED, but it succeeded");
        } catch (SQLException e) {
            assertThat(e.getMessage().toLowerCase()).containsAnyOf("permission denied", "denied");
        }
    }

    // === Helper ===

    private boolean hasTablePrivilege(Statement stmt, String role, String privilege) throws SQLException {
        String sql = String.format(
                "SELECT has_table_privilege('%s', '%s', '%s')",
                role, TABLE, privilege);
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        }
    }
}
