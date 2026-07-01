package com.sanad.platform.security.tenant;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3.3 §5 — Verifies the CI-only fixture role via the fixture DataSource.
 *
 * <p>Uses @Qualifier("tenantFixtureDataSource") to connect as sanad_fixture_ci.
 * Verifies BYPASSRLS=true, can create/read/cleanup fixtures.</p>
 */
@SpringBootTest
@Import(TenantFixtureDataSourceConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantFixtureRoleIntegrationTest {

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    @Test
    @DisplayName("Fixture DS: current_user = sanad_fixture_ci")
    void fixtureRole_currentUser() throws Exception {
        PostgresTestUtil.assertPostgreSQL(fixtureDataSource);

        try (var conn = fixtureDataSource.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT current_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                    .as("Fixture DS must connect as sanad_fixture_ci")
                    .isEqualTo("sanad_fixture_ci");
        }
    }

    @Test
    @DisplayName("Fixture role: NOSUPERUSER, BYPASSRLS, non-owner")
    void fixtureRoleVerification() throws Exception {
        PostgresTestUtil.assertPostgreSQL(fixtureDataSource);

        try (var conn = fixtureDataSource.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                "SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolcanlogin " +
                "FROM pg_roles WHERE rolname = current_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper")).as("rolsuper must be false").isFalse();
            assertThat(rs.getBoolean("rolcreatedb")).as("rolcreatedb must be false").isFalse();
            assertThat(rs.getBoolean("rolcreaterole")).as("rolcreaterole must be false").isFalse();
            assertThat(rs.getBoolean("rolreplication")).as("rolreplication must be false").isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).as("rolbypassrls must be true (fixture bypass)").isTrue();
            assertThat(rs.getBoolean("rolcanlogin")).as("rolcanlogin must be true").isTrue();

            // Verify fixture role is NOT table owner
            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM pg_class c " +
                "JOIN pg_roles r ON r.oid = c.relowner " +
                "WHERE r.rolname = current_user " +
                "AND c.relname IN ('users','organizations','organization_memberships','roles','role_capabilities','user_role_assignments','refresh_tokens','password_reset_tokens')");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("sanad_fixture_ci must not own any tenant-owned table")
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Fixture DS: can create and read test data (BYPASSRLS)")
    void fixtureDs_canCreateAndRead() throws Exception {
        PostgresTestUtil.assertPostgreSQL(fixtureDataSource);

        UUID tenantId = UUID.randomUUID();
        try (var conn = fixtureDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                // Create a tenant (no RLS on tenants table)
                stmt.execute("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES ('" + tenantId + "', 'Fixture Test', 'ft-" + tenantId + "', 'ACTIVE', NOW(), NOW())");

                // Create a user (RLS-protected, but fixture has BYPASSRLS)
                UUID userId = UUID.randomUUID();
                stmt.execute("INSERT INTO users (id, tenant_id, email, display_name, status, " +
                        "password_hash, session_version, created_at, updated_at) " +
                        "VALUES ('" + userId + "', '" + tenantId + "', 'fixture@test.com', 'FT', 'ACTIVE', " +
                        "'$2a$10$dummy', 0, NOW(), NOW())");

                // Read it back — fixture DS with BYPASSRLS can see all rows
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE tenant_id = '" + tenantId + "'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("Fixture DS must be able to read created user")
                        .isEqualTo(1);

                // Cleanup
                stmt.execute("DELETE FROM users WHERE id = '" + userId + "'");
                stmt.execute("DELETE FROM tenants WHERE id = '" + tenantId + "'");
            }
            conn.commit();
        }
    }
}
