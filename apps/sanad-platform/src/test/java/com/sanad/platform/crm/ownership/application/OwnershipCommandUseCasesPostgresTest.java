package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.infrastructure.JdbcLeadRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OwnershipCommandUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcLeadRepository leadRepository;
    private static JdbcAssignmentRepository assignmentRepository;
    private static OwnershipCommandUseCases commands;
    private static OwnershipQueryUseCases queries;
    private static JdbcSalesTeamRepository teamRepository;
    private static JdbcQueueRepository queueRepository;

    private UUID tenantId;
    private UUID actorId;
    private UUID userOne;
    private UUID userTwo;
    private SalesTeam team;
    private Queue queue;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for WP-07 PostgreSQL acceptance");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        leadRepository = new JdbcLeadRepository(jdbc);
        JdbcOwnershipHistoryRepository history = new JdbcOwnershipHistoryRepository(jdbc);
        assignmentRepository = new JdbcAssignmentRepository(jdbc, history);
        teamRepository = new JdbcSalesTeamRepository(jdbc);
        queueRepository = new JdbcQueueRepository(jdbc);
        JdbcOwnershipReadAdapter reads = new JdbcOwnershipReadAdapter(jdbc);
        AuditPort audit = (tenant, actor, action, type, id, change, at) -> {};
        TimelineEventPort timeline = (tenant, type, id, event, summary, source, sourceId, actor, at) -> {};
        commands = new OwnershipCommandUseCases(
                assignmentRepository, new JdbcOwnershipRecordAdapter(jdbc),
                new JdbcOwnershipUserValidationAdapter(jdbc), teamRepository, queueRepository,
                audit, timeline, new ObjectMapper());
        queries = new OwnershipQueryUseCases(reads);
    }

    @BeforeEach
    void seed() {
        tenantId = createTenant();
        actorId = createUser(tenantId, "actor");
        userOne = createUser(tenantId, "one");
        userTwo = createUser(tenantId, "two");
        team = tx(() -> teamRepository.save(new SalesTeam(
                null, tenantId, "TEAM-" + shortId(), "Ownership Team", null,
                TeamStatus.ACTIVE, actorId, null, null, null, null, actorId, actorId)));
        queue = tx(() -> queueRepository.save(new Queue(
                null, tenantId, "QUEUE-" + shortId(), "Ownership Queue", QueueRecordType.LEAD,
                null, QueueStatus.ACTIVE, 10, null, null, actorId,
                null, null, actorId, actorId)));
    }

    @Test
    void reassignmentsUpdateFastPathAndAppendTraceableStableHistory() {
        UUID leadId = createLead(userOne);
        UUID firstRequest = UUID.randomUUID();
        Assignment first = tx(() -> commands.reassign(command(
                leadId, OwnerType.USER, userTwo, firstRequest, null)));
        assertFastPath(leadId, userTwo, null, null);
        assertThat(queries.current(tenantId, AssignmentRecordType.LEAD, leadId)).contains(first);

        Assignment second = tx(() -> commands.reassign(command(
                leadId, OwnerType.TEAM, team.id(), UUID.randomUUID(), first.id())));
        assertFastPath(leadId, null, team.id(), null);
        Assignment third = tx(() -> commands.reassign(command(
                leadId, OwnerType.QUEUE, queue.id(), UUID.randomUUID(), second.id())));
        assertFastPath(leadId, null, null, queue.id());
        assertThat(queries.current(tenantId, AssignmentRecordType.LEAD, leadId)).contains(third);

        OwnershipHistoryPage pageOne = queries.history(
                tenantId, AssignmentRecordType.LEAD, leadId, null, 2);
        assertThat(pageOne.entries()).hasSize(2);
        assertThat(pageOne.hasMore()).isTrue();
        OwnershipHistoryPage pageTwo = queries.history(
                tenantId, AssignmentRecordType.LEAD, leadId, pageOne.nextCursor(), 2);
        assertThat(pageTwo.entries()).hasSize(1);
        assertThat(pageTwo.entries()).noneMatch(value ->
                pageOne.entries().stream().anyMatch(previous -> previous.id().equals(value.id())));

        UUID storedRequest = jdbc.queryForObject("""
                SELECT trigger_reference_id FROM crm_ownership_history
                 WHERE tenant_id=:tenantId AND record_type='LEAD' AND record_id=:recordId
                 ORDER BY recorded_at ASC, id ASC LIMIT 1
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("recordId", leadId), UUID.class);
        assertThat(storedRequest).isEqualTo(firstRequest);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM crm_assignments
                 WHERE tenant_id=:tenantId AND record_type='LEAD'
                   AND record_id=:recordId AND status='ACTIVE'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("recordId", leadId), Long.class)).isEqualTo(1L);
    }

    @Test
    void staleExpectedAssignmentAndCrossTenantOwnerFailClosed() {
        UUID leadId = createLead(userOne);
        Assignment first = tx(() -> commands.reassign(command(
                leadId, OwnerType.USER, userTwo, UUID.randomUUID(), null)));
        Assignment second = tx(() -> commands.reassign(command(
                leadId, OwnerType.TEAM, team.id(), UUID.randomUUID(), first.id())));
        assertThatThrownBy(() -> tx(() -> commands.reassign(command(
                leadId, OwnerType.USER, userOne, UUID.randomUUID(), first.id()))))
                .isInstanceOf(ConcurrentClaimConflictException.class);
        assertThat(queries.current(tenantId, AssignmentRecordType.LEAD, leadId)).contains(second);

        UUID otherTenant = createTenant();
        UUID otherUser = createUser(otherTenant, "other");
        assertThatThrownBy(() -> tx(() -> commands.reassign(command(
                leadId, OwnerType.USER, otherUser, UUID.randomUUID(), second.id()))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("same tenant");
    }

    @Test
    void bulkReassignmentIsAtomicAndBounded() {
        UUID firstLead = createLead(userOne);
        UUID secondLead = createLead(userOne);
        List<Assignment> changed = tx(() -> commands.bulkReassign(
                new OwnershipCommandUseCases.BulkReassignCommand(
                        tenantId, AssignmentRecordType.LEAD, List.of(firstLead, secondLead),
                        OwnerType.TEAM, team.id(), actorId, "BULK", UUID.randomUUID())));
        assertThat(changed).hasSize(2);
        assertFastPath(firstLead, null, team.id(), null);
        assertFastPath(secondLead, null, team.id(), null);

        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> tx(() -> commands.bulkReassign(
                new OwnershipCommandUseCases.BulkReassignCommand(
                        tenantId, AssignmentRecordType.LEAD, List.of(firstLead, missing),
                        OwnerType.USER, userTwo, actorId, "ROLLBACK", UUID.randomUUID()))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("not found");
        assertFastPath(firstLead, null, team.id(), null);

        assertThatThrownBy(() -> tx(() -> commands.bulkReassign(
                new OwnershipCommandUseCases.BulkReassignCommand(
                        tenantId, AssignmentRecordType.LEAD, List.of(firstLead, firstLead),
                        OwnerType.USER, userTwo, actorId, "DUP", UUID.randomUUID()))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void ruleDecisionCanDriveAtomicAssignment() {
        UUID leadId = createLead(userOne);
        AssignmentDecision decision = new AssignmentDecision(
                true, null, 1, DistributionMethod.DIRECT_OWNER,
                OwnerType.USER, userTwo, false, List.of("test"));
        Assignment assignment = tx(() -> commands.assignByDecision(
                tenantId, actorId, AssignmentRecordType.LEAD, leadId, decision,
                UUID.randomUUID(), UUID.randomUUID(), "RULE_MATCH"));
        assertThat(assignment.ownerUserId()).isEqualTo(userTwo);
        assertFastPath(leadId, userTwo, null, null);
    }

    private OwnershipCommandUseCases.ReassignCommand command(
            UUID leadId, OwnerType type, UUID ownerId, UUID requestId, UUID expectedId) {
        return new OwnershipCommandUseCases.ReassignCommand(
                tenantId, AssignmentRecordType.LEAD, leadId, type, ownerId,
                actorId, "MANUAL", requestId, UUID.randomUUID(), expectedId, null);
    }

    private UUID createLead(UUID owner) {
        return tx(() -> leadRepository.create(
                tenantId, actorId,
                new LeadRepository.CreateLeadCommand(
                        "Lead " + shortId(), "SNAD", shortId() + "@test.example",
                        "+966500000000", "TEST", owner, BigDecimal.TEN))).id();
    }

    private void assertFastPath(UUID leadId, UUID user, UUID teamId, UUID queueId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT owner_user_id, owner_team_id, owner_queue_id
                  FROM crm_leads WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", leadId));
        assertThat(row.get("owner_user_id")).isEqualTo(user);
        assertThat(row.get("owner_team_id")).isEqualTo(teamId);
        assertThat(row.get("owner_queue_id")).isEqualTo(queueId);
    }

    private UUID createTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, 'Ownership Test', :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("subdomain", "ownership-" + shortId()));
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
