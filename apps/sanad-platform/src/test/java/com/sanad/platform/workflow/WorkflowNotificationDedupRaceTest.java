package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowNotificationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 2 / Task 15 — notification intent deduplication under concurrency.
 *
 * <p>Reproduces the read-then-insert race in notification enqueue: without a
 * database-level uniqueness guarantee on (tenant_id, deduplication_key),
 * concurrent identical enqueues insert multiple durable intents, which a
 * delivery worker would deliver multiple times. The authoritative delivery
 * model is at-least-once + idempotency: duplicate intents must collapse to
 * one durable intent per (tenant, deduplication key).</p>
 */
class WorkflowNotificationDedupRaceTest {

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static WorkflowNotificationService notifications;
    private static boolean postgresAvailable;

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String pass = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        try {
            Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            postgresAvailable = true;
        } catch (Exception unavailable) {
            postgresAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");

        DataSource dataSource = new DriverManagerDataSource(url, user, pass);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        notifications = new WorkflowNotificationService(jdbc);
    }

    /** Tenant + user + published Y2 definition + one running instance. */
    private record Fixture(UUID tenant, UUID user, UUID definition, UUID instance) {}

    private Fixture fixture(String tag) {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", tenant, "T15Dedup-" + tag,
                "t15-dedup-" + tag + "-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                        + "created_at,updated_at) VALUES (?, ?, ?, 'Dedup User', 'ACTIVE', 'dummy', ?, ?)",
                user, tenant, "t15-dedup-" + user.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF15-DEDUP', 'Dedup', 'GENERAL', 1, 'ACTIVE',
                          'EVENT', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definition, tenant, definition, user, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, engine_generation,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                          CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instance, tenant, definition, user, now, now);
        return new Fixture(tenant, user, definition, instance);
    }

    @Test
    void concurrentIdenticalEnqueuesCollapseToOneDurableIntent() throws Exception {
        int rounds = 10;
        int racers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            for (int round = 0; round < rounds; round++) {
                Fixture fx = fixture("race-" + round);
                String dedupKey = "task-assigned-" + UUID.randomUUID();
                CyclicBarrier gate = new CyclicBarrier(racers);
                List<Future<UUID>> results = new ArrayList<>();
                for (int r = 0; r < racers; r++) {
                    results.add(pool.submit((Callable<UUID>) () -> {
                        gate.await();
                        // Race contract: a controlled duplicate-delivery signal or a
                        // transient speculative-insert deadlock retry-loser re-enqueues
                        // in a fresh transaction and lands on the replay path.
                        RuntimeException last = null;
                        for (int attempt = 0; attempt < 5; attempt++) {
                            try {
                                return tx.execute(s -> notifications.enqueue(fx.tenant(), "TASK_ASSIGNED",
                                        fx.instance(), null, fx.user(), "IN_APP", dedupKey));
                            } catch (IllegalStateException controlled) {
                                last = controlled;
                            } catch (org.springframework.dao.CannotAcquireLockException transient_) {
                                last = transient_;
                            }
                        }
                        throw last;
                    }));
                }
                java.util.Set<UUID> resolved = new java.util.HashSet<>();
                for (Future<UUID> f : results) {
                    resolved.add(f.get(30, TimeUnit.SECONDS));
                }
                UUID durableId = jdbc.queryForObject("""
                        SELECT id FROM workflow_notification_intents
                        WHERE tenant_id = ? AND deduplication_key = ?
                        """, UUID.class, fx.tenant(), dedupKey);
                assertThat(resolved)
                        .as("every successful caller resolves the single durable intent")
                        .containsExactly(durableId);
                Integer durableIntents = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM workflow_notification_intents
                        WHERE tenant_id = ? AND deduplication_key = ?
                        """, Integer.class, fx.tenant(), dedupKey);
                assertThat(durableIntents)
                        .as("round %s must keep exactly one durable intent for key %s", round, dedupKey)
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sequentialReplayReusesPriorIntent() {
        Fixture fx = fixture("sequential");
        String dedupKey = "task-assigned-" + UUID.randomUUID();
        UUID first = notifications.enqueue(fx.tenant(), "TASK_ASSIGNED", fx.instance(),
                null, fx.user(), "IN_APP", dedupKey);
        UUID replay = notifications.enqueue(fx.tenant(), "TASK_ASSIGNED", fx.instance(),
                null, fx.user(), "IN_APP", dedupKey);
        assertThat(replay).isEqualTo(first);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                WHERE tenant_id = ? AND deduplication_key = ?
                """, Integer.class, fx.tenant(), dedupKey);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void distinctKeysNeverCollapse() {
        Fixture fx = fixture("distinct");
        UUID a = notifications.enqueue(fx.tenant(), "TASK_ASSIGNED", fx.instance(),
                null, fx.user(), "IN_APP", "key-a-" + UUID.randomUUID());
        UUID b = notifications.enqueue(fx.tenant(), "TASK_ASSIGNED", fx.instance(),
                null, fx.user(), "IN_APP", "key-b-" + UUID.randomUUID());
        assertThat(a).isNotEqualTo(b);
    }
}
