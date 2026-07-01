package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §8 — Verifies the runtime database role is sanad_runtime_app
 * with NOSUPERUSER, NOBYPASSRLS, and is NOT a table owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class TenantRuntimeRoleIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Runtime role verification: NOSUPERUSER, NOBYPASSRLS, non-owner")
    void runtimeRoleVerification() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String dbName = conn.getMetaData().getDatabaseProductName();

            if ("PostgreSQL".equals(dbName)) {
                // Verify current_user
                ResultSet rs = stmt.executeQuery("SELECT current_user");
                assertThat(rs.next()).isTrue();
                String currentUser = rs.getString(1);
                assertThat(currentUser)
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
            } else {
                // H2 local profile — skip role verification
                assertThat(dbName).isEqualTo("H2");
            }
        }
    }
}
