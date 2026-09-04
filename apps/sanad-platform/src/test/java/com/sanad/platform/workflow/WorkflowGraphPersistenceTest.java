package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 / Task 5 — explicit graph transition persistence.
 *
 * <p>Verifies that Y2 transitions are scoped to exactly one concrete
 * definition version, that transition keys are unique per definition
 * (upsert semantics), and that self-loops are rejected at the domain
 * boundary.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowGraphPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkflowDefinitionRepository definitionRepo;

    private UUID tenantId;
    private UUID userId;
    private UUID definitionId;
    private UUID stepFrom;
    private UUID stepTo;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        stepFrom = UUID.randomUUID();
        stepTo = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow Graph Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-graph-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Workflow Graph User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-graph-" + userId.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-GRAPH', 'Workflow Graph', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        insertStep(stepFrom, "from_step", "START");
        insertStep(stepTo, "to_step", "HUMAN_TASK");
    }

    @Test
    void transitionBelongsToOneConcreteDefinitionVersion() {
        var transition = WorkflowTransition.create(tenantId, definitionId, stepFrom, stepTo,
                "approve", "APPROVE", null, 10, "{}");
        definitionRepo.saveTransition(transition);
        assertThat(definitionRepo.findTransitions(definitionId))
                .extracting(WorkflowTransition::id)
                .contains(transition.id());

        // A sibling definition version must not see the transition.
        var otherDefinitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-GRAPH-2', 'Workflow Graph 2', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, otherDefinitionId, tenantId, otherDefinitionId, userId,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        assertThat(definitionRepo.findTransitions(otherDefinitionId)).isEmpty();
    }

    @Test
    void transitionKeyIsUpsertedPerDefinition() {
        var first = WorkflowTransition.create(tenantId, definitionId, stepFrom, stepTo,
                "approve", "APPROVE", null, 10, "{}");
        definitionRepo.saveTransition(first);

        var rekeyed = new WorkflowTransition(UUID.randomUUID(), tenantId, definitionId,
                stepFrom, stepTo, "approve", "APPROVE", null, 20, "{}",
                Instant.now(), Instant.now());
        definitionRepo.saveTransition(rekeyed);

        var transitions = definitionRepo.findTransitions(definitionId);
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).priority()).isEqualTo(20);
    }

    @Test
    void selfLoopTransitionIsRejected() {
        assertThatThrownBy(() -> WorkflowTransition.create(tenantId, definitionId,
                stepFrom, stepFrom, "self", "SUCCESS", null, 0, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertStep(UUID stepId, String stepKey, String stepType) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, stepType, now, now);
    }
}
