package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.ActiveTeamManagerConflictException;
import com.sanad.platform.crm.ownership.domain.ArchiveBlockedWithActiveMembershipsException;
import com.sanad.platform.crm.ownership.domain.InvalidTeamManagerException;
import com.sanad.platform.crm.ownership.domain.MembershipRole;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.PrimaryMembershipConflictException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamArchivedException;
import com.sanad.platform.crm.ownership.domain.TeamMembership;
import com.sanad.platform.crm.ownership.domain.TeamMembershipNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.infrastructure.JdbcOwnershipUserValidationAdapter;
import com.sanad.platform.crm.ownership.infrastructure.JdbcQueueRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcSalesTeamRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcTeamMembershipRepository;
import com.sanad.platform.crm.ownership.infrastructure.JdbcTerritoryRepository;
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
class SalesTeamUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static SalesTeamUseCases useCases;
    private static List<String> auditActions;
    private static List<String> timelineEvents;

    private UUID tenantId;
    private UUID actorId;
    private UUID managerId;
    private UUID memberId;
    private UUID secondMemberId;
    private UUID inactiveUserId;

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
                "Docker is required for CRM-008B WP-03 PostgreSQL acceptance.");

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

        auditActions = new CopyOnWriteArrayList<>();
        timelineEvents = new CopyOnWriteArrayList<>();
        AuditPort audit = (tenant, actor, action, entityType, entityId, change, timestamp) ->
                auditActions.add(action + ":" + entityType + ":" + entityId);
        TimelineEventPort timeline = (tenant, subjectType, subjectId, eventType, summary,
                                      sourceType, sourceId, actor, occurredAt) ->
                timelineEvents.add(eventType + ":" + sourceId);

        useCases = new SalesTeamUseCases(
                new JdbcSalesTeamRepository(jdbc),
                new JdbcTeamMembershipRepository(jdbc),
                new JdbcOwnershipUserValidationAdapter(jdbc),
                new JdbcQueueRepository(jdbc),
                new JdbcTerritoryRepository(jdbc),
                audit,
                timeline,
                new ObjectMapper());
    }

    @BeforeEach
    void seedTenantAndUsers() {
        auditActions.clear();
        timelineEvents.clear();
        tenantId = createTenant("wp03");
        actorId = createUser(tenantId, "actor", "ACTIVE");
        managerId = createUser(tenantId, "manager", "ACTIVE");
        memberId = createUser(tenantId, "member", "ACTIVE");
        secondMemberId = createUser(tenantId, "member2", "ACTIVE");
        inactiveUserId = createUser(tenantId, "inactive", "INACTIVE");
    }

    @Test
    void teamCrudMembershipUpdateEndAndArchive_areAtomicAndAudited() {
        SalesTeam created = inTransaction(() -> useCases.createTeam(
                tenantId,
                actorId,
                new SalesTeamUseCases.CreateTeamCommand(
                        "TEAM-" + shortId(),
                        "Enterprise Sales",
                        "Primary enterprise team",
                        managerId,
                        null,
                        null)));

        assertThat(created.status()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(useCases.getTeam(tenantId, created.id()).managerUserId()).isEqualTo(managerId);
        assertThat(useCases.listTeams(tenantId, TeamStatus.ACTIVE))
                .extracting(SalesTeam::id)
                .contains(created.id());

        SalesTeam suspended = inTransaction(() -> useCases.updateTeam(
                tenantId,
                actorId,
                created.id(),
                new SalesTeamUseCases.UpdateTeamCommand(
                        "Enterprise Sales KSA",
                        null,
                        TeamStatus.SUSPENDED,
                        null,
                        null,
                        null)));
        assertThat(suspended.status()).isEqualTo(TeamStatus.SUSPENDED);
        assertThat(suspended.managerUserId()).isNull();

        TeamMembership membership = inTransaction(() -> useCases.addMembership(
                tenantId,
                actorId,
                created.id(),
                new SalesTeamUseCases.AddMembershipCommand(
                        memberId,
                        MembershipRole.SALES_REPRESENTATIVE,
                        true,
                        50,
                        "{\"language\":\"ar\"}")));
        assertThat(membership.isPrimary()).isTrue();
        assertThat(membership.metadata()).contains("language");

        TeamMembership updated = inTransaction(() -> useCases.updateMembership(
                tenantId,
                actorId,
                created.id(),
                membership.id(),
                new SalesTeamUseCases.UpdateMembershipCommand(
                        MembershipRole.ACCOUNT_MANAGER,
                        false,
                        125,
                        "{\"skills\":[\"enterprise\"]}")));
        assertThat(updated.role()).isEqualTo(MembershipRole.ACCOUNT_MANAGER);
        assertThat(updated.isPrimary()).isFalse();
        assertThat(updated.capacityMax()).isEqualTo(125);

        assertThatThrownBy(() -> inTransaction(() -> useCases.archiveTeam(
                tenantId, actorId, created.id())))
                .isInstanceOf(ArchiveBlockedWithActiveMembershipsException.class);

        TeamMembership ended = inTransaction(() -> useCases.endMembership(
                tenantId,
                actorId,
                created.id(),
                membership.id(),
                "MANUAL_REMOVAL"));
        assertThat(ended.status()).isEqualTo(MembershipStatus.ENDED);
        assertThat(ended.leftAt()).isNotNull();
        assertThat(ended.leftReason()).isEqualTo("MANUAL_REMOVAL");

        SalesTeam archived = inTransaction(() -> useCases.archiveTeam(
                tenantId, actorId, created.id()));
        assertThat(archived.status()).isEqualTo(TeamStatus.ARCHIVED);

        assertThatThrownBy(() -> inTransaction(() -> useCases.updateTeam(
                tenantId,
                actorId,
                created.id(),
                new SalesTeamUseCases.UpdateTeamCommand(
                        "Forbidden",
                        null,
                        TeamStatus.ACTIVE,
                        managerId,
                        null,
                        null))))
                .isInstanceOf(TeamArchivedException.class);

        assertThat(auditActions).anyMatch(action -> action.startsWith("CREATE:SALES_TEAM:"));
        assertThat(auditActions).anyMatch(action -> action.startsWith("UPDATE:SALES_TEAM:"));
        assertThat(auditActions).anyMatch(action -> action.startsWith("ARCHIVE:SALES_TEAM:"));
        assertThat(auditActions).anyMatch(action -> action.startsWith("CREATE:TEAM_MEMBERSHIP:"));
        assertThat(auditActions).anyMatch(action -> action.startsWith("UPDATE:TEAM_MEMBERSHIP:"));
        assertThat(auditActions).anyMatch(action -> action.startsWith("END:TEAM_MEMBERSHIP:"));
        assertThat(timelineEvents).anyMatch(event -> event.startsWith("crm.sales_team.created:"));
        assertThat(timelineEvents).anyMatch(event -> event.startsWith("crm.team_membership.ended:"));
    }

    @Test
    void managerMustBeActiveAndBelongToTheTenant() {
        assertThatThrownBy(() -> inTransaction(() -> useCases.createTeam(
                tenantId,
                actorId,
                createCommand("INACTIVE", inactiveUserId))))
                .isInstanceOf(InvalidTeamManagerException.class);

        UUID anotherTenant = createTenant("other");
        UUID otherManager = createUser(anotherTenant, "manager", "ACTIVE");
        assertThatThrownBy(() -> inTransaction(() -> useCases.createTeam(
                tenantId,
                actorId,
                createCommand("CROSS", otherManager))))
                .isInstanceOf(InvalidTeamManagerException.class);
    }

    @Test
    void concurrentTeamCreation_allowsOneActiveTeamPerManager() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(createTeamTask(barrier, "A"));
            Future<Boolean> second = executor.submit(createTeamTask(barrier, "B"));

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(new JdbcSalesTeamRepository(jdbc).findByManager(tenantId, managerId))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void primaryMembershipAndPathScoping_failClosed() {
        SalesTeam first = inTransaction(() -> useCases.createTeam(
                tenantId, actorId, createCommand("P1", managerId)));
        SalesTeam second = inTransaction(() -> useCases.createTeam(
                tenantId, actorId, createCommand("P2", null)));

        TeamMembership primary = inTransaction(() -> useCases.addMembership(
                tenantId,
                actorId,
                first.id(),
                new SalesTeamUseCases.AddMembershipCommand(
                        memberId,
                        MembershipRole.SALES_REPRESENTATIVE,
                        true,
                        50,
                        "{}")));

        assertThatThrownBy(() -> inTransaction(() -> useCases.addMembership(
                tenantId,
                actorId,
                second.id(),
                new SalesTeamUseCases.AddMembershipCommand(
                        memberId,
                        MembershipRole.ACCOUNT_MANAGER,
                        true,
                        50,
                        "{}"))))
                .isInstanceOf(PrimaryMembershipConflictException.class);

        assertThatThrownBy(() -> inTransaction(() -> useCases.updateMembership(
                tenantId,
                actorId,
                second.id(),
                primary.id(),
                new SalesTeamUseCases.UpdateMembershipCommand(
                        MembershipRole.SALES_MANAGER,
                        false,
                        50,
                        "{}"))))
                .isInstanceOf(TeamMembershipNotFoundException.class);

        UUID otherTenant = createTenant("isolated");
        assertThatThrownBy(() -> useCases.getTeam(otherTenant, first.id()))
                .isInstanceOf(com.sanad.platform.crm.ownership.domain.TeamNotFoundException.class);
    }

    @Test
    void membershipUserAndMetadataValidation_areStrict() {
        SalesTeam team = inTransaction(() -> useCases.createTeam(
                tenantId, actorId, createCommand("VALIDATE", managerId)));

        assertThatThrownBy(() -> inTransaction(() -> useCases.addMembership(
                tenantId,
                actorId,
                team.id(),
                new SalesTeamUseCases.AddMembershipCommand(
                        inactiveUserId,
                        MembershipRole.SALES_REPRESENTATIVE,
                        false,
                        50,
                        "{}"))))
                .isInstanceOf(com.sanad.platform.crm.ownership.domain.OwnershipDomainException.class);

        assertThatThrownBy(() -> inTransaction(() -> useCases.addMembership(
                tenantId,
                actorId,
                team.id(),
                new SalesTeamUseCases.AddMembershipCommand(
                        secondMemberId,
                        MembershipRole.SALES_REPRESENTATIVE,
                        false,
                        50,
                        "[]"))))
                .isInstanceOf(com.sanad.platform.crm.ownership.domain.OwnershipDomainException.class);
    }

    private Callable<Boolean> createTeamTask(CyclicBarrier barrier, String suffix) {
        return () -> {
            barrier.await();
            try {
                inTransaction(() -> useCases.createTeam(
                        tenantId,
                        actorId,
                        createCommand("CONCURRENT-" + suffix, managerId)));
                return true;
            } catch (ActiveTeamManagerConflictException expected) {
                return false;
            }
        };
    }

    private SalesTeamUseCases.CreateTeamCommand createCommand(String suffix, UUID manager) {
        return new SalesTeamUseCases.CreateTeamCommand(
                "TEAM-" + suffix + "-" + shortId(),
                "Team " + suffix,
                null,
                manager,
                null,
                null);
    }

    private UUID createTenant(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "Sales Team Test " + prefix)
                .addValue("subdomain", prefix + "-" + id.toString().substring(0, 8)));
        return id;
    }

    private UUID createUser(UUID tenant, String prefix, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users
                    (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :email, :displayName, :status, 'dummy',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenant)
                .addValue("email", prefix + "-" + id.toString().substring(0, 8) + "@test.example")
                .addValue("displayName", "User " + prefix)
                .addValue("status", status));
        return id;
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
