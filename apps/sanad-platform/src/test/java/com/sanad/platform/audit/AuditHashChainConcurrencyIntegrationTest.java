package com.sanad.platform.audit;

import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
import com.sanad.platform.security.service.JwtTokenProvider;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §8 — Verifies linear, concurrency-safe audit hash chain
 * extension under concurrent writes.
 *
 * <p>20 concurrent HTTP POSTs to {@code /api/v1/organizations} (each with a
 * unique name and unique Idempotency-Key) trigger 20 concurrent calls to
 * {@link com.sanad.platform.audit.service.AuditService#record}. The
 * pessimistic write lock on {@code audit_chain_heads} (SELECT FOR UPDATE)
 * serializes chain extension — each event must receive a unique, monotonically
 * increasing {@code sequence_number} in the range 1..20.</p>
 *
 * <p>Stage 05A.1 §13 — Actor trust boundary: each request uses a real JWT
 * through MockMvc. The {@code TenantContextFilter} establishes the
 * TenantContext from verified JWT claims, and {@link com.sanad.platform.audit.service.AuditService}
 * takes the actor identity from it ONLY.</p>
 *
 * <p>Assertions:</p>
 * <ul>
 *   <li>20 audit events inserted for the same tenant.</li>
 *   <li>20 distinct sequence_numbers, all in the range 1..20 (no gaps).</li>
 *   <li>0 duplicate sequence_numbers.</li>
 *   <li>0 duplicate previousHash branches (linear chain — every previousHash
 *       except the genesis appears exactly once as the eventHash of the
 *       preceding event).</li>
 *   <li>{@link AuditIntegrityVerificationService#verifyChain} reports
 *       {@code valid=true}.</li>
 *   <li>{@code calculatedHeadHash == storedHeadHash} (chain head matches the
 *       recomputed hash of the last event).</li>
 * </ul>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditHashChainConcurrencyIntegrationTest {

    private static final int CONCURRENT_WRITES = 20;
    private static final long TIMEOUT_SECONDS = 120;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AuditIntegrityVerificationService integrityService;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenA;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("twentyConcurrentAuditWrites_linearChain: 20 concurrent POSTs → 20 events, sequences 1..20, valid integrity")
    void twentyConcurrentAuditWrites_linearChain() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITES);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_WRITES; i++) {
            final int idx = i;
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                String uniqueName = "Concurrency Org " + idx + " " + UUID.randomUUID();
                String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"concurrent\"}";
                String idempotencyKey = "conc-key-" + idx + "-" + UUID.randomUUID();
                try {
                    ResultActions result = mockMvc.perform(post("/api/v1/organizations")
                            .param("tenantId", fixture.tenantAId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Authorization", "Bearer " + tokenA)
                            .header("Idempotency-Key", idempotencyKey));
                    int status = result.andReturn().getResponse().getStatus();
                    if (status == 201) {
                        successes.incrementAndGet();
                    }
                    return status;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        // Release all threads simultaneously.
        startLatch.countDown();

        // Wait for all futures.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        // === Assertion 1: 20 audit events inserted ===
        Integer eventCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ?",
                Integer.class, fixture.tenantAId());
        assertThat(eventCount)
                .as("exactly %d audit events must be inserted for tenant A", CONCURRENT_WRITES)
                .isEqualTo(CONCURRENT_WRITES);

        // === Assertion 2: 20 distinct sequence_numbers in range 1..20 ===
        List<Long> sequences = new ArrayList<>();
        String seqSql = "SELECT sequence_number FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY sequence_number ASC";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(seqSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sequences.add(rs.getLong("sequence_number"));
                }
            }
        }
        assertThat(sequences)
                .as("must collect %d sequence numbers", CONCURRENT_WRITES)
                .hasSize(CONCURRENT_WRITES);

        Set<Long> uniqueSequences = new HashSet<>(sequences);
        assertThat(uniqueSequences)
                .as("sequence numbers must be distinct (0 duplicates)")
                .hasSize(CONCURRENT_WRITES);

        for (long i = 1; i <= CONCURRENT_WRITES; i++) {
            assertThat(uniqueSequences)
                    .as("sequence number %d must be present", i)
                    .contains(i);
        }

        // === Assertion 3: 0 duplicate previousHash branches ===
        // In a linear chain, every event's previousHash is the eventHash of
        // the preceding event (or the genesis hash for sequence 1). So the
        // set of previousHashes (excluding the genesis) should equal the set
        // of eventHashes (excluding the last). Equivalently: the union of
        // previousHashes has exactly CONCURRENT_WRITES entries (1 genesis +
        // (CONCURRENT_WRITES - 1) internal links), and they all link forward
        // uniquely.
        List<String> previousHashes = new ArrayList<>();
        List<String> eventHashes = new ArrayList<>();
        String hashSql = "SELECT previous_hash, event_hash FROM audit_events "
                + "WHERE tenant_id = ? ORDER BY sequence_number ASC";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(hashSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    previousHashes.add(rs.getString("previous_hash"));
                    eventHashes.add(rs.getString("event_hash"));
                }
            }
        }
        // previousHash[0] is the genesis; previousHash[i] == eventHash[i-1] for i >= 1.
        // No two events share the same previousHash (that would indicate a fork).
        Set<String> uniquePreviousHashes = new HashSet<>(previousHashes);
        assertThat(uniquePreviousHashes)
                .as("previousHash values must all be distinct (no fork)")
                .hasSize(CONCURRENT_WRITES);
        // The first previousHash is the genesis; the rest must chain.
        for (int i = 1; i < CONCURRENT_WRITES; i++) {
            assertThat(previousHashes.get(i))
                    .as("event %d previousHash must equal event %d eventHash (linear chain)",
                            i + 1, i)
                    .isEqualTo(eventHashes.get(i - 1));
        }

        // === Assertion 4: integrity verification is valid ===
        AuditIntegrityVerificationService.VerificationResult result =
                integrityService.verifyChain(fixture.tenantAId());
        assertThat(result.valid())
                .as("integrity verification must report valid=true after 20 concurrent writes")
                .isTrue();
        assertThat(result.eventsChecked())
                .as("integrity verification must check all 20 events")
                .isEqualTo(CONCURRENT_WRITES);
        assertThat(result.firstBrokenEventId())
                .as("no broken event should be reported")
                .isNull();

        // === Assertion 5: calculatedHeadHash == storedHeadHash ===
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal storedHeadHash (chain head matches)")
                .isEqualTo(result.storedHeadHash());

        // Sanity: at least most POSTs should have succeeded (some may race
        // with the OrganizationAlreadyExistsException if names collide —
        // they don't here because each name is unique, but if any do fail
        // we still want the audit chain to be consistent).
        assertThat(successes.get())
                .as("all %d concurrent creates should succeed (each name is unique)", CONCURRENT_WRITES)
                .isEqualTo(CONCURRENT_WRITES);

        // Sanity check: at least the audit events all have action=ORGANIZATION.CREATE
        Integer orgCreateCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE'",
                Integer.class, fixture.tenantAId());
        assertThat(orgCreateCount)
                .as("all audit events must have action=ORGANIZATION.CREATE")
                .isEqualTo(CONCURRENT_WRITES);
    }
}
