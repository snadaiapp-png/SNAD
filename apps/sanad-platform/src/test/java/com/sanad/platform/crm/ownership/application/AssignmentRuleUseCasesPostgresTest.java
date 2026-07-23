package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import com.sanad.platform.crm.ownership.infrastructure.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AssignmentRuleUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcAssignmentRuleRepository ruleRepository;
    private static JdbcAssignmentRepository assignmentRepository;
    private static JdbcSalesTeamRepository teamRepository;
    private static JdbcTeamMembershipRepository membershipRepository;
    private static JdbcQueueRepository queueRepository;
    private static JdbcTerritoryRepository territoryRepository;
    private static JdbcTerritoryAssignmentRepository territoryAssignmentRepository;
    private static AssignmentRuleUseCases useCases;
    private static TerritoryUseCases territoryUseCases;

    private UUID tenantId;
    private UUID actorId;
    private UUID firstUser;
    private UUID secondUser;
    private SalesTeam team;
    private Queue queue;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for WP-06 PostgreSQL acceptance");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ruleRepository = new JdbcAssignmentRuleRepository(jdbc);
        JdbcOwnershipHistoryRepository history = new JdbcOwnershipHistoryRepository(jdbc);
        assignmentRepository = new JdbcAssignmentRepository(jdbc, history);
        teamRepository = new JdbcSalesTeamRepository(jdbc);
        membershipRepository = new JdbcTeamMembershipRepository(jdbc);
        queueRepository = new JdbcQueueRepository(jdbc);
        territoryRepository = new JdbcTerritoryRepository(jdbc);
        territoryAssignmentRepository = new JdbcTerritoryAssignmentRepository(jdbc);
        JdbcOwnershipUserValidationAdapter users = new JdbcOwnershipUserValidationAdapter(jdbc);
        AuditPort audit = (tenant, actor, action, type, id, change, at) -> {};
        TimelineEventPort timeline = (tenant, type, id, event, summary, source, sourceId, actor, at) -> {};
        ObjectMapper mapper = new ObjectMapper();
        territoryUseCases = new TerritoryUseCases(
                territoryRepository, territoryAssignmentRepository, teamRepository,
                users, audit, timeline, mapper);
        useCases = new AssignmentRuleUseCases(
                ruleRepository, teamRepository, membershipRepository, queueRepository,
                assignmentRepository, territoryUseCases, users, audit, timeline, mapper);
    }

    @BeforeEach
    void seed() {
        tenantId = createTenant();
        actorId = createUser(tenantId, "actor");
        firstUser = createUser(tenantId, "first");
        secondUser = createUser(tenantId, "second");
        team = tx(() -> teamRepository.save(new SalesTeam(
                null, tenantId, "TEAM-" + shortId(), "Rule Team", null,
                TeamStatus.ACTIVE, actorId, null, null, null, null, actorId, actorId)));
        tx(() -> membershipRepository.save(membership(firstUser, 10)));
        tx(() -> membershipRepository.save(membership(secondUser, 10)));
        queue = tx(() -> queueRepository.save(new Queue(
                null, tenantId, "QUEUE-" + shortId(), "Lead Queue", QueueRecordType.LEAD,
                null, QueueStatus.ACTIVE, 10, null, null, actorId,
                null, null, actorId, actorId)));
    }

    @Test
    void deterministicPriorityConditionsAndVersionActivation_work() {
        AssignmentRule lowPriority = tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "DIRECT-" + shortId(), definition(
                                "Direct", 20, "{\"source\":\"web\"}",
                                DistributionMethod.DIRECT_OWNER, firstUser, null, null, null))));
        AssignmentRule highPriority = tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "QUEUE-" + shortId(), definition(
                                "Queue", 10, "{\"source\":\"web\"}",
                                DistributionMethod.QUEUE_ASSIGNMENT, null, null, queue.id(), null))));

        AssignmentDecision decision = useCases.simulate(
                tenantId,
                new AssignmentRuleUseCases.EvaluationInput(
                        AssignmentRecordType.LEAD, Map.of("source", "web"), List.of()));
        assertThat(decision.ruleId()).isEqualTo(highPriority.id());
        assertThat(decision.ownerType()).isEqualTo(OwnerType.QUEUE);
        assertThat(decision.ownerId()).isEqualTo(queue.id());
        assertThat(decision.trace()).anyMatch(value -> value.contains("priority=10"));

        AssignmentDecision noMatch = useCases.simulate(
                tenantId,
                new AssignmentRuleUseCases.EvaluationInput(
                        AssignmentRecordType.LEAD, Map.of("source", "referral"), List.of()));
        assertThat(noMatch.matched()).isFalse();

        AssignmentRuleUseCases.VersionDefinition versionTwo = definition(
                "Direct V2", 5, "{\"source\":\"partner\"}",
                DistributionMethod.DIRECT_OWNER, secondUser, null, null, null);
        AssignmentRuleVersion created = tx(() -> useCases.createVersion(
                tenantId, actorId, lowPriority.id(), versionTwo));
        assertThat(created.status()).isEqualTo(RuleStatus.INACTIVE);
        assertThat(created.version()).isEqualTo(2);
        AssignmentRuleVersion active = tx(() -> useCases.activateVersion(
                tenantId, actorId, lowPriority.id(), 2));
        assertThat(active.version()).isEqualTo(2);
        assertThat(ruleRepository.findActiveVersion(tenantId, lowPriority.id()))
                .get().extracting(AssignmentRuleVersion::version).isEqualTo(2);
        assertThat(useCases.listVersions(tenantId, lowPriority.id()))
                .extracting(AssignmentRuleVersion::version).containsExactly(2, 1);
    }

    @Test
    void roundRobinSimulationDoesNotMutateAndDecisionsRotateAtomically() {
        AssignmentRule rule = tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "RR-" + shortId(), definition(
                                "Round Robin", 1, "{}", DistributionMethod.ROUND_ROBIN,
                                null, team.id(), null, null))));

        AssignmentDecision simulationOne = useCases.simulate(tenantId, input());
        AssignmentDecision simulationTwo = useCases.simulate(tenantId, input());
        assertThat(simulationTwo.ownerId()).isEqualTo(simulationOne.ownerId());
        assertThat(ruleRepository.findCounter(tenantId, rule.id())).isEmpty();

        AssignmentDecision first = tx(() -> useCases.decide(tenantId, input()));
        AssignmentDecision second = tx(() -> useCases.decide(tenantId, input()));
        AssignmentDecision third = tx(() -> useCases.decide(tenantId, input()));
        assertThat(first.ownerId()).isNotEqualTo(second.ownerId());
        assertThat(third.ownerId()).isEqualTo(first.ownerId());
        assertThat(ruleRepository.findCounter(tenantId, rule.id())).get()
                .extracting(AssignmentRuleCounter::counter).isEqualTo(3L);
    }

    @Test
    void leastLoadedTeamAndFallbackAreDeterministic() {
        tx(() -> assignmentRepository.save(activeAssignment(firstUser, UUID.randomUUID())));
        tx(() -> assignmentRepository.save(activeAssignment(firstUser, UUID.randomUUID())));
        tx(() -> assignmentRepository.save(activeAssignment(secondUser, UUID.randomUUID())));

        tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "LEAST-" + shortId(), definition(
                                "Least Loaded", 1, "{}", DistributionMethod.LEAST_LOADED,
                                null, team.id(), null, actorId))));
        AssignmentDecision decision = useCases.simulate(tenantId, input());
        assertThat(decision.ownerType()).isEqualTo(OwnerType.USER);
        assertThat(decision.ownerId()).isEqualTo(secondUser);
        assertThat(decision.trace()).anyMatch(value -> value.contains("least_loaded_user"));
    }

    @Test
    void territoryStrategyAndUnsupportedMethodsFailClosed() {
        Territory territory = tx(() -> territoryUseCases.create(
                tenantId, actorId,
                new TerritoryUseCases.CreateCommand(
                        "T-" + shortId(), "Saudi", null, null,
                        TerritoryRuleType.GEOGRAPHIC, "{\"country\":\"SA\"}", 10)));
        tx(() -> territoryUseCases.assign(
                tenantId, actorId, territory.id(),
                new TerritoryUseCases.AssignCommand(
                        AssigneeType.USER, firstUser, TerritoryAssignmentRole.PRIMARY,
                        50, null, null)));
        tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "TERR-" + shortId(), definition(
                                "Territory", 1, "{}", DistributionMethod.TERRITORY_BASED,
                                null, null, null, null))));
        AssignmentDecision decision = useCases.simulate(
                tenantId,
                new AssignmentRuleUseCases.EvaluationInput(
                        AssignmentRecordType.LEAD, Map.of(), List.of(territory.id())));
        assertThat(decision.ownerId()).isEqualTo(firstUser);

        assertThatThrownBy(() -> tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "WEIGHT-" + shortId(), definition(
                                "Weighted", 2, "{}", DistributionMethod.WEIGHTED,
                                null, team.id(), null, null)))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("outside approved");
    }

    @Test
    void crossTenantTargetsAndNonObjectConditionsAreRejected() {
        UUID otherTenant = createTenant();
        UUID otherUser = createUser(otherTenant, "other");
        assertThatThrownBy(() -> tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "CROSS-" + shortId(), definition(
                                "Cross", 1, "{}", DistributionMethod.DIRECT_OWNER,
                                otherUser, null, null, null)))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("same-tenant");
        assertThatThrownBy(() -> tx(() -> useCases.createRule(
                tenantId, actorId,
                new AssignmentRuleUseCases.CreateRuleCommand(
                        "JSON-" + shortId(), definition(
                                "JSON", 1, "[]", DistributionMethod.DIRECT_OWNER,
                                firstUser, null, null, null)))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("JSON object");
    }

    private AssignmentRuleUseCases.VersionDefinition definition(
            String name, int priority, String conditions, DistributionMethod method,
            UUID owner, UUID teamId, UUID queueId, UUID fallback) {
        return new AssignmentRuleUseCases.VersionDefinition(
                name, null, AssignmentRecordType.LEAD, priority, conditions, method,
                owner, teamId, queueId, fallback, Instant.now(), null);
    }

    private AssignmentRuleUseCases.EvaluationInput input() {
        return new AssignmentRuleUseCases.EvaluationInput(
                AssignmentRecordType.LEAD, Map.of(), List.of());
    }

    private TeamMembership membership(UUID user, int capacity) {
        return new TeamMembership(
                null, tenantId, team.id(), user, MembershipRole.SALES_REPRESENTATIVE,
                false, MembershipStatus.ACTIVE, Instant.now(), null, null,
                capacity, "{}", null, null, actorId, actorId);
    }

    private Assignment activeAssignment(UUID owner, UUID recordId) {
        Instant now = Instant.now();
        return new Assignment(
                null, tenantId, 0, AssignmentRecordType.LEAD.name(), recordId, owner, "OWNER",
                AssignmentStatus.ACTIVE, now, null, "LOAD",
                OwnerType.USER, owner, null, null, AssignmentRecordType.LEAD, recordId,
                null, actorId, UUID.randomUUID(), null, now, null,
                now, now, actorId, actorId);
    }

    private UUID createTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, 'Rule Test', :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("subdomain", "rules-" + shortId()));
        return id;
    }

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users
                  (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :tenantId, :email, :name, 'ACTIVE', 'dummy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenant)
                .addValue("email", prefix + "-" + shortId() + "@test.example")
                .addValue("name", prefix));
        return id;
    }

    private <T> T tx(java.util.concurrent.Callable<T> callable) {
        return transactions.execute(status -> {
            try { return callable.call(); }
            catch (RuntimeException runtime) { throw runtime; }
            catch (Exception checked) { throw new IllegalStateException(checked); }
        });
    }

    private String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
}
