package com.sanad.platform.idempotency;

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

/**
 * Stage 05A.1 §17 — Verifies HTTP-level concurrent idempotency enforcement
 * on {@code POST /api/v1/organizations}.
 *
 * <p>Stage 05A.1 §13 — All HTTP requests use real JWTs through MockMvc.
 * No direct {@link com.sanad.platform.idempotency.service.IdempotencyService}
 * calls — the test exercises the full filter chain including the
 * {@link IdempotencyCommandInterceptor}.</p>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header.</p>
 *
 * <p>20 concurrent POST requests use the SAME Idempotency-Key and the SAME
 * body. The unique constraint on {@code (tenant_id, operation, route,
 * idempotency_key)} serializes them:</p>
 * <ul>
 *   <li>Exactly 1 thread wins the INSERT → 201 Created.</li>
 *   <li>The remaining 19 threads either see IN_PROGRESS (HTTP 409
 *       SANAD-IDEMP-003) while the winner is processing, or see REPLAY
 *       (HTTP 201 with Idempotency-Replayed: true) after the winner
 *       completes.</li>
 *   <li>Exactly 1 idempotency record exists in the DB afterward.</li>
 *   <li>Exactly 1 organization is created.</li>
 * </ul>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyConcurrentExecutionIntegrationTest {

    private static final int THREAD_COUNT = 20;
    private static final long TIMEOUT_SECONDS = 120;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenA;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("twentyConcurrentRequests_executeOnce: 20 concurrent POSTs with same key+body → 1 record, 1 org created")
    void twentyConcurrentRequests_executeOnce() throws Exception {
        String key = "concurrent-key-" + UUID.randomUUID();
        String uniqueName = "Concurrent Org " + UUID.randomUUID();
        String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"concurrent\"}";

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        AtomicInteger created201 = new AtomicInteger(0);
        AtomicInteger replayed201 = new AtomicInteger(0);
        AtomicInteger conflict409 = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                // Retry loop: if we get 409 IN_PROGRESS, wait briefly and retry
                // until we get 201 (winner or replay).
                for (int retry = 0; retry < 200; retry++) {
                    try {
                        int status = mockMvc.perform(post("/api/v1/organizations")
                                        .param("tenantId", fixture.tenantAId().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)
                                        .header("Authorization", "Bearer " + tokenA)
                                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                                .andReturn().getResponse().getStatus();
                        if (status == 201) {
                            // Could be the original 201 OR a replayed 201.
                            // Distinguish via the Idempotency-Replayed header.
                            boolean replayed = "true".equals(
                                    mockMvc.perform(post("/api/v1/organizations")
                                                    .param("tenantId", fixture.tenantAId().toString())
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(body)
                                                    .header("Authorization", "Bearer " + tokenA)
                                                    .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                                            .andReturn().getResponse()
                                            .getHeader(IdempotencyCommandInterceptor.IDEMPOTENCY_REPLAYED_HEADER));
                            // The second call above will always be a REPLAY now.
                            // Track the first-call status by incrementing the
                            // appropriate counter.
                            if (retry == 0) {
                                created201.incrementAndGet();
                            } else {
                                replayed201.incrementAndGet();
                            }
                            return status;
                        }
                        if (status == 409) {
                            // IN_PROGRESS — wait and retry.
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                            conflict409.incrementAndGet();
                            continue;
                        }
                        return status;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                throw new RuntimeException("Exhausted retries waiting for 201");
            }, executor);
            futures.add(future);
        }

        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        // Collect final statuses.
        Set<Integer> finalStatuses = new HashSet<>();
        for (CompletableFuture<Integer> f : futures) {
            finalStatuses.add(f.get());
        }

        // All 20 threads must eventually get a 201 (either as the winner or
        // as a replay after the winner completes).
        assertThat(finalStatuses)
                .as("all 20 threads must eventually return 201 (winner or replay)")
                .containsOnly(201);

        // Exactly 1 idempotency record must exist.
        String countSql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int recordCount = rs.getInt(1);
                assertThat(recordCount)
                        .as("exactly one idempotency record must exist for the concurrent key")
                        .isEqualTo(1);
            }
        }

        // Exactly 1 organization with the unique name must exist.
        String orgSql = "SELECT COUNT(*) FROM organizations "
                + "WHERE tenant_id = ? AND name = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(orgSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, uniqueName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int orgCount = rs.getInt(1);
                assertThat(orgCount)
                        .as("exactly one organization must be created for the concurrent key")
                        .isEqualTo(1);
            }
        }
    }
}
