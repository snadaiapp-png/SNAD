package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentRule;
import com.sanad.platform.crm.ownership.domain.AssignmentStatus;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.ConcurrentClaimConflictException;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipHistory;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.QueueRecordType;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import com.sanad.platform.crm.ownership.domain.RuleStatus;
import com.sanad.platform.crm.ownership.domain.TerritoryCycleException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OwnershipPersistenceConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcAssignmentRepository assignments;
    private static JdbcOwnershipHistoryRepository history;
    private static JdbcQueueRepository queues;
    private static JdbcQueueMembershipRepository queueMemberships;
    private static JdbcAssignmentRuleRepository rules;
    private static JdbcTerritoryRepository territories;
    private static JdbcOwnershipWriteAdapter writes;

    private UUID tenantId;
    private UUID actorId;

    @BeforeAll
    static void migrateAndCreateAdapters() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is required for CRM-008B PostgreSQL 16 persistence acceptance.");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        history = new JdbcOwnershipHistoryRepository(jdbc);
        assignments = new JdbcAssignmentRepository(jdbc, history);
        queues = new JdbcQueueRepository(jdbc);
        queueMemberships = new JdbcQueueMembershipRepository(jdbc);
        rules = new JdbcAssignmentRuleRepository(jdbc);
        territories = new JdbcTerritoryRepository(jdbc);
        writes = new JdbcOwnershipWriteAdapter(
                assignments,
                new JdbcTransferRequestRepository(jdbc),
                queues,
                queueMemberships);
    }

    @BeforeEach
    void createTenant() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "CRM-008B Persistence Test")
                .addValue("subdomain", "crm008b-" + tenantId.toString().substring(0, 8)));
    }

    @Test
    void claimAndRelease_preserveExactlyOneActiveAssignmentAndHistory() {
        UUID queueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        createQueue(queueId, actorId, 10);
        createMembership(queueId, userId);
        createQueueAssignment(queueId, recordId, actorId);

        Assignment claimed = inTransaction(() -> writes.claimQueueItem(
                new OwnershipWritePort.QueueClaimCommand(
                        tenantId, queueId, AssignmentRecordType.LEAD,
                        recordId, userId, UUID.randomUUID())));

        assertThat(claimed.ownerType()).isEqualTo(OwnerType.USER);
        assertThat(claimed.ownerUserId()).isEqualTo(userId);
        assertThat(claimed.ownerQueueId()).isNull();
        assertSingleActive(recordId, OwnerType.USER, userId, null);

        inTransaction(() -> {
            writes.releaseQueueItem(new OwnershipWritePort.QueueReleaseCommand(
                    tenantId, queueId, AssignmentRecordType.LEAD,
                    recordId, userId, "Return to queue", UUID.randomUUID()));
            return null;
        });

        assertSingleActive(recordId, OwnerType.QUEUE, null, queueId);
        List<OwnershipHistory> changes = history.findByRecord(
                tenantId, AssignmentRecordType.LEAD, recordId, null, 10);
        assertThat(changes).extracting(OwnershipHistory::changeType)
                .contains(ChangeType.QUEUE_CLAIM, ChangeType.QUEUE_RELEASE);
    }

    @Test
    void concurrentQueueClaim_hasOneWinnerAndOneDomainConflict() throws Exception {
        UUID queueId = UUID.randomUUID();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        createQueue(queueId, actorId, 10);
        createMembership(queueId, firstUser);
        createMembership(queueId, secondUser);
        createQueueAssignment(queueId, recordId, actorId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(claimTask(barrier, queueId, recordId, firstUser));
            Future<Boolean> second = executor.submit(claimTask(barrier, queueId, recordId, secondUser));

            List<Boolean> results = List.of(first.get(), second.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);

            Assignment active = assignments.findActive(
                            tenantId, AssignmentRecordType.LEAD, recordId)
                    .orElseThrow();
            assertThat(active.ownerType()).isEqualTo(OwnerType.USER);
            assertThat(active.ownerUserId()).isIn(firstUser, secondUser);
            assertThat(active.ownerQueueId()).isNull();
            assertThat(activeAssignmentCount(recordId)).isEqualTo(1);
            assertThat(history.countByRecord(
                    tenantId, AssignmentRecordType.LEAD, recordId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void roundRobinCounter_atomicUpsertLosesNoIncrements() throws Exception {
        UUID ruleId = UUID.randomUUID();
        inTransaction(() -> rules.save(new AssignmentRule(
                ruleId, tenantId, "RR-" + ruleId.toString().substring(0, 8),
                1, RuleStatus.ACTIVE, null, null, actorId, actorId)));

        int workers = 40;
        CyclicBarrier barrier = new CyclicBarrier(workers);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return inTransaction(() -> rules.incrementCounter(tenantId, ruleId).counter());
                }));
            }
            for (Future<Long> future : futures) {
                assertThat(future.get()).isBetween(1L, (long) workers);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(rules.getOrCreateCounter(tenantId, ruleId).counter())
                .isEqualTo(workers);
    }

    @Test
    void territoryClosure_supportsDepthBeyondTwentyAndRejectsCycle() {
        List<UUID> ids = new ArrayList<>();
        UUID parentId = null;
        for (int depth = 0; depth < 25; depth++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("""
                    INSERT INTO crm_territories
                      (id, tenant_id, code, display_name, parent_id, status,
                       rule_type, rule_definition, priority, created_by, updated_by,
                       created_at, updated_at)
                    VALUES
                      (:id, :tenantId, :code, :name, :parentId, 'ACTIVE',
                       'GEOGRAPHIC', '{}'::jsonb, :priority, :actorId, :actorId,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", tenantId)
                    .addValue("code", "T-" + depth + "-" + id.toString().substring(0, 6))
                    .addValue("name", "Territory " + depth)
                    .addValue("parentId", parentId)
                    .addValue("priority", depth)
                    .addValue("actorId", actorId));
            parentId = id;
        }

        inTransaction(() -> {
            territories.rebuildClosure(tenantId);
            return null;
        });

        UUID rootId = ids.get(0);
        UUID deepestId = ids.get(ids.size() - 1);
        assertThat(territories.findAncestors(tenantId, deepestId)).hasSize(24);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND ancestor_id=:rootId
                   AND descendant_id=:deepestId
                   AND depth=24
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("rootId", rootId)
                .addValue("deepestId", deepestId), Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> inTransaction(() -> {
            territories.updateParent(tenantId, rootId, deepestId, actorId);
            return null;
        })).isInstanceOf(TerritoryCycleException.class);
    }

    private Callable<Boolean> claimTask(CyclicBarrier barrier,
                                        UUID queueId,
                                        UUID recordId,
                                        UUID userId) {
        return () -> {
            barrier.await();
            try {
                inTransaction(() -> writes.claimQueueItem(
                        new OwnershipWritePort.QueueClaimCommand(
                                tenantId, queueId, AssignmentRecordType.LEAD,
                                recordId, userId, UUID.randomUUID())));
                return true;
            } catch (ConcurrentClaimConflictException expected) {
                return false;
            }
        };
    }

    private void createQueue(UUID queueId, UUID defaultOwnerId, int capacity) {
        inTransaction(() -> queues.save(new Queue(
                queueId, tenantId,
                "Q-" + queueId.toString().substring(0, 8),
                "Lead Queue", QueueRecordType.LEAD, null,
                QueueStatus.ACTIVE, capacity, null, null, defaultOwnerId,
                null, null, actorId, actorId)));
    }

    private void createMembership(UUID queueId, UUID userId) {
        inTransaction(() -> queueMemberships.save(new QueueMembership(
                null, tenantId, queueId, userId, MembershipStatus.ACTIVE,
                null, null, null, null, null, actorId, actorId)));
    }

    private void createQueueAssignment(UUID queueId, UUID recordId, UUID legacyUserId) {
        Instant now = Instant.now();
        inTransaction(() -> assignments.save(new Assignment(
                null, tenantId, 0,
                AssignmentRecordType.LEAD.name(), recordId, legacyUserId, "OWNER",
                AssignmentStatus.ACTIVE, now, null, "Queued for claim",
                OwnerType.QUEUE, null, null, queueId,
                AssignmentRecordType.LEAD, recordId, null, legacyUserId,
                UUID.randomUUID(), null, now, null,
                now, now, legacyUserId, legacyUserId)));
    }

    private void assertSingleActive(UUID recordId,
                                    OwnerType expectedType,
                                    UUID expectedUserId,
                                    UUID expectedQueueId) {
        Assignment active = assignments.findActive(
                        tenantId, AssignmentRecordType.LEAD, recordId)
                .orElseThrow();
        assertThat(active.ownerType()).isEqualTo(expectedType);
        assertThat(active.ownerUserId()).isEqualTo(expectedUserId);
        assertThat(active.ownerQueueId()).isEqualTo(expectedQueueId);
        assertThat(activeAssignmentCount(recordId)).isEqualTo(1);
    }

    private long activeAssignmentCount(UUID recordId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_assignments
                 WHERE tenant_id=:tenantId
                   AND record_type='LEAD'
                   AND record_id=:recordId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordId", recordId), Long.class);
        return count != null ? count : 0L;
    }

    private static <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
