package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyReservationStore;
import com.sanad.platform.idempotency.service.LeaseGrant;
import com.sanad.platform.idempotency.service.RequestFingerprintService;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.1 §5 — Verifies the atomic reservation mechanism of
 * {@link IdempotencyReservationStore#atomicReserve}.
 *
 * <p>The atomic reservation uses PostgreSQL's
 * {@code INSERT ... ON CONFLICT DO NOTHING RETURNING} (in the
 * {@code PostgresIdempotencyReservationStore}) so that exactly one of
 * N concurrent reservations for the same key wins, and the others get
 * an empty {@link Optional}.</p>
 *
 * <p>Two scenarios:</p>
 * <ol>
 *   <li>{@code twoConcurrentReservations_oneWinsOneEmpty} — two threads
 *       reserve the same key simultaneously via the store. Exactly one
 *       thread gets a non-empty {@link Optional#of(UUID)}, the other
 *       gets {@link Optional#empty()}.</li>
 *   <li>{@code twentyConcurrentReservations_exactlyOneWinner} — twenty
 *       threads reserve the same key simultaneously. Exactly one thread
 *       wins (non-empty), the other nineteen get empty optionals. No
 *       duplicate rows exist in the DB afterward.</li>
 * </ol>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyAtomicReservationIntegrationTest {

    @Autowired private IdempotencyReservationStore store;
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

    /**
     * Computes the same fingerprint that {@link com.sanad.platform.idempotency.service.IdempotencyService}
     * would compute for the given request body.
     */
    private String computeFingerprint(UUID tenantId, String body) {
        RequestFingerprintService fps = new RequestFingerprintService();
        return fps.compute("POST", "/api/v1/organizations", body, null,
                tenantId, "ORGANIZATION.CREATE");
    }

    /**
     * Counts idempotency records for the given tenant + key.
     */
    private int countRecords(UUID tenantId, String key) throws Exception {
        String sql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("twoConcurrentReservations_oneWinsOneEmpty: 2 threads reserve same key → exactly 1 non-empty, 1 empty")
    void twoConcurrentReservations_oneWinsOneEmpty() throws Exception {
        String key = "atomic-reserve-2-" + UUID.randomUUID();
        String body = "{\"name\":\"Atomic Reserve 2 Org\"}";
        String fingerprint = computeFingerprint(fixture.tenantAId(), body);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant leaseExpiresAt = Instant.now().plusSeconds(300);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Optional<LeaseGrant>>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final String ownerRequestId = "owner-2-" + i + "-" + UUID.randomUUID();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return store.atomicReserve(
                        fixture.tenantAId(), key, "ORGANIZATION.CREATE",
                        "/api/v1/organizations", "Organization", fingerprint,
                        expiresAt, ownerRequestId, leaseExpiresAt);
            }, executor));
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        executor.shutdown();

        List<Optional<LeaseGrant>> results = new ArrayList<>();
        for (CompletableFuture<Optional<LeaseGrant>> f : futures) {
            results.add(f.get());
        }

        long nonEmptyCount = results.stream().filter(Optional::isPresent).count();
        long emptyCount = results.stream().filter(Optional::isEmpty).count();

        assertThat(nonEmptyCount)
                .as("exactly one reservation must win (non-empty Optional)")
                .isEqualTo(1L);
        assertThat(emptyCount)
                .as("exactly one reservation must lose (empty Optional)")
                .isEqualTo(1L);

        // The winner's UUID must be non-null.
        LeaseGrant winnerGrant = results.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);
        assertThat(winnerGrant)
                .as("winner LeaseGrant must be non-null")
                .isNotNull();

        // Exactly 1 record must exist in the DB.
        int recordCount = countRecords(fixture.tenantAId(), key);
        assertThat(recordCount)
                .as("exactly 1 idempotency record must exist after concurrent reservations")
                .isEqualTo(1);

        // The winner's UUID must match the stored row's id.
        String idSql = "SELECT id FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(idSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("the stored row must exist")
                        .isTrue();
                UUID storedId = (UUID) rs.getObject("id");
                assertThat(storedId)
                        .as("stored row id must equal the winner's returned UUID")
                        .isEqualTo(winnerGrant);
            }
        }
    }

    @Test
    @DisplayName("twentyConcurrentReservations_exactlyOneWinner: 20 threads reserve same key → 1 winner, 19 empty, 1 DB row")
    void twentyConcurrentReservations_exactlyOneWinner() throws Exception {
        String key = "atomic-reserve-20-" + UUID.randomUUID();
        String body = "{\"name\":\"Atomic Reserve 20 Org\"}";
        String fingerprint = computeFingerprint(fixture.tenantAId(), body);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant leaseExpiresAt = Instant.now().plusSeconds(300);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Optional<LeaseGrant>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String ownerRequestId = "owner-20-" + i + "-" + UUID.randomUUID();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return store.atomicReserve(
                        fixture.tenantAId(), key, "ORGANIZATION.CREATE",
                        "/api/v1/organizations", "Organization", fingerprint,
                        expiresAt, ownerRequestId, leaseExpiresAt);
            }, executor));
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(120, TimeUnit.SECONDS);
        executor.shutdown();

        List<Optional<LeaseGrant>> results = new ArrayList<>();
        for (CompletableFuture<Optional<LeaseGrant>> f : futures) {
            results.add(f.get());
        }

        long nonEmptyCount = results.stream().filter(Optional::isPresent).count();
        long emptyCount = results.stream().filter(Optional::isEmpty).count();

        assertThat(nonEmptyCount)
                .as("exactly one of %d concurrent reservations must win", threadCount)
                .isEqualTo(1L);
        assertThat(emptyCount)
                .as("the remaining %d reservations must return empty Optional", threadCount - 1)
                .isEqualTo((long) threadCount - 1);

        int recordCount = countRecords(fixture.tenantAId(), key);
        assertThat(recordCount)
                .as("exactly 1 idempotency record must exist after %d concurrent reservations",
                        threadCount)
                .isEqualTo(1);

        // Verify the record was created with lease_version=1 (Stage 05A.2.1 §6).
        String lvSql = "SELECT lease_version, status, attempt_count "
                + "FROM idempotency_records WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(lvSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("the stored row must exist")
                        .isTrue();
                long leaseVersion = rs.getLong("lease_version");
                String status = rs.getString("status");
                int attemptCount = rs.getInt("attempt_count");
                assertThat(leaseVersion)
                        .as("new reservation must have lease_version=1 (Stage 05A.2.1 §6)")
                        .isEqualTo(1L);
                assertThat(status)
                        .as("new reservation must have status=PROCESSING")
                        .isEqualTo("PROCESSING");
                assertThat(attemptCount)
                        .as("new reservation must have attempt_count=1")
                        .isEqualTo(1);
            }
        }
    }
}
