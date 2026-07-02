package com.sanad.platform.idempotency;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.3 — Verifies the three DataSource identities used by the
 * idempotency runtime path under the {@code tenant-postgres-test} profile.
 *
 * <p>Idempotency in production runs against three distinct PostgreSQL
 * connection pools, each connecting as a different database role with
 * different RLS privileges:</p>
 * <ul>
 *   <li><b>runtime</b> DataSource (primary, {@code sanad_runtime_app}) —
 *       subject to FORCE RLS, used by the idempotent transaction executor,
 *       JPA EntityManagerFactory, and PostgresIdempotencyReservationStore.</li>
 *   <li><b>fixture</b> DataSource ({@code sanad_fixture_ci}) — CI-only
 *       role with BYPASSRLS for fixture setup and cleanup. Never used
 *       in production.</li>
 *   <li><b>migration owner</b> DataSource ({@code sanad_migration_owner}) —
 *       owns the schema, subject to FORCE RLS for ordinary DML, used only
 *       for migration and verification.</li>
 * </ul>
 *
 * <p>This test asserts that each DataSource connects as the expected
 * {@code current_user} and that all three DataSources are distinct bean
 * instances (no accidental aliasing that would route runtime queries
 * through the BYPASSRLS fixture pool, or vice versa).</p>
 *
 * <p>Stage 05A.1 §13 — Each assertion uses {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class,
        TenantMigrationOwnerDataSourceConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyRuntimeDataSourceIntegrationTest {

    /** Runtime DataSource — subject to FORCE RLS (sanad_runtime_app, no BYPASSRLS). */
    @Autowired
    private DataSource runtimeDataSource;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    @Autowired
    @Qualifier("tenantMigrationOwnerDataSource")
    private DataSource migrationOwnerDataSource;

    /**
     * Returns the PostgreSQL {@code current_user} for the given DataSource.
     */
    private static String currentUser(DataSource ds) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("runtimeDataSource_currentUserIsSanadRuntimeApp: primary DataSource connects as sanad_runtime_app")
    void runtimeDataSource_currentUserIsSanadRuntimeApp() throws Exception {
        assertThat(currentUser(runtimeDataSource))
                .as("runtime DataSource (primary) must connect as sanad_runtime_app "
                        + "(NOSUPERUSER, NOBYPASSRLS — subject to FORCE RLS)")
                .isEqualTo("sanad_runtime_app");
    }

    @Test
    @DisplayName("fixtureDataSource_currentUserIsSanadFixtureCi: fixture DataSource connects as sanad_fixture_ci")
    void fixtureDataSource_currentUserIsSanadFixtureCi() throws Exception {
        assertThat(currentUser(fixtureDataSource))
                .as("fixture DataSource must connect as sanad_fixture_ci "
                        + "(CI-only role with BYPASSRLS — never used in production)")
                .isEqualTo("sanad_fixture_ci");
    }

    @Test
    @DisplayName("allThreeDataSourcesAreDistinct: runtime, fixture, and migration-owner are separate pools")
    void allThreeDataSourcesAreDistinct() {
        assertThat(runtimeDataSource)
                .as("runtime DataSource must be a distinct bean from fixture DataSource")
                .isNotSameAs(fixtureDataSource);
        assertThat(runtimeDataSource)
                .as("runtime DataSource must be a distinct bean from migration-owner DataSource")
                .isNotSameAs(migrationOwnerDataSource);
        assertThat(fixtureDataSource)
                .as("fixture DataSource must be a distinct bean from migration-owner DataSource")
                .isNotSameAs(migrationOwnerDataSource);
    }

    @Test
    @DisplayName("allThreeDataSourcesConnectAsDistinctUsers: three distinct current_user values")
    void allThreeDataSourcesConnectAsDistinctUsers() throws Exception {
        String runtimeUser = currentUser(runtimeDataSource);
        String fixtureUser = currentUser(fixtureDataSource);
        String migrationOwnerUser = currentUser(migrationOwnerDataSource);

        assertThat(runtimeUser).isEqualTo("sanad_runtime_app");
        assertThat(fixtureUser).isEqualTo("sanad_fixture_ci");
        assertThat(migrationOwnerUser).isEqualTo("sanad_migration_owner");

        // All three must be distinct — no aliasing of pools.
        assertThat(runtimeUser).isNotEqualTo(fixtureUser);
        assertThat(runtimeUser).isNotEqualTo(migrationOwnerUser);
        assertThat(fixtureUser).isNotEqualTo(migrationOwnerUser);
    }
}
