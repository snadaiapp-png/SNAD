package com.sanad.platform.audit;

import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies that twenty concurrent HTTP mutations append a single linear audit
 * chain for one tenant. Database inspection uses the fixture connection;
 * production integrity verification is executed under an explicit verified
 * tenant context so PostgreSQL RLS remains enforced.
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
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
    @Autowired private TenantContextProvider contextProvider;
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
        contextProvider.clear();
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("20 concurrent writes produce one valid linear audit chain")
    void twentyConcurrentAuditWrites_linearChain() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITES);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_WRITES; i++) {
                final int idx = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        startLatch.await();
                        String uniqueName = "Concurrency Org " + idx + " " + UUID.randomUUID();
                        String body = "{\"name\":\"" + uniqueName
                                + "\",\"description\":\"concurrent\"}";
                        String idempotencyKey = "conc-key-" + idx + "-" + UUID.randomUUID();
                        return mockMvc.perform(post("/api/v1/organizations")
                                        .param("tenantId", fixture.tenantAId().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)
                                        .header("Authorization", "Bearer " + tokenA)
                                        .header("Idempotency-Key", idempotencyKey))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        throw new RuntimeException("Concurrent request " + idx + " failed", e);
                    }
                }, executor));
            }

            startLatch.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        List<Integer> statuses = futures.stream().map(CompletableFuture::join).toList();
        assertThat(statuses)
                .as("all concurrent requests must complete with HTTP 201; statuses=%s", statuses)
                .containsOnly(201)
                .hasSize(CONCURRENT_WRITES);

        Integer eventCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ?",
                Integer.class, fixture.tenantAId());
        assertThat(eventCount).isEqualTo(CONCURRENT_WRITES);

        List<Long> sequences = new ArrayList<>();
        List<String> previousHashes = new ArrayList<>();
        List<String> eventHashes = new ArrayList<>();
        String chainSql = "SELECT sequence_number, previous_hash, event_hash "
                + "FROM audit_events WHERE tenant_id = ? ORDER BY sequence_number ASC";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(chainSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sequences.add(rs.getLong("sequence_number"));
                    previousHashes.add(rs.getString("previous_hash"));
                    eventHashes.add(rs.getString("event_hash"));
                }
            }
        }

        assertThat(sequences).hasSize(CONCURRENT_WRITES);
        assertThat(new HashSet<>(sequences)).hasSize(CONCURRENT_WRITES);
        for (long sequence = 1; sequence <= CONCURRENT_WRITES; sequence++) {
            assertThat(sequences).contains(sequence);
        }

        assertThat(new HashSet<>(previousHashes))
                .as("each event must have a distinct predecessor; no chain fork is allowed")
                .hasSize(CONCURRENT_WRITES);
        for (int i = 1; i < CONCURRENT_WRITES; i++) {
            assertThat(previousHashes.get(i))
                    .as("event %d must link to event %d", i + 1, i)
                    .isEqualTo(eventHashes.get(i - 1));
        }

        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(tokenA);
        assertThat(claims).isNotNull();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(),
                fixture.userAId(),
                claims.getId(),
                0L,
                Set.of(),
                TenantContext.TenantContextSource.JWT_CLAIM,
                "audit-integrity-verification-" + UUID.randomUUID()));

        AuditIntegrityVerificationService.VerificationResult result;
        try {
            result = integrityService.verifyChain(fixture.tenantAId());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.valid()).isTrue();
        assertThat(result.eventsChecked()).isEqualTo(CONCURRENT_WRITES);
        assertThat(result.firstBrokenEventId()).isNull();
        assertThat(result.calculatedHeadHash()).isEqualTo(result.storedHeadHash());

        Long headSequence = fixtureJdbc.queryForObject(
                "SELECT head_sequence FROM audit_chain_heads WHERE tenant_id = ?",
                Long.class, fixture.tenantAId());
        assertThat(headSequence).isEqualTo((long) CONCURRENT_WRITES);

        Integer orgCreateCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events "
                        + "WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE'",
                Integer.class, fixture.tenantAId());
        assertThat(orgCreateCount).isEqualTo(CONCURRENT_WRITES);
    }
}
