package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentStatus;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipHistory;
import com.sanad.platform.crm.ownership.domain.OwnershipHistoryPage;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.QueueRecordType;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import com.sanad.platform.crm.ownership.domain.WorkloadSummary;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OwnershipReadQueueLifecyclePostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcQueueRepository queues;
    private static JdbcQueueMembershipRepository queueMemberships;
    private static JdbcOwnershipHistoryRepository history;
    private static JdbcAssignmentRepository assignments;
    private static JdbcOwnershipWriteAdapter writes;
    private static JdbcOwnershipReadAdapter reads;

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
                "Docker is required for CRM-008B read and queue lifecycle acceptance.");

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

        queues = new JdbcQueueRepository(jdbc);
        queueMemberships = new JdbcQueueMembershipRepository(jdbc);
        history = new JdbcOwnershipHistoryRepository(jdbc);
        assignments = new JdbcAssignmentRepository(jdbc, history);
        writes = new JdbcOwnershipWriteAdapter(
                assignments,
                new JdbcTransferRequestRepository(jdbc),
                queues,
                queueMemberships);
        reads = new JdbcOwnershipReadAdapter(jdbc);
    }

    @BeforeEach
    void createTenant() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        insertTenant(tenantId, "read-queue");
    }

    @Test
    void queueLifecycle_enforcesSameTenantEscalationAndTerminalArchive() {
        UUID escalationId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        inTransaction(() -> queues.save(queue(
                escalationId, tenantId, "ESC", QueueStatus.ACTIVE, null)));
        Queue created = inTransaction(() -> queues.save(queue(
                sourceId, tenantId, "SRC", QueueStatus.ACTIVE, escalationId)));

        assertThat(created.id()).isEqualTo(sourceId);
        assertThat(created.escalationTargetQueueId()).isEqualTo(escalationId);

        Queue draining = inTransaction(() -> queues.save(new Queue(
                created.id(), created.tenantId(), created.code(),
                "Source Queue Updated", created.recordType(), created.description(),
                QueueStatus.DRAINING, 25, created.slaMinutes(),
                created.escalationTargetQueueId(), created.defaultOwnerId(),
                created.createdAt(), created.updatedAt(), created.createdBy(), actorId)));
        assertThat(draining.status()).isEqualTo(QueueStatus.DRAINING);
        assertThat(draining.displayName()).isEqualTo("Source Queue Updated");

        inTransaction(() -> {
            queues.updateStatus(tenantId, sourceId, QueueStatus.ARCHIVED, actorId);
            return null;
        });
        assertThat(queues.findById(tenantId, sourceId).orElseThrow().status())
                .isEqualTo(QueueStatus.ARCHIVED);

        assertThatThrownBy(() -> inTransaction(() -> {
            queues.updateStatus(tenantId, sourceId, QueueStatus.ACTIVE, actorId);
            return null;
        })).isInstanceOf(OwnershipDomainException.class);

        UUID selfId = UUID.randomUUID();
        assertThatThrownBy(() -> inTransaction(() -> queues.save(queue(
                selfId, tenantId, "SELF", QueueStatus.ACTIVE, selfId))))
                .isInstanceOf(OwnershipDomainException.class);

        UUID otherTenant = UUID.randomUUID();
        insertTenant(otherTenant, "other");
        UUID otherQueueId = UUID.randomUUID();
        inTransaction(() -> queues.save(queue(
                otherQueueId, otherTenant, "OTHER", QueueStatus.ACTIVE, null)));

        assertThatThrownBy(() -> inTransaction(() -> queues.save(queue(
                UUID.randomUUID(), tenantId, "CROSS", QueueStatus.ACTIVE, otherQueueId))))
                .isInstanceOf(OwnershipDomainException.class);
    }

    @Test
    void workloadAndQueueClaimCounts_followLatestOwnershipTransition() {
        UUID queueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        inTransaction(() -> queues.save(queue(
                queueId, tenantId, "WORK", QueueStatus.ACTIVE, null)));
        inTransaction(() -> queueMemberships.save(new QueueMembership(
                null, tenantId, queueId, userId, MembershipStatus.ACTIVE,
                Instant.now(), null, null, null, null, actorId, actorId)));
        createQueueAssignment(queueId, recordId);

        inTransaction(() -> writes.claimQueueItem(
                new OwnershipWritePort.QueueClaimCommand(
                        tenantId, queueId, AssignmentRecordType.LEAD,
                        recordId, userId, UUID.randomUUID())));

        WorkloadSummary claimedWorkload = reads.findUserWorkload(tenantId, userId);
        assertThat(claimedWorkload.activeAssignments()).isEqualTo(1);
        assertThat(claimedWorkload.activeQueueItems()).isEqualTo(1);
        assertThat(reads.findUserQueueClaimCount(tenantId, userId)).isEqualTo(1);

        inTransaction(() -> {
            writes.releaseQueueItem(new OwnershipWritePort.QueueReleaseCommand(
                    tenantId, queueId, AssignmentRecordType.LEAD,
                    recordId, userId, "Release", UUID.randomUUID()));
            return null;
        });

        WorkloadSummary releasedWorkload = reads.findUserWorkload(tenantId, userId);
        assertThat(releasedWorkload.activeAssignments()).isZero();
        assertThat(releasedWorkload.activeQueueItems()).isZero();
        assertThat(reads.findUserQueueClaimCount(tenantId, userId)).isZero();
    }

    @Test
    void historyCursor_isStableAndTenantScoped() {
        UUID recordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant effectiveAt = Instant.parse("2026-07-23T00:00:00Z");

        inTransaction(() -> {
            for (int index = 0; index < 5; index++) {
                history.append(new OwnershipHistory(
                        UUID.randomUUID(), tenantId,
                        AssignmentRecordType.LEAD, recordId,
                        null, null, null, null,
                        OwnerType.USER, userId, null, null,
                        ChangeType.REASSIGN, TriggerSource.MANUAL, null,
                        actorId, "History " + index, UUID.randomUUID(),
                        effectiveAt.plusSeconds(index), null));
            }
            return null;
        });

        List<UUID> collected = new ArrayList<>();
        UUID cursor = null;
        do {
            OwnershipHistoryPage page = reads.findOwnershipHistory(
                    tenantId, AssignmentRecordType.LEAD, recordId, cursor, 2);
            page.entries().forEach(entry -> collected.add(entry.id()));
            cursor = page.nextCursor();
            if (!page.hasMore()) {
                break;
            }
        } while (true);

        assertThat(collected).hasSize(5);
        Set<UUID> unique = new HashSet<>(collected);
        assertThat(unique).hasSize(5);

        UUID otherTenant = UUID.randomUUID();
        insertTenant(otherTenant, "history-other");
        assertThat(reads.findOwnershipHistory(
                otherTenant, AssignmentRecordType.LEAD, recordId, null, 10).entries())
                .isEmpty();
        assertThat(reads.findActiveAssignment(
                otherTenant, AssignmentRecordType.LEAD, recordId)).isEmpty();

        assertThatThrownBy(() -> reads.findOwnershipHistory(
                tenantId, AssignmentRecordType.LEAD,
                recordId, UUID.randomUUID(), 2))
                .isInstanceOf(OwnershipDomainException.class);
    }

    private Queue queue(UUID id,
                        UUID tenant,
                        String code,
                        QueueStatus status,
                        UUID escalationTarget) {
        return new Queue(
                id, tenant, code + "-" + id.toString().substring(0, 6),
                code + " Queue", QueueRecordType.LEAD, null, status,
                20, 60, escalationTarget, actorId,
                null, null, actorId, actorId);
    }

    private void createQueueAssignment(UUID queueId, UUID recordId) {
        Instant now = Instant.now();
        inTransaction(() -> assignments.save(new Assignment(
                null, tenantId, 0,
                AssignmentRecordType.LEAD.name(), recordId, actorId, "OWNER",
                AssignmentStatus.ACTIVE, now, null, "Queued",
                OwnerType.QUEUE, null, null, queueId,
                AssignmentRecordType.LEAD, recordId, null, actorId,
                UUID.randomUUID(), null, now, null,
                now, now, actorId, actorId)));
    }

    private void insertTenant(UUID id, String prefix) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "CRM-008B " + prefix)
                .addValue("subdomain", prefix + "-" + id.toString().substring(0, 8)));
    }

    private static <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
