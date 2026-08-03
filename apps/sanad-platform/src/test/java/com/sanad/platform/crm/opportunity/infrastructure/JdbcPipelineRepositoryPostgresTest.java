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
 * Testcontainers PostgreSQL integration tests for {@link JdbcPipelineRepository} (TD-003-S2).
 *
 * <p>Covers pipeline create, findById/findAll round-trip, and stage auto-derivation (terminal
 * "Won" stage + probability scaling). The {@code update(...)} method is deliberately NOT tested
 * here because it references an {@code updated_by} column that is absent from the
 * {@code crm_pipelines} schema (see TD-003-S2-IMPLEMENTATION-REPORT.md, Defect A2) — exercising
 * it would throw {@code BadSqlGrammarException}. That schema gap is a production defect to be
 * resolved separately, not worked around inside this story.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
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
}
