package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
import com.sanad.platform.audit.service.AuditService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2 §7 — Verifies that the first audit write for a new tenant
 * does not race on chain-head creation.
 *
 * <p>Before Stage 05A.2, the {@link AuditService} used a
 * {@code findByTenantIdForUpdate().orElseGet(save())} pattern to initialize
 * the chain head. Under concurrent first-write access, multiple threads
 * could observe an empty {@code Optional}, all attempt to save, and the
 * unique constraint would reject the duplicates — leaking exceptions and
 * losing audit events.</p>
 *
 * <p>Stage 05A.2 replaces this with {@code atomicInit} (INSERT ON CONFLICT
 * DO NOTHING) followed by {@code findByTenantIdForUpdate}. This test
 * verifies the fix: 20 concurrent threads call {@link AuditService#record}
 * for a fresh tenant (no pre-existing chain head), and all 20 events are
 * persisted with unique sequence numbers 1..20, exactly 1 chain head row
 * exists, and the integrity verification is valid.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete tenants
 * (FK RESTRICT from audit_events). Each test uses the fixture's unique
 * tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>Stage 05A.1 §13 — Each thread establishes its own
 * {@link TenantContext} with {@link TenantContext.TenantContextSource#JWT_CLAIM}
 * source (the same source the filter chain establishes), populated with
 * the verified user/tenant IDs from the fixture and a real JWT jti for
 * sessionId. No {@code TEST_FIXTURE} source is used.</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditFirstWriteConcurrencyIntegrationTest {

    private static final int CONCURRENT_WRITES = 20;
    private static final long TIMEOUT_SECONDS = 120;

    @Autowired private AuditService auditService;
    @Autowired private AuditIntegrityVerificationService integrityService;
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
                "first-write-req-" + UUID.randomUUID()));
    }

    @Test
    @DisplayName("twentySimultaneousFirstAuditWrites_oneChainHead: 20 concurrent first writes → 1 chain head, sequences 1..20, valid integrity")
    void twentySimultaneousFirstAuditWrites_oneChainHead() throws Exception {
        // === Sanity check: no audit_chain_heads row exists for tenantA yet ===
        // (The fixture creates tenants AFTER migration, so V27's chain-head
        // initialization does not cover them. The first AuditService.record
        // call will atomicInit the row.)
        Integer initialChainHeads = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_chain_heads WHERE tenant_id = ?",
                Integer.class, fixture.tenantAId());
        assertThat(initialChainHeads)
                .as("no chain head row should exist for the fresh fixture tenant before the first write")
                .isEqualTo(0);

        // === Launch 20 concurrent AuditService.record() calls ===
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITES);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_WRITES; i++) {
            final int idx = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                // Each thread establishes its own TenantContext (thread-local).
                String token = jwtTokenProvider.mintAccessToken(
                        fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
                io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
                String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
                contextProvider.setContext(new TenantContext(
                        fixture.tenantAId(), fixture.userAId(), jti, 0L,
                        Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                        "first-write-req-" + idx + "-" + UUID.randomUUID()));
                try {
                    AuditContext ctx = AuditContext.builder(
                                    "TEST.FIRST.WRITE." + idx, "TestResource", "CREATE")
                            .outcome(AuditOutcome.SUCCESS)
                            .resourceId("first-write-resource-" + idx + "-" + UUID.randomUUID())
                            .build();
                    auditService.record(ctx);
                } finally {
                    contextProvider.clear();
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

        // === Assertion 1: exactly 1 audit_chain_heads row ===
        Integer chainHeadCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_chain_heads WHERE tenant_id = ?",
                Integer.class, fixture.tenantAId());
        assertThat(chainHeadCount)
                .as("exactly 1 audit_chain_heads row must exist after %d concurrent first writes",
                        CONCURRENT_WRITES)
                .isEqualTo(1);

        // === Assertion 2: 20 audit events inserted ===
        Integer eventCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ?",
                Integer.class, fixture.tenantAId());
        assertThat(eventCount)
                .as("exactly %d audit events must be inserted for the fresh tenant",
                        CONCURRENT_WRITES)
                .isEqualTo(CONCURRENT_WRITES);

        // === Assertion 3: 20 distinct sequence_numbers in range 1..20 ===
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

        // === Assertion 4: integrity verification is valid ===
        setJwtClaimContext();
        AuditIntegrityVerificationService.VerificationResult result;
        try {
            result = integrityService.verifyChain(fixture.tenantAId());
        } finally {
            contextProvider.clear();
        }
        assertThat(result.valid())
                .as("integrity verification must report valid=true after %d concurrent first writes",
                        CONCURRENT_WRITES)
                .isTrue();
        assertThat(result.eventsChecked())
                .as("integrity verification must check all %d events", CONCURRENT_WRITES)
                .isEqualTo(CONCURRENT_WRITES);
        assertThat(result.firstBrokenEventId())
                .as("no broken event should be reported")
                .isNull();
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal storedHeadHash (chain head matches)")
                .isEqualTo(result.storedHeadHash());

        // === Assertion 5: chain head sequence advanced to 20 ===
        Long headSequence = fixtureJdbc.queryForObject(
                "SELECT head_sequence FROM audit_chain_heads WHERE tenant_id = ?",
                Long.class, fixture.tenantAId());
        assertThat(headSequence)
                .as("chain head sequence must be %d after %d writes", CONCURRENT_WRITES, CONCURRENT_WRITES)
                .isEqualTo((long) CONCURRENT_WRITES);
    }
}
