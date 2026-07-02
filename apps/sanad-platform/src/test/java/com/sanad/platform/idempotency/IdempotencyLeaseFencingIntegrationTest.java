package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyReservationStore;
import com.sanad.platform.idempotency.service.LeaseGrant;
import com.sanad.platform.idempotency.service.RequestFingerprintService;
import com.sanad.platform.idempotency.service.StaleIdempotencyLeaseException;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05A.2.1 §7 — Verifies lease fencing via the {@code lease_version}
 * column on {@code idempotency_records}.
 *
 * <p>Lease fencing prevents stale workers from corrupting idempotency
 * state after a lease takeover:</p>
 * <ol>
 *   <li>Worker A reserves a key (lease_version = 1, owner = requestId-A).</li>
 *   <li>Worker A's lease expires (lease_expires_at &lt; NOW()).</li>
 *   <li>Worker B takes over via {@link IdempotencyReservationStore#atomicTakeoverLease}
 *       — the lease_version is atomically incremented to 2, and the owner
 *       is changed to requestId-B.</li>
 *   <li>Worker A attempts to complete the record with its stale
 *       lease_version = 1 → {@link StaleIdempotencyLeaseException}.</li>
 *   <li>Worker B completes the record with its current lease_version = 2
 *       → success.</li>
 * </ol>
 *
 * <p>Stage 05A.2.1 §6 — The {@code lease_version} column was added in
 * the V32 migration. It serves as a fencing token: any
 * {@code atomicComplete} or {@code atomicFail} call must include the
 * caller's expected lease_version; if it doesn't match the stored value,
 * the call is rejected.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyLeaseFencingIntegrationTest {

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
     * Reads the current lease_version and lease_owner_request_id for the
     * given idempotency record.
     */
    private long[] readLeaseState(UUID tenantId, String key) throws Exception {
        String sql = "SELECT lease_version, lease_owner_request_id, status "
                + "FROM idempotency_records WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("record must exist for key %s", key)
                        .isTrue();
                // Return lease_version (long) and store owner as a long-encoded
                // pseudo-hash so the caller can do simple assertions.
                return new long[]{rs.getLong("lease_version")};
            }
        }
    }

    @Test
    @DisplayName("workerA_complete_afterTakeoverByWorkerB_staleLeaseRejected: A reserves (v1), B takes over (v2), A complete → StaleIdempotencyLeaseException, B complete → success")
    void workerA_complete_afterTakeoverByWorkerB_staleLeaseRejected() throws Exception {
        String key = "lease-fence-" + UUID.randomUUID();
        String body = "{\"name\":\"Lease Fence Org\"}";
        String fingerprint = computeFingerprint(fixture.tenantAId(), body);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        // Worker A's lease expires in 1 second — we will manually expire it
        // before takeover so the test is deterministic.
        Instant leaseExpiresAtA = Instant.now().plusSeconds(3600);
        String ownerA = "owner-A-" + UUID.randomUUID();
        String ownerB = "owner-B-" + UUID.randomUUID();

        // === Step 1: Worker A reserves the key (lease_version = 1) ===
        Optional<LeaseGrant> reservedA = store.atomicReserve(
                fixture.tenantAId(), key, "ORGANIZATION.CREATE",
                "/api/v1/organizations", "Organization", fingerprint,
                expiresAt, ownerA, leaseExpiresAtA);
        assertThat(reservedA)
                .as("Worker A's reservation must succeed (first reservation)")
                .isPresent();
        UUID recordId = reservedA.get().recordId();

        long[] stateAfterReserve = readLeaseState(fixture.tenantAId(), key);
        assertThat(stateAfterReserve[0])
                .as("lease_version must be 1 immediately after reservation")
                .isEqualTo(1L);

        // === Step 2: Worker A's lease expires (manually set lease_expires_at to past) ===
        // We use the fixture DataSource to set the lease_expires_at to the past,
        // simulating that Worker A's lease has timed out.
        String expireLeaseSql = "UPDATE idempotency_records "
                + "SET lease_expires_at = ? WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(expireLeaseSql)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minusSeconds(600)));
            ps.setObject(2, recordId);
            ps.executeUpdate();
        }

        // === Step 3: Worker B takes over (lease_version becomes 2) ===
        Instant leaseExpiresAtB = Instant.now().plusSeconds(3600);
        Optional<LeaseGrant> takenOver =
                store.atomicTakeoverLease(recordId, ownerB, leaseExpiresAtB);
        assertThat(takenOver)
                .as("Worker B's takeover must succeed (lease was expired)")
                .isPresent();

        long[] stateAfterTakeover = readLeaseState(fixture.tenantAId(), key);
        assertThat(stateAfterTakeover[0])
                .as("lease_version must be incremented to 2 after Worker B's takeover")
                .isEqualTo(2L);

        // === Step 4: Worker A attempts completion with stale lease_version = 1 → StaleIdempotencyLeaseException ===
        assertThatThrownBy(() -> store.atomicComplete(
                recordId, fixture.tenantAId(), ownerA, 1L, 201,
                "Content-Type: application/json", "{\"id\":\"" + recordId + "\"}"))
                .as("Worker A's completion with stale lease_version=1 must be rejected")
                .isInstanceOf(StaleIdempotencyLeaseException.class);

        // Verify the record is still PROCESSING (Worker A's stale completion did not commit).
        String statusSql = "SELECT status FROM idempotency_records WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(statusSql)) {
            ps.setObject(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status"))
                        .as("record must still be PROCESSING after Worker A's stale completion was rejected")
                        .isEqualTo("PROCESSING");
            }
        }

        // === Step 5: Worker B completes with lease_version = 2 → success ===
        store.atomicComplete(
                recordId, fixture.tenantAId(), ownerB, 2L, 201,
                "Content-Type: application/json",
                "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Lease Fence Org\"}");

        // Verify the record is now COMPLETED.
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(statusSql)) {
            ps.setObject(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status"))
                        .as("record must be COMPLETED after Worker B's completion with lease_version=2")
                        .isEqualTo("COMPLETED");
            }
        }
    }

    @Test
    @DisplayName("workerA_fail_afterTakeoverByWorkerB_staleLeaseRejected: A reserves (v1), B takes over (v2), A fail → StaleIdempotencyLeaseException, B fail → success")
    void workerA_fail_afterTakeoverByWorkerB_staleLeaseRejected() throws Exception {
        String key = "lease-fence-fail-" + UUID.randomUUID();
        String body = "{\"name\":\"Lease Fence Fail Org\"}";
        String fingerprint = computeFingerprint(fixture.tenantAId(), body);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant leaseExpiresAtA = Instant.now().plusSeconds(3600);
        String ownerA = "owner-A-fail-" + UUID.randomUUID();
        String ownerB = "owner-B-fail-" + UUID.randomUUID();

        // Step 1: Worker A reserves.
        Optional<LeaseGrant> reservedA = store.atomicReserve(
                fixture.tenantAId(), key, "ORGANIZATION.CREATE",
                "/api/v1/organizations", "Organization", fingerprint,
                expiresAt, ownerA, leaseExpiresAtA);
        assertThat(reservedA).isPresent();
        UUID recordId = reservedA.get().recordId();

        // Step 2: Manually expire Worker A's lease.
        String expireLeaseSql = "UPDATE idempotency_records "
                + "SET lease_expires_at = ? WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(expireLeaseSql)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minusSeconds(600)));
            ps.setObject(2, recordId);
            ps.executeUpdate();
        }

        // Step 3: Worker B takes over.
        Instant leaseExpiresAtB = Instant.now().plusSeconds(3600);
        store.atomicTakeoverLease(recordId, ownerB, leaseExpiresAtB);

        // Step 4: Worker A attempts fail with stale lease_version = 1 → rejected.
        assertThatThrownBy(() -> store.atomicFail(
                recordId, fixture.tenantAId(), ownerA, 1L,
                "SANAD-IDEMP-EXEC", "Worker A's stale failure", true))
                .as("Worker A's fail with stale lease_version=1 must be rejected")
                .isInstanceOf(StaleIdempotencyLeaseException.class);

        // Step 5: Worker B fails with lease_version = 2 → success (FAILED_RETRYABLE).
        store.atomicFail(
                recordId, fixture.tenantAId(), ownerB, 2L,
                "SANAD-IDEMP-EXEC", "Worker B's failure", true);

        // Verify the record is now FAILED_RETRYABLE.
        String statusSql = "SELECT status FROM idempotency_records WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(statusSql)) {
            ps.setObject(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status"))
                        .as("record must be FAILED_RETRYABLE after Worker B's fail with lease_version=2")
                        .isEqualTo("FAILED_RETRYABLE");
            }
        }
    }
}
