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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.1 §14 — Verifies Row-Level Security on {@code idempotency_records}.
 *
 * <p>Stage 05A.1 §7 — RLS isolates idempotency records per tenant. Tenant A's
 * records must be invisible to Tenant B (and vice versa), and a missing
 * tenant context must yield zero rows.</p>
 *
 * <p>Inserts records for two tenants via the fixture DataSource (BYPASSRLS),
 * then queries via the runtime DataSource (subject to RLS) with each
 * tenant's config set. Only the current tenant's records should be
 * visible.</p>
 *
 * <p>All DB reads use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
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
        String sql = "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, "
                + "operation, route, request_fingerprint, status, expires_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'ORGANIZATION.CREATE', "
                + "'/api/v1/organizations', ?, 'COMPLETED', ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(3600));
        fixtureJdbc.update(sql, id, tenantId, key, "a".repeat(64),
                expiresAt, now, now);
        return id;
    }

    /**
     * Sets the runtime tenant context for the given tenant via the runtime
     * DataSource, then runs the provided SQL query and returns the int
     * result from the first column of the first row.
     */
    private int countVisibleRecordsForTenant(UUID tenantId, UUID targetRecordId) throws Exception {
        try (Connection conn = runtimeDataSource.getConnection()) {
            // Set the tenant config on this connection.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery();
            }
            // Now query — RLS will filter rows by tenant_id.
            String sql = "SELECT COUNT(*) FROM idempotency_records WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, targetRecordId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    @Test
    @DisplayName("runtimeRole_seesOnlyOwnTenantRecords: runtime DS with tenant=A → only A's records visible")
    void runtimeRole_seesOnlyOwnTenantRecords() throws Exception {
        UUID recordA = insertRecord(fixture.tenantAId(), "rls-key-a");
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-b");

        int visibleA = countVisibleRecordsForTenant(fixture.tenantAId(), recordA);
        int visibleB = countVisibleRecordsForTenant(fixture.tenantAId(), recordB);

        assertThat(visibleA)
                .as("Tenant A's record must be visible under tenant A")
                .isEqualTo(1);
        assertThat(visibleB)
                .as("Tenant B's record must NOT be visible under tenant A (RLS)")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("crossTenantIdempotencyRecord_invisible: runtime DS with tenant=A → tenant B's record invisible")
    void crossTenantIdempotencyRecord_invisible() throws Exception {
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-cross-b");

        int visibleB = countVisibleRecordsForTenant(fixture.tenantAId(), recordB);
        assertThat(visibleB)
                .as("Tenant B's idempotency record must be invisible under Tenant A (RLS)")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("missingTenantContext_zeroRows: runtime DS without tenant config → 0 rows visible")
    void missingTenantContext_zeroRows() throws Exception {
        UUID recordA = insertRecord(fixture.tenantAId(), "rls-key-no-context");

        // Query WITHOUT setting the tenant config — RLS should return 0 rows
        // because the current_setting('app.current_tenant_id', true) returns
        // NULL/empty, which doesn't match any tenant_id.
        try (Connection conn = runtimeDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM idempotency_records WHERE id = ?")) {
            ps.setObject(1, recordA);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                assertThat(count)
                        .as("without tenant context, 0 records must be visible (RLS fail-closed)")
                        .isEqualTo(0);
            }
        }
    }
}
