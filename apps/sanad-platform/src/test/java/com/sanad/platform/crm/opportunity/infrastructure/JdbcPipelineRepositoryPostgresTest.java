package com.sanad.platform.crm.opportunity.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.CreatePipelineCommand;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcPipelineRepository} (TD-003-S2 + REM-1).
 *
 * <p>Covers pipeline create, findById/findAll round-trip, and stage auto-derivation (terminal
 * "Won" stage + probability scaling).
 *
 * <p>The {@code update(...)} path (optimistic-lock version bump + concurrency conflict) was
 * deferred by TD-003-S2 because it references an {@code updated_by} column absent from
 * {@code crm_pipelines} (Defect A2). Epic REM-1 reconciled that schema drift (migration
 * {@code V20260804_1}), so {@code update()} is now covered below.
 *
 * <p>Branch: fix/rem-1-crm-schema-drift (Epic REM-1)
 */
class JdbcPipelineRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcPipelineRepository pipelines;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        pipelines = new JdbcPipelineRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    @Test
    void create_persistsPipelineAndDerivesStages() {
        PipelineRecord saved = inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Sales " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Qualified", "Proposal", "Won"))));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.name()).startsWith("Sales ");
        assertThat(saved.currencyCode()).isEqualTo("SAR");
        assertThat(saved.active()).isTrue();

        List<StageRecord> stages = pipelines.findStages(tenantId, saved.id());
        assertThat(stages).hasSize(4);
        // terminal "Won" stage derives probability 100 and terminal_state WON
        StageRecord won = stages.get(stages.size() - 1);
        assertThat(won.name()).isEqualTo("Won");
        assertThat(won.terminalState()).isEqualTo("WON");
        assertThat(won.probability()).isEqualByComparingTo(java.math.BigDecimal.valueOf(100));
        // non-terminal stages have no terminal_state
        assertThat(stages.get(0).terminalState()).isNull();
        assertThat(stages.get(0).sequence()).isEqualTo(1);
    }

    @Test
    void findById_roundTripsCreatedPipeline() {
        PipelineRecord saved = inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Leads " + tenantId.toString().substring(0, 6),
                        "USD", List.of("New", "Won", "Lost"))));

        PipelineRecord fetched = pipelines.findById(tenantId, saved.id());
        assertThat(fetched).isEqualTo(saved);
    }

    @Test
    void findAll_listsAllPipelinesForTenant() {
        inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Alpha " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Won"))));
        inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Beta " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Won"))));

        List<PipelineRecord> all = pipelines.findAll(tenantId);
        assertThat(all).hasSize(2);
    }

    @Test
    void findStages_whenPipelineMissingThrowsNotFound() {
        assertThatThrownBy(() -> pipelines.findStages(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_PIPELINE_NOT_FOUND));
    }

    @Test
    void findById_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> pipelines.findById(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_PIPELINE_NOT_FOUND));
    }

    // --- update() path (added by Epic REM-1; was deferred under Defect A2) ---

    @Test
    void update_bumpsVersionAndMutatesProvidedFields() {
        PipelineRecord saved = inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Original " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Won"))));

        PipelineRecord updated = inTransaction(() -> pipelines.update(tenantId, actorId, saved.id(),
                "Renamed " + tenantId.toString().substring(0, 6), "USD", saved.version()));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);   // optimistic-lock bump
        assertThat(updated.name()).startsWith("Renamed ");
        assertThat(updated.currencyCode()).isEqualTo("USD");
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        PipelineRecord saved = inTransaction(() -> pipelines.create(tenantId, actorId,
                new CreatePipelineCommand("Conflict " + tenantId.toString().substring(0, 6),
                        "SAR", List.of("New", "Won"))));

        // The UPDATE ... WHERE version=:expectedVersion matches 0 rows when the presented
        // version is stale; the repository maps that to CRM_CONCURRENCY_CONFLICT.
        long staleVersion = saved.version() + 7;
        assertThatThrownBy(() -> inTransaction(() -> pipelines.update(tenantId, actorId, saved.id(),
                "Stale", "USD", staleVersion)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        // The row itself is untouched by the failed update.
        PipelineRecord unchanged = pipelines.findById(tenantId, saved.id());
        assertThat(unchanged.version()).isEqualTo(saved.version());
        assertThat(unchanged.name()).startsWith("Conflict ");
    }
}
