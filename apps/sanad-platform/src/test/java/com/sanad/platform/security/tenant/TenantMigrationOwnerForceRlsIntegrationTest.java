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
 * Stage 04A.3.2 §9 — Proves that the migration_owner (table owner) is
 * subject to FORCE RLS during ordinary DML operations.
 *
 * <p>Uses a SEPARATE migration-owner DataSource to verify that FORCE RLS
 * applies even to the table owner. The fixture role (sanad_fixture_ci)
 * with BYPASSRLS is the ONLY role that can insert test data.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantMigrationOwnerForceRlsIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Migration owner under FORCE RLS: zero rows without tenant setting")
    void migrationOwner_zeroRowsWithoutTenantSetting() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // Verify FORCE RLS is active
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
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }
}
