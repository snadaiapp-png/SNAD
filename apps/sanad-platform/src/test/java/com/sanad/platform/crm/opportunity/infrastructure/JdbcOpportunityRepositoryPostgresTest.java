package com.sanad.platform.crm.opportunity.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.CreateOpportunityCommand;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.UpdateOpportunityCommand;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.CreatePipelineCommand;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcOpportunityRepository} (TD-003-S2).
 *
 * <p>Covers opportunity create round-trip, optimistic-concurrency conflict on update, stage
 * movement with version increment, and the not-found error path. Pipelines + stages are seeded
 * via {@link JdbcPipelineRepository} (which auto-derives terminal stages) so opportunities have
 * valid FK targets.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcOpportunityRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcOpportunityRepository opportunities;
    private JdbcPipelineRepository pipelines;
    private UUID tenantId;
    private UUID actorId;
    private UUID pipelineId;
    private UUID openStageId;
    private UUID wonStageId;

    @BeforeEach
    void setUp() {
        opportunities = new JdbcOpportunityRepository(jdbc());
        pipelines = new JdbcPipelineRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();

        // create a pipeline whose stage derivation gives us an open stage + a Won terminal stage
        PipelineRecord pipeline = inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Sales " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Qualified", "Won"))));
        pipelineId = pipeline.id();
        List<StageRecord> stages = pipelines.findStages(tenantId, pipelineId);
        openStageId = stages.get(0).id();
        wonStageId = stages.get(stages.size() - 1).id();
    }

    @Test
    void create_persistsOpportunityWithDefaults() {
        OpportunityRecord saved = inTransaction(() -> opportunities.create(tenantId, actorId,
                new CreateOpportunityCommand(null, null, pipelineId, openStageId,
                        "Big Deal", new BigDecimal("100000"), "SAR",
                        LocalDate.now().plusDays(30), actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.status()).isEqualTo("OPEN"); // create hardcodes OPEN
        assertThat(saved.probability()).isEqualByComparingTo(BigDecimal.ZERO); // create hardcodes 0
        assertThat(saved.amount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(saved.currencyCode()).isEqualTo("SAR");
        assertThat(opportunities.findById(tenantId, saved.id()).name()).isEqualTo("Big Deal");
    }

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        OpportunityRecord created = inTransaction(() -> opportunities.create(tenantId, actorId,
                new CreateOpportunityCommand(null, null, pipelineId, openStageId,
                        "Deal", new BigDecimal("1000"), "SAR", null, actorId)));

        OpportunityRecord updated = inTransaction(() -> opportunities.update(tenantId, actorId,
                created.id(), new UpdateOpportunityCommand("Bigger Deal",
                        new BigDecimal("5000"), actorId, null), 0));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.name()).isEqualTo("Bigger Deal");
        assertThat(updated.amount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        OpportunityRecord created = inTransaction(() -> opportunities.create(tenantId, actorId,
                new CreateOpportunityCommand(null, null, pipelineId, openStageId,
                        "Deal", new BigDecimal("1000"), "SAR", null, actorId)));
        inTransaction(() -> opportunities.update(tenantId, actorId, created.id(),
                new UpdateOpportunityCommand("v1", null, actorId, null), 0)); // -> v1

        assertThatThrownBy(() -> inTransaction(() ->
                opportunities.update(tenantId, actorId, created.id(),
                        new UpdateOpportunityCommand("stale", null, actorId, null), 0)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void moveStage_recordsNewStageStatusAndReason() {
        OpportunityRecord created = inTransaction(() -> opportunities.create(tenantId, actorId,
                new CreateOpportunityCommand(null, null, pipelineId, openStageId,
                        "Deal", new BigDecimal("1000"), "SAR", null, actorId)));

        OpportunityRecord moved = inTransaction(() -> opportunities.moveStage(tenantId, actorId,
                created.id(), wonStageId, "WON", "Customer signed", 0));

        assertThat(moved.version()).isEqualTo(1);
        assertThat(moved.stageId()).isEqualTo(wonStageId);
        assertThat(moved.status()).isEqualTo("WON");
        assertThat(moved.winLossReason()).isEqualTo("Customer signed");
    }

    @Test
    void findById_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> opportunities.findById(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_OPPORTUNITY_NOT_FOUND));
    }
}
