package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.ownership.domain.AssigneeType;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentRule;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleVersion;
import com.sanad.platform.crm.ownership.domain.DistributionMethod;
import com.sanad.platform.crm.ownership.domain.MembershipRole;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.RuleStatus;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamMembership;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.domain.Territory;
import com.sanad.platform.crm.ownership.domain.TerritoryAssignment;
import com.sanad.platform.crm.ownership.domain.TerritoryAssignmentRole;
import com.sanad.platform.crm.ownership.domain.TerritoryAssignmentStatus;
import com.sanad.platform.crm.ownership.domain.TerritoryRuleType;
import com.sanad.platform.crm.ownership.domain.TerritoryStatus;
import com.sanad.platform.crm.ownership.domain.TransferPolicy;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferState;
import com.sanad.platform.crm.ownership.domain.TransferStateConflictException;
import com.sanad.platform.crm.ownership.domain.TransferStepDecision;
import com.sanad.platform.crm.ownership.domain.TransferType;
import com.sanad.platform.crm.ownership.domain.UnauthorizedTransferApproverException;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OwnershipRepositoryBindingPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcSalesTeamRepository teams;
    private static JdbcTeamMembershipRepository teamMemberships;
    private static JdbcQueueMembershipRepository queueMemberships;
    private static JdbcTerritoryRepository territories;
    private static JdbcTerritoryAssignmentRepository territoryAssignments;
    private static JdbcAssignmentRuleRepository rules;
    private static JdbcTransferRequestRepository transfers;

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
                "Docker is required for CRM-008B PostgreSQL binding acceptance.");

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

        teams = new JdbcSalesTeamRepository(jdbc);
        teamMemberships = new JdbcTeamMembershipRepository(jdbc);
        queueMemberships = new JdbcQueueMembershipRepository(jdbc);
        territories = new JdbcTerritoryRepository(jdbc);
        territoryAssignments = new JdbcTerritoryAssignmentRepository(jdbc);
        rules = new JdbcAssignmentRuleRepository(jdbc);
        transfers = new JdbcTransferRequestRepository(jdbc);
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
                .addValue("name", "CRM-008B Binding Test")
                .addValue("subdomain", "crm008b-bind-" + tenantId.toString().substring(0, 8)));
    }

    @Test
    void teamMembership_persistsJsonbAndExplicitTimestamps() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant joinedAt = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        inTransaction(() -> teams.save(new SalesTeam(
                teamId, tenantId, "TEAM-" + teamId.toString().substring(0, 6),
                "Enterprise Team", null, TeamStatus.ACTIVE, actorId,
                null, null, null, null, actorId, actorId)));

        TeamMembership saved = inTransaction(() -> teamMemberships.save(new TeamMembership(
                null, tenantId, teamId, userId, MembershipRole.SALES_REPRESENTATIVE,
                true, MembershipStatus.ACTIVE, joinedAt, null, null, 75,
                "{\"skills\":[\"enterprise\"]}", null, null, actorId, actorId)));

        assertThat(saved.joinedAt()).isEqualTo(joinedAt);
        assertThat(saved.metadata()).contains("enterprise");
        assertThat(jdbc.queryForObject("""
                SELECT metadata->'skills'->>0
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", saved.id()), String.class)).isEqualTo("enterprise");
    }

    @Test
    void queueMembership_persistsProvidedAddedAt() {
        UUID queueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant addedAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        jdbc.update("""
                INSERT INTO crm_queues
                  (id, tenant_id, code, display_name, record_type, status,
                   max_items_per_user, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :code, 'Binding Queue', 'LEAD', 'ACTIVE',
                   10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", queueId)
                .addValue("tenantId", tenantId)
                .addValue("code", "Q-" + queueId.toString().substring(0, 6)));

        QueueMembership saved = inTransaction(() -> queueMemberships.save(new QueueMembership(
                null, tenantId, queueId, userId, MembershipStatus.ACTIVE,
                addedAt, null, null, null, null, actorId, actorId)));

        assertThat(saved.addedAt()).isEqualTo(addedAt);
    }

    @Test
    void territoryAssignment_persistsEffectiveWindow() {
        UUID territoryId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant to = from.plus(30, ChronoUnit.DAYS);

        inTransaction(() -> territories.save(new Territory(
                territoryId, tenantId,
                "T-" + territoryId.toString().substring(0, 6),
                "North Territory", null, null, TerritoryStatus.ACTIVE,
                TerritoryRuleType.GEOGRAPHIC, "{\"country\":\"SA\"}",
                1, null, null, actorId, actorId)));

        TerritoryAssignment saved = inTransaction(() -> territoryAssignments.save(
                new TerritoryAssignment(
                        null, tenantId, territoryId, AssigneeType.USER, assigneeId,
                        TerritoryAssignmentRole.PRIMARY, 10,
                        TerritoryAssignmentStatus.ACTIVE, from, to,
                        null, null, actorId, actorId)));

        assertThat(saved.effectiveFrom()).isEqualTo(from);
        assertThat(saved.effectiveTo()).isEqualTo(to);
    }

    @Test
    void assignmentRuleVersion_persistsJsonbAndEffectiveWindow() {
        UUID ruleId = UUID.randomUUID();
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant to = from.plus(10, ChronoUnit.DAYS);

        inTransaction(() -> rules.save(new AssignmentRule(
                ruleId, tenantId, "RULE-" + ruleId.toString().substring(0, 6),
                1, RuleStatus.ACTIVE, null, null, actorId, actorId)));

        AssignmentRuleVersion saved = inTransaction(() -> rules.saveVersion(
                new AssignmentRuleVersion(
                        null, tenantId, ruleId, 1, "Lead Rule", null,
                        AssignmentRecordType.LEAD, 100,
                        "{\"country\":\"SA\"}", DistributionMethod.DIRECT_OWNER,
                        actorId, null, null, null, from, to,
                        RuleStatus.ACTIVE, actorId, null)));

        assertThat(saved.effectiveFrom()).isEqualTo(from);
        assertThat(saved.effectiveTo()).isEqualTo(to);
        assertThat(jdbc.queryForObject("""
                SELECT match_conditions->>'country'
                  FROM crm_assignment_rule_versions
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", saved.id()), String.class)).isEqualTo("SA");
    }

    @Test
    void transferRequest_persistsRecordIdsAndEnforcesStateMachineAndSeparationOfDuties() {
        UUID transferId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID proposedOwnerId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        Instant temporaryEnd = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        TransferRequest saved = inTransaction(() -> transfers.save(new TransferRequest(
                transferId, tenantId, AssignmentRecordType.LEAD, List.of(recordId),
                requesterId, actorId, proposedOwnerId, null,
                TransferType.TEMPORARY, temporaryEnd, "Coverage transfer",
                TransferPolicy.SINGLE_APPROVER, TransferState.DRAFT,
                1, null, null, null, null, null, null)));

        assertThat(saved.recordIds()).containsExactly(recordId);
        assertThat(saved.temporaryEndDate()).isEqualTo(temporaryEnd);

        assertThatThrownBy(() -> inTransaction(() -> {
            transfers.updateState(tenantId, transferId, TransferState.COMPLETED, actorId, null);
            return null;
        })).isInstanceOf(TransferStateConflictException.class);

        assertThatThrownBy(() -> inTransaction(() ->
                transfers.addStep(tenantId, transferId, 1, requesterId)))
                .isInstanceOf(UnauthorizedTransferApproverException.class);

        inTransaction(() -> {
            transfers.updateState(tenantId, transferId, TransferState.SUBMITTED, null, null);
            transfers.addStep(tenantId, transferId, 1, approverId);
            transfers.updateState(tenantId, transferId, TransferState.UNDER_REVIEW, null, null);
            transfers.decideStep(
                    tenantId, transferId, 1,
                    TransferStepDecision.APPROVED, approverId, "Approved");
            transfers.updateState(tenantId, transferId, TransferState.APPROVED, null, null);
            transfers.updateState(tenantId, transferId, TransferState.COMPLETED, actorId, null);
            return null;
        });

        TransferRequest completed = transfers.findById(tenantId, transferId).orElseThrow();
        assertThat(completed.state()).isEqualTo(TransferState.COMPLETED);
        assertThat(completed.executedByUserId()).isEqualTo(actorId);
        assertThat(completed.executedAt()).isNotNull();
    }

    private static <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
