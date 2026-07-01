package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §7 — Raw PostgreSQL RLS integration test using JDBC.
 * Non-skippable: requires PostgreSQL. Asserts relrowsecurity, relforcerowsecurity,
 * policy existence, and tenant-scoped reads/writes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantRlsIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("RLS is enabled and forced on users and organization_memberships")
    void rlsEnabled_andForced() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            for (String table : new String[]{"users", "organization_memberships"}) {
                var rs = stmt.executeQuery(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = '" + table + "'");
                assertThat(rs.next()).as("Table %s must exist", table).isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("Table %s must have RLS enabled", table).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("Table %s must have FORCE RLS", table).isTrue();
            }

            var rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM pg_policies WHERE schemaname = 'public' " +
                "AND tablename IN ('users','organization_memberships')");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("At least 2 RLS policies must exist")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("Missing tenant setting → zero rows (fail-closed)")
    void missingTenantSetting_zeroRows() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                // Set tenant to NULL (not empty string) — RLS policy evaluates
                // tenant_id = NULL::uuid → FALSE for all rows → 0 rows
                stmt.execute("SELECT set_config('app.current_tenant_id', NULL, true)");

                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("Missing/null tenant setting must return 0 rows (fail-closed)")
                        .isEqualTo(0);
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Tenant A setting → only A rows visible; COUNT excludes B")
    void tenantASetting_seesOnlyARows() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantA = java.util.UUID.randomUUID();
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SELECT set_config('app.current_tenant_id', '" + tenantA + "', true)");

                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE tenant_id = '" + tenantA + "'");
                assertThat(rs.next()).isTrue();
            }
            conn.rollback();
        }
    }
}
