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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TransferUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcLeadRepository leadRepository;
    private static JdbcAssignmentRepository assignmentRepository;
    private static JdbcTransferRequestRepository transferRepository;
    private static OwnershipCommandUseCases ownershipCommands;
    private static TransferUseCases transfers;
    private static JdbcSalesTeamRepository teamRepository;

    private UUID tenantId;
    private UUID currentOwner;
    private UUID targetOwner;
    private UUID alternateOwner;
    private UUID approver;
    private SalesTeam targetTeam;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for WP-08 PostgreSQL acceptance");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        ObjectMapper mapper = new ObjectMapper();
        leadRepository = new JdbcLeadRepository(jdbc);
        JdbcOwnershipHistoryRepository history = new JdbcOwnershipHistoryRepository(jdbc);
        assignmentRepository = new JdbcAssignmentRepository(jdbc, history);
        transferRepository = new JdbcTransferRequestRepository(jdbc);
        teamRepository = new JdbcSalesTeamRepository(jdbc);
        JdbcQueueRepository queues = new JdbcQueueRepository(jdbc);
        JdbcOwnershipRecordAdapter records = new JdbcOwnershipRecordAdapter(jdbc);
        JdbcOwnershipUserValidationAdapter users = new JdbcOwnershipUserValidationAdapter(jdbc);
        AuditPort audit = (tenant, actor, action, type, id, change, at) -> {};
        TimelineEventPort timeline = (tenant, type, id, event, summary, source, sourceId, actor, at) -> {};

        ownershipCommands = new OwnershipCommandUseCases(
                assignmentRepository, records, users, teamRepository, queues,
                audit, timeline, mapper);
        transfers = new TransferUseCases(
                transferRepository, assignmentRepository, records, ownershipCommands,
                users, teamRepository, new InlineTransferWorkflowStubAdapter(),
                new DisabledHrmOwnershipAdapter(), audit, timeline, mapper);
    }

    @BeforeEach
    void seed() {
        tenantId = createTenant();
        currentOwner = createUser(tenantId, "current");
        targetOwner = createUser(tenantId, "target");
        alternateOwner = createUser(tenantId, "alternate");
        approver = createUser(tenantId, "approver");
        targetTeam = tx(() -> teamRepository.save(new SalesTeam(
                null, tenantId, "TEAM-" + shortId(), "Transfer Team", null,
                TeamStatus.ACTIVE, approver, null, null,
                null, null, approver, approver)));
    }

    @Test
    void singleApprovalCompletesAtomicBulkTransferAndPersistsWorkflow() {
        UUID firstLead = createOwnedLead(currentOwner);
        UUID secondLead = createOwnedLead(currentOwner);
        TransferRequest draft = tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(firstLead, secondLead), OwnerType.USER, targetOwner,
                        TransferType.PERMANENT, null, TransferPolicy.SINGLE_APPROVER)));

        TransferRequest submitted = tx(() -> transfers.submit(
                tenantId, currentOwner, draft.id(), List.of(approver)));
        assertThat(submitted.state()).isEqualTo(TransferState.UNDER_REVIEW);
        assertThat(submitted.workflowRunId()).isNotNull();
        assertThat(submitted.currentApprovalStep()).isEqualTo(1);
        assertThat(transferRepository.findSteps(tenantId, draft.id()))
                .singleElement().extracting(TransferStep::approverUserId).isEqualTo(approver);

        assertThatThrownBy(() -> tx(() -> transfers.decide(
                tenantId, currentOwner, draft.id(), TransferStepDecision.APPROVED, "self")))
                .isInstanceOf(UnauthorizedTransferApproverException.class);

        TransferRequest completed = tx(() -> transfers.decide(
                tenantId, approver, draft.id(), TransferStepDecision.APPROVED, "approved"));
        assertThat(completed.state()).isEqualTo(TransferState.COMPLETED);
        assertThat(completed.executedByUserId()).isEqualTo(approver);
        assertOwner(firstLead, OwnerType.USER, targetOwner);
        assertOwner(secondLead, OwnerType.USER, targetOwner);

        for (UUID leadId : List.of(firstLead, secondLead)) {
            Assignment active = assignmentRepository.findActive(
                    tenantId, AssignmentRecordType.LEAD, leadId).orElseThrow();
            assertThat(active.ownerUserId()).isEqualTo(targetOwner);
            UUID triggerReference = jdbc.queryForObject("""
                    SELECT trigger_reference_id FROM crm_ownership_history
                     WHERE tenant_id=:tenantId AND record_type='LEAD' AND record_id=:recordId
                       AND change_type='TRANSFER'
                     ORDER BY recorded_at DESC, id DESC LIMIT 1
                    """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("recordId", leadId), UUID.class);
            assertThat(triggerReference).isEqualTo(draft.id());
        }
    }

    @Test
    void rejectionAndRequesterCancellationDoNotChangeOwnership() {
        UUID rejectedLead = createOwnedLead(currentOwner);
        TransferRequest rejectedDraft = tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(rejectedLead), OwnerType.USER, targetOwner,
                        TransferType.PERMANENT, null, TransferPolicy.SINGLE_APPROVER)));
        tx(() -> transfers.submit(tenantId, currentOwner, rejectedDraft.id(), List.of(approver)));
        TransferRequest rejected = tx(() -> transfers.decide(
                tenantId, approver, rejectedDraft.id(), TransferStepDecision.REJECTED, "no"));
        assertThat(rejected.state()).isEqualTo(TransferState.REJECTED);
        assertOwner(rejectedLead, OwnerType.USER, currentOwner);

        UUID cancelledLead = createOwnedLead(currentOwner);
        TransferRequest cancelledDraft = tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(cancelledLead), OwnerType.USER, targetOwner,
                        TransferType.PERMANENT, null, TransferPolicy.SINGLE_APPROVER)));
        assertThatThrownBy(() -> tx(() -> transfers.cancel(
                tenantId, approver, cancelledDraft.id(), "not requester")))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("requester");
        TransferRequest cancelled = tx(() -> transfers.cancel(
                tenantId, currentOwner, cancelledDraft.id(), "withdrawn"));
        assertThat(cancelled.state()).isEqualTo(TransferState.CANCELLED);
        assertOwner(cancelledLead, OwnerType.USER, currentOwner);
    }

    @Test
    void noApprovalTemporaryTeamTransferCompletesWithEndDate() {
        UUID leadId = createOwnedLead(currentOwner);
        Instant end = Instant.now().plus(3, ChronoUnit.DAYS);
        TransferRequest draft = tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(leadId), OwnerType.TEAM, targetTeam.id(),
                        TransferType.TEMPORARY, end, TransferPolicy.NO_APPROVAL_REQUIRED)));
        TransferRequest completed = tx(() -> transfers.submit(
                tenantId, currentOwner, draft.id(), List.of()));
        assertThat(completed.state()).isEqualTo(TransferState.COMPLETED);
        Assignment active = assignmentRepository.findActive(
                tenantId, AssignmentRecordType.LEAD, leadId).orElseThrow();
        assertThat(active.ownerType()).isEqualTo(OwnerType.TEAM);
        assertThat(active.ownerTeamId()).isEqualTo(targetTeam.id());
        assertThat(active.effectiveTo()).isEqualTo(end);
        assertOwner(leadId, OwnerType.TEAM, targetTeam.id());
    }

    @Test
    void staleSourceOwnershipRollsBackApprovalAndAllWrites() {
        UUID firstLead = createOwnedLead(currentOwner);
        UUID secondLead = createOwnedLead(currentOwner);
        TransferRequest draft = tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(firstLead, secondLead), OwnerType.USER, targetOwner,
                        TransferType.PERMANENT, null, TransferPolicy.SINGLE_APPROVER)));
        tx(() -> transfers.submit(tenantId, currentOwner, draft.id(), List.of(approver)));

        tx(() -> ownershipCommands.reassign(new OwnershipCommandUseCases.ReassignCommand(
                tenantId, AssignmentRecordType.LEAD, secondLead,
                OwnerType.USER, alternateOwner, approver, "intervening change",
                UUID.randomUUID(), UUID.randomUUID(),
                assignmentRepository.findActive(tenantId, AssignmentRecordType.LEAD, secondLead)
                        .orElseThrow().id(), null)));

        assertThatThrownBy(() -> tx(() -> transfers.decide(
                tenantId, approver, draft.id(), TransferStepDecision.APPROVED, "stale")))
                .isInstanceOf(ConcurrentClaimConflictException.class);
        assertThat(transferRepository.findById(tenantId, draft.id()).orElseThrow().state())
                .isEqualTo(TransferState.UNDER_REVIEW);
        assertThat(transferRepository.findStep(tenantId, draft.id(), 1).orElseThrow().decision())
                .isNull();
        assertOwner(firstLead, OwnerType.USER, currentOwner);
        assertOwner(secondLead, OwnerType.USER, alternateOwner);
    }

    @Test
    void multiStepAndHrmAbsenceReassignmentFailClosed() {
        UUID leadId = createOwnedLead(currentOwner);
        assertThatThrownBy(() -> tx(() -> transfers.createDraft(
                tenantId, currentOwner,
                command(List.of(leadId), OwnerType.USER, targetOwner,
                        TransferType.PERMANENT, null, TransferPolicy.MULTI_APPROVER))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("WorkflowPort is a stub");
        assertThatThrownBy(() -> transfers.requestAbsenceDrivenReassignment(
                tenantId, currentOwner))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("disabled");
    }

    private TransferUseCases.CreateTransferCommand command(
            List<UUID> recordIds,
            OwnerType ownerType,
            UUID ownerId,
            TransferType type,
            Instant end,
            TransferPolicy policy) {
        return new TransferUseCases.CreateTransferCommand(
                AssignmentRecordType.LEAD, recordIds, ownerType, ownerId,
                type, end, "Transfer test", policy);
    }

    private UUID createOwnedLead(UUID owner) {
        UUID leadId = tx(() -> leadRepository.create(
                tenantId, owner,
                new LeadRepository.CreateLeadCommand(
                        "Lead " + shortId(), "SNAD", shortId() + "@test.example",
                        "+966500000000", "TEST", owner, BigDecimal.TEN))).id();
        tx(() -> ownershipCommands.reassign(new OwnershipCommandUseCases.ReassignCommand(
                tenantId, AssignmentRecordType.LEAD, leadId,
                OwnerType.USER, owner, owner, "initial owner",
                UUID.randomUUID(), UUID.randomUUID(), null, null)));
        return leadId;
    }

    private void assertOwner(UUID leadId, OwnerType type, UUID ownerId) {
        Assignment assignment = assignmentRepository.findActive(
                tenantId, AssignmentRecordType.LEAD, leadId).orElseThrow();
        assertThat(assignment.ownerType()).isEqualTo(type);
        switch (type) {
            case USER -> assertThat(assignment.ownerUserId()).isEqualTo(ownerId);
            case TEAM -> assertThat(assignment.ownerTeamId()).isEqualTo(ownerId);
            case QUEUE -> assertThat(assignment.ownerQueueId()).isEqualTo(ownerId);
        }
        var row = jdbc.queryForMap("""
                SELECT owner_user_id, owner_team_id, owner_queue_id
                  FROM crm_leads WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("id", leadId));
        assertThat(row.get("owner_user_id")).isEqualTo(type == OwnerType.USER ? ownerId : null);
        assertThat(row.get("owner_team_id")).isEqualTo(type == OwnerType.TEAM ? ownerId : null);
        assertThat(row.get("owner_queue_id")).isEqualTo(type == OwnerType.QUEUE ? ownerId : null);
    }

    private UUID createTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, 'Transfer Test', :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("subdomain", "transfer-" + shortId()));
        return id;
    }

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users
                  (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :tenantId, :email, :name, 'ACTIVE', 'dummy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("tenantId", tenant)
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

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
