package com.sanad.platform.security.tenant;

import com.sanad.platform.security.tenant.support.TenantMigrationOwnerDataSourceConfig;
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
 * Stage 04A.3.3 §4 — Proves migration_owner is subject to FORCE RLS.
 *
 * <p>Uses a DEDICATED migration-owner DataSource (not the runtime DS).
 * Verifies current_user = sanad_migration_owner, FORCE RLS on all tables,
 * and zero rows without tenant setting.</p>
 */
@SpringBootTest
@Import(TenantMigrationOwnerDataSourceConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantMigrationOwnerForceRlsIntegrationTest {

    @Autowired
    @Qualifier("tenantMigrationOwnerDataSource")
    private DataSource migrationOwnerDataSource;

    @Test
    @DisplayName("Migration owner: current_user = sanad_migration_owner")
    void migrationOwner_currentUser() throws Exception {
        PostgresTestUtil.assertPostgreSQL(migrationOwnerDataSource);

        try (var conn = migrationOwnerDataSource.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT current_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                    .as("Migration owner DS must connect as sanad_migration_owner")
                    .isEqualTo("sanad_migration_owner");
        }
    }

    @Test
    @DisplayName("Migration owner under FORCE RLS: zero rows without tenant setting")
    void migrationOwner_zeroRowsWithoutTenantSetting() throws Exception {
        PostgresTestUtil.assertPostgreSQL(migrationOwnerDataSource);

        try (var conn = migrationOwnerDataSource.getConnection(); var stmt = conn.createStatement()) {
            // Verify FORCE RLS is active on users table
            var rs = stmt.executeQuery(
                "SELECT relforcerowsecurity FROM pg_class WHERE relname = 'users'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1))
                    .as("FORCE RLS must be true (V20 restored)").isTrue();

            // Without tenant setting, all tenant-owned tables return 0 rows
            rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("Migration owner must see 0 rows without tenant setting (FORCE RLS)")
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Migration owner: FORCE RLS on all 8 tenant-owned tables")
    void migrationOwner_allTablesForceRls() throws Exception {
        PostgresTestUtil.assertPostgreSQL(migrationOwnerDataSource);

        String[] tables = {"organizations", "organization_memberships", "users", "roles",
                "role_capabilities", "user_role_assignments", "refresh_tokens", "password_reset_tokens"};

        try (var conn = migrationOwnerDataSource.getConnection(); var stmt = conn.createStatement()) {
            for (String table : tables) {
                var rs = stmt.executeQuery(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = '" + table + "'");
                assertThat(rs.next()).as("Table %s must exist", table).isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("Table %s must have RLS enabled", table).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("Table %s must have FORCE RLS", table).isTrue();
            }
        }
    }
}
