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
import java.util.ArrayList;
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

/**
 * Stage 05 §17 — Verifies that the database-level unique constraint on
 * {@code (tenant_id, operation, route, idempotency_key)} serializes
 * concurrent duplicate requests.
 *
 * <p>20 threads simultaneously call {@link IdempotencyService#reserveOrReplay}
 * with the same key and payload. Exactly 1 thread must get NEW (and
 * subsequently complete the record). The remaining 19 must get
 * IN_PROGRESS (and retry until REPLAY after the winner completes).</p>
 *
 * <p>After all threads complete, exactly 1 idempotency record must exist
 * with status COMPLETED, and all 20 threads must return the same
 * response body.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyConcurrentExecutionIntegrationTest {

    private static final int THREAD_COUNT = 20;
    private static final int MAX_RETRIES = 200;
    private static final long RETRY_SLEEP_MS = 50;

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

    @Test
    @DisplayName("twentyConcurrentRequests_executeOnce: 20 threads, same key+body → 1 record, all return same response")
    void twentyConcurrentRequests_executeOnce() throws Exception {
        String key = "concurrent-key-" + UUID.randomUUID();
        String body = "{\"name\":\"Concurrent Org\"}";
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";
        String expectedResponseBody = "{\"id\":\"org-concurrent-1\",\"name\":\"Concurrent Org\"}";
        String expectedHeaders = "Location:/api/v1/organizations/org-concurrent-1";

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger newCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                setTenantContext();
                try {
                    for (int retry = 0; retry < MAX_RETRIES; retry++) {
                        IdempotencyService.ReservationResult result =
                                idempotencyService.reserveOrReplay(
                                        key, operation, route, resourceType,
                                        method, body, null);
                        switch (result.type()) {
                            case NEW -> {
                                newCount.incrementAndGet();
                                // Simulate the business operation and store the response.
                                idempotencyService.complete(
                                        result.record().getId(), 201,
                                        expectedHeaders, expectedResponseBody);
                                return expectedResponseBody;
                            }
                            case REPLAY -> {
                                return result.record().getResponseBody();
                            }
                            case IN_PROGRESS -> {
                                // Wait and retry.
                                try {
                                    Thread.sleep(RETRY_SLEEP_MS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(e);
                                }
                            }
                            default -> throw new RuntimeException(
                                    "Unexpected reservation type: " + result.type());
                        }
                    }
                    throw new RuntimeException("Exhausted retries waiting for REPLAY");
                } finally {
                    contextProvider.clear();
                }
            }, executor);
            futures.add(future);
        }

        // Release all threads simultaneously.
        startLatch.countDown();

        // Wait for all futures to complete.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Collect results.
        Set<String> responseBodies = new java.util.HashSet<>();
        for (CompletableFuture<String> f : futures) {
            responseBodies.add(f.get());
        }

        // Assert: exactly 1 NEW reservation.
        assertThat(newCount.get())
                .as("exactly one thread must get NEW")
                .isEqualTo(1);

        // Assert: all 20 threads return the same response body.
        assertThat(responseBodies)
                .as("all 20 threads must return the same response body")
                .hasSize(1);
        assertThat(responseBodies.iterator().next())
                .isEqualTo(expectedResponseBody);

        // Assert: exactly 1 idempotency record with status COMPLETED.
        Integer completedCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records " +
                "WHERE tenant_id = ? AND idempotency_key = ? AND status = 'COMPLETED'",
                Integer.class, fixture.tenantAId(), key);
        assertThat(completedCount)
                .as("exactly one COMPLETED idempotency record must exist")
                .isEqualTo(1);

        Integer totalCount = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records " +
                "WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, fixture.tenantAId(), key);
        assertThat(totalCount)
                .as("exactly one idempotency record (any status) must exist")
                .isEqualTo(1);
    }
}
