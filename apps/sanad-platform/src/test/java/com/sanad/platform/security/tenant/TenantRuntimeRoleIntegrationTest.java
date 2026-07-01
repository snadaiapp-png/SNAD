package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §8 — Runtime role verification. Non-skippable PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantRuntimeRoleIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Runtime role: sanad_runtime_app with NOSUPERUSER, NOBYPASSRLS, non-owner")
    @Transactional
    void runtimeRoleVerification() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // Verify current_user
            var rs = stmt.executeQuery("SELECT current_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                    .as("Runtime user must be sanad_runtime_app")
                    .isEqualTo("sanad_runtime_app");

            // Verify role flags
            rs = stmt.executeQuery(
                "SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolcanlogin " +
                "FROM pg_roles WHERE rolname = current_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper")).as("rolsuper must be false").isFalse();
            assertThat(rs.getBoolean("rolcreatedb")).as("rolcreatedb must be false").isFalse();
            assertThat(rs.getBoolean("rolcreaterole")).as("rolcreaterole must be false").isFalse();
            assertThat(rs.getBoolean("rolreplication")).as("rolreplication must be false").isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).as("rolbypassrls must be false").isFalse();
            assertThat(rs.getBoolean("rolcanlogin")).as("rolcanlogin must be true").isTrue();

            // Verify runtime role is NOT table owner
            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM pg_class c " +
                "JOIN pg_roles r ON r.oid = c.relowner " +
                "WHERE r.rolname = 'sanad_runtime_app' " +
                "AND c.relname IN ('users','organizations','organization_memberships','roles','role_capabilities','user_role_assignments','refresh_tokens','password_reset_tokens')");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("sanad_runtime_app must not own any tenant-owned table")
                    .isEqualTo(0);
        }
    }
}
