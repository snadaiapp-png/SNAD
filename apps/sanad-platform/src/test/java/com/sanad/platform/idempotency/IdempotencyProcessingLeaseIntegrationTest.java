package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyService;
import com.sanad.platform.idempotency.service.RequestFingerprintService;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2 §14 — Verifies the processing lease takeover mechanism.
 *
 * <p>The V31 migration added four columns to {@code idempotency_records}:
 * {@code lease_owner_request_id}, {@code lease_expires_at},
 * {@code attempt_count}, and {@code last_attempt_at}. These enable atomic
 * takeover of stale PROCESSING records — when the original request's lease
 * expires, a retry can atomically take over and re-execute.</p>
 *
 * <p>Three tests verify the takeover semantics:</p>
 * <ol>
 *   <li>{@code freshProcessing_noTakeover} — a PROCESSING record with a
 *       future lease cannot be taken over. The next
 *       {@link IdempotencyService#reserveOrReplay} returns
 *       {@link IdempotencyService.ReservationType#IN_PROGRESS}.</li>
 *   <li>{@code expiredLease_oneTakeover} — a PROCESSING record with an
 *       expired lease is taken over. The next
 *       {@link IdempotencyService#reserveOrReplay} returns
 *       {@link IdempotencyService.ReservationType#NEW} with a "takeover"
 *       message, and {@code attempt_count} is incremented.</li>
 *   <li>{@code twoRecoveryRequests_oneTakeoverOnly} — two concurrent
 *       recovery requests on an expired PROCESSING record: exactly one
 *       succeeds ({@code NEW}), the other gets {@code IN_PROGRESS}.</li>
 * </ol>
 *
 * <p>Stage 05A.1 §13 — The {@link TenantContext} is established with
 * {@link TenantContext.TenantContextSource#JWT_CLAIM} source (the same
 * source the {@code TenantContextFilter} establishes) and populated with
 * the verified user/tenant IDs from the fixture. No {@code TEST_FIXTURE}
 * source is used.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB writes use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyProcessingLeaseIntegrationTest {

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
     * Each invocation mints a fresh JWT so the jti (sessionId) is unique.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "lease-req-" + UUID.randomUUID()));
    }

    /**
     * Inserts a PROCESSING idempotency record directly via the fixture
     * DataSource with the given lease expiry. The request fingerprint is
     * computed by {@link RequestFingerprintService} so that a subsequent
     * {@link IdempotencyService#reserveOrReplay} with the same body
     * produces the same fingerprint (and thus hits the existing record).
     *
     * @param leaseExpiry when the processing lease expires
     * @param body        the request body used to compute the fingerprint
     * @return the idempotency_key of the inserted record
     */
    private String insertProcessingRecord(UUID tenantId, Instant leaseExpiry, String body)
            throws Exception {
        UUID id = UUID.randomUUID();
        String key = "lease-test-" + UUID.randomUUID();

        // Compute the fingerprint the same way IdempotencyService would.
        RequestFingerprintService fingerprintService = new RequestFingerprintService();
        String fingerprint = fingerprintService.compute(
                "POST", "/api/v1/organizations", body, null,
                tenantId, "ORGANIZATION.CREATE");

        // Stage 05A.2.1 §6 — Insert with lease_version=1 (the V32 column is
        // NOT NULL with DEFAULT 0, but we set it explicitly to mirror what
        // PostgresIdempotencyReservationStore.atomicReserve does).
        String sql = "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, "
                + "operation, route, request_fingerprint, status, expires_at, "
                + "created_at, updated_at, lease_owner_request_id, lease_expires_at, "
                + "attempt_count, last_attempt_at, lease_version) "
                + "VALUES (?, ?, ?, 'ORGANIZATION.CREATE', '/api/v1/organizations', "
                + "?, 'PROCESSING', ?, ?, ?, ?, ?, 1, ?, 1)";
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp futureExpiry = Timestamp.from(Instant.now().plusSeconds(3600));
        Timestamp leaseTs = Timestamp.from(leaseExpiry);
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, key);
            ps.setString(4, fingerprint);
            ps.setTimestamp(5, futureExpiry);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.setString(8, "original-owner-" + UUID.randomUUID());
            ps.setTimestamp(9, leaseTs);
            ps.setTimestamp(10, now);
            ps.executeUpdate();
        }
        return key;
    }

    /**
     * Reads the attempt_count for the given idempotency key.
     */
    private int readAttemptCount(UUID tenantId, String key) throws Exception {
        String sql = "SELECT attempt_count FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("idempotency record must exist for key %s", key)
                        .isTrue();
                return rs.getInt("attempt_count");
            }
        }
    }

    /**
     * Reads the lease_owner_request_id for the given idempotency key.
     */
    private String readLeaseOwner(UUID tenantId, String key) throws Exception {
        String sql = "SELECT lease_owner_request_id FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("lease_owner_request_id");
            }
        }
    }

    @Test
    @DisplayName("freshProcessing_noTakeover: PROCESSING record with future lease → IN_PROGRESS (no takeover)")
    void freshProcessing_noTakeover() throws Exception {
        String body = "{\"name\":\"Fresh Lease Org\"}";
        // Lease expires 5 minutes in the future — still fresh.
        Instant freshLeaseExpiry = Instant.now().plusSeconds(300);
        String key = insertProcessingRecord(fixture.tenantAId(), freshLeaseExpiry, body);

        // Attempt reserveOrReplay with the same key + body.
        setJwtClaimContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null, "test-req-" + java.util.UUID.randomUUID());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.type())
                .as("fresh PROCESSING record must return IN_PROGRESS (no takeover)")
                .isEqualTo(IdempotencyService.ReservationType.IN_PROGRESS);
        assertThat(result.message())
                .as("IN_PROGRESS message should mention processing")
                .contains("processing");

        // Verify attempt_count was NOT incremented (no takeover happened).
        int attemptCount = readAttemptCount(fixture.tenantAId(), key);
        assertThat(attemptCount)
                .as("attempt_count must remain 1 (no takeover on fresh lease)")
                .isEqualTo(1);

        // Verify the lease_owner_request_id was NOT changed.
        String leaseOwner = readLeaseOwner(fixture.tenantAId(), key);
        assertThat(leaseOwner)
                .as("lease_owner_request_id must start with 'original-owner-' (no takeover)")
                .startsWith("original-owner-");
    }

    @Test
    @DisplayName("expiredLease_oneTakeover: PROCESSING record with expired lease → NEW (takeover succeeds, attempt_count incremented)")
    void expiredLease_oneTakeover() throws Exception {
        String body = "{\"name\":\"Expired Lease Org\"}";
        // Lease expired 5 minutes ago.
        Instant expiredLeaseExpiry = Instant.now().minusSeconds(300);
        String key = insertProcessingRecord(fixture.tenantAId(), expiredLeaseExpiry, body);

        // Attempt reserveOrReplay with the same key + body.
        setJwtClaimContext();
        IdempotencyService.ReservationResult result;
        try {
            result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null, "test-req-" + java.util.UUID.randomUUID());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.type())
                .as("expired PROCESSING lease must be taken over (NEW)")
                .isEqualTo(IdempotencyService.ReservationType.NEW);
        assertThat(result.message())
                .as("takeover message should mention 'takeover'")
                .containsIgnoringCase("takeover");

        // Verify attempt_count was incremented (takeover happened).
        int attemptCount = readAttemptCount(fixture.tenantAId(), key);
        assertThat(attemptCount)
                .as("attempt_count must be incremented after takeover (was 1, should be 2)")
                .isGreaterThanOrEqualTo(2);

        // Verify the lease_owner_request_id was changed (new owner took over).
        String leaseOwner = readLeaseOwner(fixture.tenantAId(), key);
        assertThat(leaseOwner)
                .as("lease_owner_request_id must NOT be the original owner after takeover")
                .doesNotStartWith("original-owner-");

        // Verify the lease_expires_at was updated to a future time.
        String leaseSql = "SELECT lease_expires_at, lease_version FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(leaseSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                Timestamp leaseExpiresAt = rs.getTimestamp("lease_expires_at");
                assertThat(leaseExpiresAt)
                        .as("lease_expires_at must be updated to a future time after takeover")
                        .isNotNull();
                assertThat(leaseExpiresAt.toInstant())
                        .as("lease_expires_at must be in the future after takeover")
                        .isAfter(Instant.now());
                // Stage 05A.2.1 §6 — lease_version must be incremented to 2.
                long leaseVersion = rs.getLong("lease_version");
                assertThat(leaseVersion)
                        .as("lease_version must be incremented from 1 to 2 after takeover (Stage 05A.2.1 §6)")
                        .isEqualTo(2L);
            }
        }
    }

    @Test
    @DisplayName("twoRecoveryRequests_oneTakeoverOnly: two concurrent takeovers on expired PROCESSING → exactly one NEW, one IN_PROGRESS")
    void twoRecoveryRequests_oneTakeoverOnly() throws Exception {
        String body = "{\"name\":\"Concurrent Takeover Org\"}";
        // Lease expired 5 minutes ago.
        Instant expiredLeaseExpiry = Instant.now().minusSeconds(300);
        String key = insertProcessingRecord(fixture.tenantAId(), expiredLeaseExpiry, body);

        // Two concurrent recovery requests.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<IdempotencyService.ReservationType>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                // Each thread establishes its own TenantContext (thread-local)
                // with a unique jti/requestId so the lease_owner_request_id
                // differs between the two attempts.
                String token = jwtTokenProvider.mintAccessToken(
                        fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
                io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
                String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
                contextProvider.setContext(new TenantContext(
                        fixture.tenantAId(), fixture.userAId(), jti, 0L,
                        Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                        "takeover-req-" + idx + "-" + UUID.randomUUID()));
                try {
                    IdempotencyService.ReservationResult r = idempotencyService.reserveOrReplay(
                            key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                            "Organization", "POST", body, null, "test-req-" + java.util.UUID.randomUUID());
                    return r.type();
                } finally {
                    contextProvider.clear();
                }
            }, executor));
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        executor.shutdown();

        List<IdempotencyService.ReservationType> results = new ArrayList<>();
        for (CompletableFuture<IdempotencyService.ReservationType> f : futures) {
            results.add(f.get());
        }

        long newCount = results.stream()
                .filter(t -> t == IdempotencyService.ReservationType.NEW)
                .count();
        long inProgressCount = results.stream()
                .filter(t -> t == IdempotencyService.ReservationType.IN_PROGRESS)
                .count();

        assertThat(newCount)
                .as("exactly one recovery request must succeed (NEW) — the other must not take over")
                .isEqualTo(1);
        assertThat(inProgressCount)
                .as("the other recovery request must return IN_PROGRESS (lost the race)")
                .isEqualTo(1);

        // Verify exactly 1 record exists (no duplicate inserts).
        String countSql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                int recordCount = rs.getInt(1);
                assertThat(recordCount)
                        .as("exactly 1 idempotency record must exist (no duplicate inserts)")
                        .isEqualTo(1);
            }
        }

        // Verify attempt_count was incremented exactly once (one takeover).
        // The record was inserted with attempt_count=1, and one takeover
        // incremented it to 2. The other attempt did not increment it.
        int attemptCount = readAttemptCount(fixture.tenantAId(), key);
        assertThat(attemptCount)
                .as("attempt_count must be exactly 2 (one takeover, one failed attempt did not increment)")
                .isEqualTo(2);
    }
}
