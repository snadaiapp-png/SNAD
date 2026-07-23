package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueCapacityExceededException;
import com.sanad.platform.crm.ownership.domain.QueueItemPage;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.QueueRecordType;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import com.sanad.platform.crm.ownership.infrastructure.JdbcAssignmentRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcOwnershipHistoryRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcOwnershipReadAdapter;
import com.sanad.platform.crm.ownership.infrastructure.JdbcOwnershipUserValidationAdapter;
import com.sanad.platform.crm.ownership.infrastructure.JdbcOwnershipWriteAdapter;
import com.sanad.platform.crm.ownership.infrastructure.JdbcQueueClaimIdempotencyAdapter;
import com.sanad.platform.crm.ownership.infrastructure.JdbcQueueMembershipRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcQueueRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcTransferRequestRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class QueueUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static QueueUseCases useCases;
    private static OwnershipWritePort ownershipWritePort;
    private static JdbcAssignmentRepository assignmentRepository;
    private static List<String> auditActions;
    private static List<String> timelineEvents;

    private UUID tenantId;
    private UUID actorId;
    private UUID memberId;

    @BeforeAll
    static void migrateAndCreateService() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is required for CRM-008B WP-04 PostgreSQL acceptance.");

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

        ObjectMapper mapper = new ObjectMapper();
        JdbcQueueRepository queueRepository = new JdbcQueueRepository(jdbc);
        JdbcQueueMembershipRepository membershipRepository = new JdbcQueueMembershipRepository(jdbc);
        JdbcOwnershipHistoryRepository historyRepository = new JdbcOwnershipHistoryRepository(jdbc);
        assignmentRepository = new JdbcAssignmentRepository(jdbc, historyRepository);
        JdbcOwnershipReadAdapter readAdapter = new JdbcOwnershipReadAdapter(jdbc);
        ownershipWritePort = new JdbcOwnershipWriteAdapter(
                assignmentRepository,
                new JdbcTransferRequestRepository(jdbc),
                queueRepository,
                membershipRepository);

        auditActions = new CopyOnWriteArrayList<>();
        timelineEvents = new CopyOnWriteArrayList<>();
        AuditPort audit = (tenant, actor, action, entityType, entityId, change, timestamp) ->
                auditActions.add(action + ":" + entityType + ":" + entityId);
        TimelineEventPort timeline = (tenant, subjectType, subjectId, eventType, summary,
                                      sourceType, sourceId, actor, occurredAt) ->
                timelineEvents.add(eventType + ":" + sourceId);

        useCases = new QueueUseCases(
                queueRepository,
                membershipRepository,
                assignmentRepository,
                readAdapter,
                ownershipWritePort,
                new JdbcQueueClaimIdempotencyAdapter(jdbc, mapper),
                new JdbcOwnershipUserValidationAdapter(jdbc),
                audit,
                timeline,
                mapper);
    }

    @BeforeEach
    void seedTenantAndUsers() {
        auditActions.clear();
        timelineEvents.clear();
        tenantId = createTenant("wp04");
        actorId = createUser(tenantId, "actor", "ACTIVE");
        memberId = createUser(tenantId, "member", "ACTIVE");
    }

    @Test
    void queueLifecycleBoundedItemsClaimReplayReleaseAndArchive_areGoverned() {
        Queue queue = inTransaction(() -> useCases.createQueue(
                tenantId,
                actorId,
                new QueueUseCases.CreateQueueCommand(
                        "LEADS-" + shortId(),
                        "Inbound Leads",
                        QueueRecordType.LEAD,
                        "Inbound claim queue",
                        2,
                        60,
                        null,
                        actorId)));
        QueueMembership membership = inTransaction(() -> useCases.addMembership(
                tenantId, actorId, queue.id(), memberId));
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);

        UUID firstRecord = UUID.randomUUID();
        UUID secondRecord = UUID.randomUUID();
        UUID thirdRecord = UUID.randomUUID();
        enqueue(queue, firstRecord);
        enqueue(queue, secondRecord);
        enqueue(queue, thirdRecord);

        QueueItemPage firstPage = useCases.listItems(tenantId, queue.id(), null, 1);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.hasMore()).isTrue();
        QueueItemPage secondPage = useCases.listItems(
                tenantId, queue.id(), firstPage.nextCursor(), 1);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().get(0).assignmentId())
                .isNotEqualTo(firstPage.items().get(0).assignmentId());

        UUID firstKey = UUID.randomUUID();
        QueueUseCases.ClaimResult firstClaim = inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD,
                        firstRecord,
                        firstKey,
                        UUID.randomUUID())));
        assertThat(firstClaim.replayed()).isFalse();
        assertThat(firstClaim.assignment().ownerType()).isEqualTo(OwnerType.USER);
        assertThat(firstClaim.assignment().ownerUserId()).isEqualTo(memberId);

        QueueUseCases.ClaimResult replay = inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD,
                        firstRecord,
                        firstKey,
                        UUID.randomUUID())));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.assignment().id()).isEqualTo(firstClaim.assignment().id());

        inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD,
                        secondRecord,
                        UUID.randomUUID(),
                        UUID.randomUUID())));
        assertThatThrownBy(() -> inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD,
                        thirdRecord,
                        UUID.randomUUID(),
                        UUID.randomUUID()))))
                .isInstanceOf(QueueCapacityExceededException.class);

        Queue draining = inTransaction(() -> useCases.updateQueue(
                tenantId,
                actorId,
                queue.id(),
                new QueueUseCases.UpdateQueueCommand(
                        null,
                        false,
                        null,
                        QueueStatus.DRAINING,
                        null,
                        false,
                        null,
                        false,
                        null,
                        false,
                        null)));
        assertThat(draining.status()).isEqualTo(QueueStatus.DRAINING);

        assertThatThrownBy(() -> inTransaction(() -> ownershipWritePort.assign(
                queueAssignment(queue, UUID.randomUUID()))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("does not accept new items");

        Assignment released = inTransaction(() -> useCases.release(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ReleaseCommand(
                        AssignmentRecordType.LEAD,
                        firstRecord,
                        "RETURN_TO_QUEUE",
                        UUID.randomUUID())));
        assertThat(released.ownerType()).isEqualTo(OwnerType.QUEUE);
        assertThat(released.ownerQueueId()).isEqualTo(queue.id());

        QueueUseCases.ClaimResult drainingClaim = inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD,
                        thirdRecord,
                        UUID.randomUUID(),
                        UUID.randomUUID())));
        assertThat(drainingClaim.assignment().ownerUserId()).isEqualTo(memberId);

        assertThatThrownBy(() -> inTransaction(() -> useCases.removeMembership(
                tenantId, actorId, queue.id(), membership.id(), "MANUAL_REMOVAL")))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("Release active claimed items");

        endActive(tenantId, AssignmentRecordType.LEAD, firstRecord);
        endActive(tenantId, AssignmentRecordType.LEAD, secondRecord);
        endActive(tenantId, AssignmentRecordType.LEAD, thirdRecord);
        QueueMembership removed = inTransaction(() -> useCases.removeMembership(
                tenantId, actorId, queue.id(), membership.id(), "MANUAL_REMOVAL"));
        assertThat(removed.status()).isEqualTo(MembershipStatus.REMOVED);

        Queue archived = inTransaction(() -> useCases.archiveQueue(
                tenantId, actorId, queue.id()));
        assertThat(archived.status()).isEqualTo(QueueStatus.ARCHIVED);
        assertThatThrownBy(() -> inTransaction(() -> useCases.addMembership(
                tenantId, actorId, queue.id(), memberId)))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("archived queue");

        assertThat(auditActions).anyMatch(value -> value.startsWith("CREATE:QUEUE:"));
        assertThat(auditActions).anyMatch(value -> value.startsWith("CLAIM:QUEUE_ITEM:"));
        assertThat(auditActions).anyMatch(value -> value.startsWith("RELEASE:QUEUE_ITEM:"));
        assertThat(timelineEvents).anyMatch(value -> value.startsWith("crm.queue_item.claimed:"));
    }

    @Test
    void concurrentClaims_respectPerQueueCapacityAndProduceOneWinner() throws Exception {
        Queue queue = inTransaction(() -> useCases.createQueue(
                tenantId,
                actorId,
                new QueueUseCases.CreateQueueCommand(
                        "CAPACITY-" + shortId(),
                        "Capacity Queue",
                        QueueRecordType.LEAD,
                        null,
                        1,
                        null,
                        null,
                        actorId)));
        inTransaction(() -> useCases.addMembership(tenantId, actorId, queue.id(), memberId));
        UUID firstRecord = UUID.randomUUID();
        UUID secondRecord = UUID.randomUUID();
        enqueue(queue, firstRecord);
        enqueue(queue, secondRecord);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(claimTask(barrier, queue, firstRecord));
            Future<Boolean> second = executor.submit(claimTask(barrier, queue, secondRecord));
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(assignmentRepository.countActiveQueueClaims(
                    tenantId, queue.id(), memberId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void idempotencyAndTenantScoping_failClosed() {
        Queue queue = inTransaction(() -> useCases.createQueue(
                tenantId,
                actorId,
                new QueueUseCases.CreateQueueCommand(
                        "IDEMP-" + shortId(),
                        "Idempotency Queue",
                        QueueRecordType.LEAD,
                        null,
                        5,
                        null,
                        null,
                        actorId)));
        inTransaction(() -> useCases.addMembership(tenantId, actorId, queue.id(), memberId));
        UUID firstRecord = UUID.randomUUID();
        UUID secondRecord = UUID.randomUUID();
        enqueue(queue, firstRecord);
        enqueue(queue, secondRecord);
        UUID key = UUID.randomUUID();
        inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD, firstRecord, key, UUID.randomUUID())));

        assertThatThrownBy(() -> inTransaction(() -> useCases.claim(
                tenantId,
                memberId,
                queue.id(),
                new QueueUseCases.ClaimCommand(
                        AssignmentRecordType.LEAD, secondRecord, key, UUID.randomUUID()))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("different queue claim request");

        UUID otherTenant = createTenant("other");
        assertThatThrownBy(() -> useCases.getQueue(otherTenant, queue.id()))
                .isInstanceOf(com.sanad.platform.crm.ownership.domain.QueueNotFoundException.class);
        assertThatThrownBy(() -> useCases.listItems(
                otherTenant, queue.id(), null, 25))
                .isInstanceOf(com.sanad.platform.crm.ownership.domain.QueueNotFoundException.class);
    }

    private Callable<Boolean> claimTask(CyclicBarrier barrier,
                                        Queue queue,
                                        UUID recordId) {
        return () -> {
            barrier.await();
            try {
                inTransaction(() -> useCases.claim(
                        tenantId,
                        memberId,
                        queue.id(),
                        new QueueUseCases.ClaimCommand(
                                AssignmentRecordType.LEAD,
                                recordId,
                                UUID.randomUUID(),
                                UUID.randomUUID())));
                return true;
            } catch (QueueCapacityExceededException expected) {
                return false;
            }
        };
    }

    private void enqueue(Queue queue, UUID recordId) {
        inTransaction(() -> ownershipWritePort.assign(queueAssignment(queue, recordId)));
    }

    private OwnershipWritePort.AssignmentCommand queueAssignment(Queue queue, UUID recordId) {
        return new OwnershipWritePort.AssignmentCommand(
                queue.tenantId(),
                AssignmentRecordType.LEAD,
                recordId,
                OwnerType.QUEUE,
                null,
                null,
                queue.id(),
                actorId,
                null,
                "QUEUE_SEED",
                UUID.randomUUID(),
                TriggerSource.MANUAL);
    }

    private void endActive(UUID tenant,
                           AssignmentRecordType recordType,
                           UUID recordId) {
        Assignment active = assignmentRepository.findActive(tenant, recordType, recordId)
                .orElseThrow();
        inTransaction(() -> {
            assignmentRepository.endAssignment(tenant, active.id(), actorId, "TEST_DRAIN");
            return null;
        });
    }

    private UUID createTenant(String prefix) {
        UUID tenant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenant)
                .addValue("name", "Queue Test " + prefix)
                .addValue("subdomain", prefix + "-" + tenant.toString().substring(0, 8)));
        return tenant;
    }

    private UUID createUser(UUID tenant, String prefix, String status) {
        UUID user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users
                  (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :email, :displayName, :status, 'dummy',
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", user)
                .addValue("tenantId", tenant)
                .addValue("email", prefix + "-" + user.toString().substring(0, 8) + "@test.example")
                .addValue("displayName", "Queue User " + prefix)
                .addValue("status", status));
        return user;
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> callable) {
        return transactions.execute(status -> {
            try {
                return callable.call();
            } catch (RuntimeException runtime) {
                throw runtime;
            } catch (Exception checked) {
                throw new IllegalStateException(checked);
            }
        });
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
