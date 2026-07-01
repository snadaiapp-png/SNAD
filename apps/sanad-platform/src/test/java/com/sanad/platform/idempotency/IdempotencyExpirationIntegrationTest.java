package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyService;
import com.sanad.platform.security.service.JwtTokenProvider;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.1 §19 — Verifies that an expired idempotency record returns
 * EXPIRED (not REPLAY or NEW) when the reservation is attempted again.
 *
 * <p>An expired record (regardless of whether it was PROCESSING or
 * COMPLETED) must return EXPIRED so the caller knows the key is no
 * longer valid and a fresh key should be used.</p>
 *
 * <p>Stage 05A.1 §13 — The {@link TenantContext} is established with
 * {@link TenantContext.TenantContextSource#JWT_CLAIM} source (the same
 * source the {@code TenantContextFilter} establishes) and populated with
 * the verified user/tenant IDs from the fixture. No {@code TEST_FIXTURE}
 * source is used.</p>
 *
 * <p>All DB writes use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
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
    @Autowired private JwtTokenProvider jwtTokenProvider;

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

    /**
     * Establishes a JWT_CLAIM-sourced TenantContext (same source the filter
     * chain establishes) with the verified user/tenant IDs from the fixture.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "test-req-" + UUID.randomUUID()));
    }

    /**
     * Inserts an expired idempotency record directly via the fixture DS
     * with the given status and a past expires_at. The request fingerprint
     * is computed by {@link com.sanad.platform.idempotency.service.RequestFingerprintService}
     * so that a subsequent reserveOrReplay with the same body produces the
     * same fingerprint (and thus hits the existing record).
     *
     * @return the idempotency_key of the inserted record
     */
    private String insertExpiredRecord(String status, String body) throws Exception {
        UUID id = UUID.randomUUID();
        String key = "expired-" + status.toLowerCase() + "-" + UUID.randomUUID();

        // Compute the request fingerprint the same way IdempotencyService would,
        // so that a subsequent reserveOrReplay with the same body hits this record.
        com.sanad.platform.idempotency.service.RequestFingerprintService fingerprintService =
                new com.sanad.platform.idempotency.service.RequestFingerprintService();
        String fingerprint = fingerprintService.compute(
                "POST", "/api/v1/organizations", body, null,
                fixture.tenantAId(), "ORGANIZATION.CREATE");

        String sql = "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, "
                + "operation, route, request_fingerprint, status, expires_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'ORGANIZATION.CREATE', "
                + "'/api/v1/organizations', ?, ?, ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp pastExpiry = Timestamp.from(Instant.now().minusSeconds(3600));
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, fixture.tenantAId());
            ps.setString(3, key);
            ps.setString(4, fingerprint);
            ps.setString(5, status);
            ps.setTimestamp(6, pastExpiry);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            ps.executeUpdate();
        }
        return key;
    }

    @Test
    @DisplayName("expiredProcessingRecord_returnsExpired: PROCESSING record past expiresAt → EXPIRED")
    void expiredProcessingRecord_returnsExpired() throws Exception {
        String body = "{\"name\":\"Expired Processing Org\"}";
        String key = insertExpiredRecord("PROCESSING", body);

        setJwtClaimContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null);
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
    void expiredCompletedRecord_returnsExpired() throws Exception {
        String body = "{\"name\":\"Expired Completed Org\"}";
        String key = insertExpiredRecord("COMPLETED", body);

        // Set a response body so the record looks like a real completed one.
        String updateSql = "UPDATE idempotency_records SET response_status = 201, "
                + "response_body = ? WHERE idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, "{\"id\":\"old-org\"}");
            ps.setString(2, key);
            ps.executeUpdate();
        }

        setJwtClaimContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(result.type())
                .as("expired COMPLETED record must return EXPIRED (not REPLAY)")
                .isEqualTo(IdempotencyService.ReservationType.EXPIRED);
    }
}
