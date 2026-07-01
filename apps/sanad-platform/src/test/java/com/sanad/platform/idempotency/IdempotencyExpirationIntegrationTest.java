package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyService;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
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
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §19 — Verifies that an expired idempotency record returns
 * EXPIRED (not REPLAY or NEW) when the reservation is attempted again.
 *
 * <p>An expired record (regardless of whether it was PROCESSING or
 * COMPLETED) must return EXPIRED so the caller knows the key is no
 * longer valid and a fresh key should be used.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyExpirationIntegrationTest {

    @Autowired private IdempotencyService idempotencyService;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

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

    private void setTenantContext() {
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(),
                "test-jti-" + UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "test-req-" + UUID.randomUUID()));
    }

    private UUID insertExpiredRecord(String status, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        String key = "expired-" + status.toLowerCase() + "-" + UUID.randomUUID();
        fixtureJdbc.update(
                "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, " +
                "operation, route, request_fingerprint, status, expires_at, " +
                "created_at, updated_at) VALUES (?, ?, ?, 'ORGANIZATION.CREATE', " +
                "'/api/v1/organizations', ?, ?, ?, NOW(), NOW())",
                id, fixture.tenantAId(), key,
                "a".repeat(64), status, expiresAt);
        return id;
    }

    @Test
    @DisplayName("expiredProcessingRecord_returnsExpired: PROCESSING record past expiresAt → EXPIRED")
    void expiredProcessingRecord_returnsExpired() {
        // Insert a PROCESSING record that expired 1 hour ago.
        Instant pastExpiry = Instant.now().minusSeconds(3600);
        UUID recordId = insertExpiredRecord("PROCESSING", pastExpiry);

        // Attempt to reserve with the same key — the service must detect the
        // expired record and return EXPIRED. We need to look up the key.
        String key = fixtureJdbc.queryForObject(
                "SELECT idempotency_key FROM idempotency_records WHERE id = ?",
                String.class, recordId);

        setTenantContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", "{\"name\":\"Expired Processing\"}", null);
        } finally {
            contextProvider.clear();
        }
        assertThat(result.type())
                .as("expired PROCESSING record must return EXPIRED")
                .isEqualTo(IdempotencyService.ReservationType.EXPIRED);
        assertThat(result.message())
                .as("EXPIRED message should mention expiration")
                .contains("expired");
    }

    @Test
    @DisplayName("expiredCompletedRecord_returnsExpired: COMPLETED record past expiresAt → EXPIRED (not REPLAY)")
    void expiredCompletedRecord_returnsExpired() {
        // Insert a COMPLETED record that expired 1 hour ago.
        Instant pastExpiry = Instant.now().minusSeconds(3600);
        UUID recordId = insertExpiredRecord("COMPLETED", pastExpiry);

        // Set a response body so the record looks like a real completed one.
        fixtureJdbc.update(
                "UPDATE idempotency_records SET response_status = 201, " +
                "response_body = '{\"id\":\"old-org\"}' WHERE id = ?",
                recordId);

        String key = fixtureJdbc.queryForObject(
                "SELECT idempotency_key FROM idempotency_records WHERE id = ?",
                String.class, recordId);

        setTenantContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", "{\"name\":\"Expired Completed\"}", null);
        } finally {
            contextProvider.clear();
        }
        assertThat(result.type())
                .as("expired COMPLETED record must return EXPIRED (not REPLAY)")
                .isEqualTo(IdempotencyService.ReservationType.EXPIRED);
    }
}
