package com.sanad.platform.idempotency;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §14 — Verifies Row-Level Security on idempotency_records.
 *
 * <p>Inserts records for two tenants via the fixture DataSource, then
 * queries via the runtime DataSource with the tenant config set to
 * each tenant. Only the current tenant's records should be visible.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyRlsIntegrationTest {

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    /** Runtime DataSource — subject to RLS (sanad_runtime_app, no BYPASSRLS). */
    @Autowired
    private DataSource runtimeDataSource;

    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    private UUID insertRecord(UUID tenantId, String key) {
        UUID id = UUID.randomUUID();
        fixtureJdbc.update(
                "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, " +
                "operation, route, request_fingerprint, status, expires_at, " +
                "created_at, updated_at) VALUES (?, ?, ?, 'ORGANIZATION.CREATE', " +
                "'/api/v1/organizations', ?, 'COMPLETED', NOW() + INTERVAL '24 hours', " +
                "NOW(), NOW())",
                id, tenantId, key, "a".repeat(64));
        return id;
    }

    @Test
    @DisplayName("runtimeRole_seesOnlyOwnTenantRecords: runtime DS with tenant=A → only A's records visible")
    void runtimeRole_seesOnlyOwnTenantRecords() throws Exception {
        UUID recordA = insertRecord(fixture.tenantAId(), "rls-key-a");
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-b");

        try (var conn = runtimeDataSource.getConnection(); var stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.execute("SELECT set_config('app.current_tenant_id', '" +
                    fixture.tenantAId() + "', true)");

            var rs = stmt.executeQuery(
                    "SELECT id FROM idempotency_records WHERE id = '" + recordA + "'");
            assertThat(rs.next()).as("Tenant A's record must be visible under tenant A").isTrue();

            var rs2 = stmt.executeQuery(
                    "SELECT id FROM idempotency_records WHERE id = '" + recordB + "'");
            assertThat(rs2.next())
                    .as("Tenant B's record must NOT be visible under tenant A")
                    .isFalse();

            conn.rollback();
        }
    }

    @Test
    @DisplayName("crossTenantIdempotencyRecord_invisible: runtime DS with tenant=A → tenant B's record invisible")
    void crossTenantIdempotencyRecord_invisible() throws Exception {
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-cross-b");

        try (var conn = runtimeDataSource.getConnection(); var stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.execute("SELECT set_config('app.current_tenant_id', '" +
                    fixture.tenantAId() + "', true)");

            var rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM idempotency_records WHERE id = '" + recordB + "'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("Tenant B's idempotency record must be invisible under Tenant A (RLS)")
                    .isEqualTo(0);

            conn.rollback();
        }
    }
}
