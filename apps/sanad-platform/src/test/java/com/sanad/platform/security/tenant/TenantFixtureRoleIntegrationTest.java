package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3.2 §10 — Verifies the CI-only fixture role (sanad_fixture_ci).
 *
 * <p>This role has BYPASSRLS=true so it can insert test fixtures without
 * RLS restrictions. It is created ONLY in CI (not by Flyway migrations)
 * and is never used by the application runtime.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantFixtureRoleIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("Fixture role verification: NOSUPERUSER, BYPASSRLS, non-owner")
    void fixtureRoleVerification() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // Verify current_user — should be sanad_runtime_app (the runtime DS).
            // The fixture DS uses sanad_fixture_ci, but this test uses the runtime DS.
            // We verify the fixture role exists and has the correct flags.
            var rs = stmt.executeQuery(
                "SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolcanlogin " +
                "FROM pg_roles WHERE rolname = 'sanad_fixture_ci'");
            assertThat(rs.next())
                    .as("sanad_fixture_ci role must exist in CI").isTrue();
            assertThat(rs.getString("rolname")).isEqualTo("sanad_fixture_ci");
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
                "WHERE r.rolname = 'sanad_fixture_ci' " +
                "AND c.relname IN ('users','organizations','organization_memberships','roles','role_capabilities','user_role_assignments','refresh_tokens','password_reset_tokens')");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("sanad_fixture_ci must not own any tenant-owned table")
                    .isEqualTo(0);
        }
    }
}
