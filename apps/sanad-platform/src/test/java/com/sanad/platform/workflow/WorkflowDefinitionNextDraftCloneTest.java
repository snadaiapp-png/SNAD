package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0.G2 — next-draft deep graph clone.
 *
 * <p>Creating the next draft from a published definition must produce a
 * complete, editable copy of the business graph: every step and every
 * transition is cloned with NEW identifiers, transitions reference the NEW
 * step ids, no runtime state is copied, the source published version stays
 * unchanged and immutable, the operation is atomic, and cross-tenant clones
 * are denied.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowDefinitionNextDraftCloneTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkflowDefinitionService definitionService;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID userId;
    private UUID sourceId;
    private UUID startStepId;
    private UUID taskStepId;
    private UUID endStepId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        startStepId = UUID.randomUUID();
        taskStepId = UUID.randomUUID();
        endStepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "R0Clone-" + tenantId, "r0-clone-" + tenantId, now, now);
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                otherTenantId, "R0CloneB-" + otherTenantId, "r0-clone-b-" + otherTenantId, now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                        + "created_at,updated_at) VALUES (?, ?, ?, 'Clone User', 'ACTIVE', 'x', ?, ?)",
                userId, tenantId, "r0-clone-" + userId + "@test", now, now);
        // Published source definition, version 1.
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'R0-CLONE', 'Clone Source', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, sourceId, tenantId, sourceId, userId, now, now);
        // Graph: START -> HUMAN_TASK -> END with assignment config on the task.
        insertStep(startStepId, "start", "Start", "START", 0);
        insertStep(taskStepId, "task", "Task", "HUMAN_TASK", 1);
        insertStep(endStepId, "end", "End", "END", 2);
        insertTransition("t1", startStepId, taskStepId, "SUCCESS", null);
        insertTransition("t2", taskStepId, endStepId, "SUCCESS",
                "{\"field\":\"context.amount\",\"op\":\"GT\",\"value\":100}");
    }

    @AfterEach
    void cleanup() {
        // Non-transactional test: remove family-scoped rows explicitly,
        // children before parents (FK fk_wf_inst_def).
        jdbc.update("""
                DELETE FROM workflow_step_transitions WHERE workflow_definition_id IN
                  (SELECT id FROM workflow_definitions WHERE definition_family_id = ?)
                """, sourceId);
        jdbc.update("""
                DELETE FROM workflow_steps WHERE workflow_definition_id IN
                  (SELECT id FROM workflow_definitions WHERE definition_family_id = ?)
                """, sourceId);
        jdbc.update("DELETE FROM workflow_instances WHERE workflow_definition_id = ?", sourceId);
        jdbc.update("DELETE FROM workflow_definitions WHERE definition_family_id = ?", sourceId);
        jdbc.update("DELETE FROM users WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
        jdbc.update("DELETE FROM tenants WHERE id IN (?, ?)", tenantId, otherTenantId);
    }

    private void insertStep(UUID stepId, String key, String name, String type, int order) {
        jdbc.update("""
                INSERT INTO workflow_steps
                    (id, tenant_id, workflow_definition_id, step_key, name, step_type,
                     sequence_order, configuration, sla_hours, required_capability,
                     required_role, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST('{}' AS jsonb), NULL, NULL, NULL, 0, NOW(), NOW())
                """, stepId, tenantId, sourceId, key, name, type, order);
    }

    private void insertTransition(String key, UUID from, UUID to, String outcome, String condition) {
        jdbc.update("""
                INSERT INTO workflow_step_transitions
                    (id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                     transition_key, outcome, condition_ast, priority, metadata,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 0, CAST('{}' AS jsonb), NOW(), NOW())
                """, UUID.randomUUID(), tenantId, sourceId, from, to, key, outcome, condition);
    }

    @Test
    void nextDraftDeepClonesGraphWithNewIds() {
        var draft = definitionService.createNextDraft(tenantId, sourceId, userId);

        // NEXT_VERSION_INCREMENT_CORRECT
        assertThat(draft.version()).isEqualTo(2);
        assertThat(draft.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.DRAFT);
        assertThat(draft.definitionFamilyId()).isEqualTo(sourceId);

        var draftSteps = definitionService.findSteps(draft.id());
        var draftTransitions = definitionService.findTransitions(tenantId, draft.id());
        var sourceSteps = definitionService.findSteps(sourceId);
        var sourceTransitions = definitionService.findTransitions(tenantId, sourceId);

        // STEP_COUNT_EQUAL / TRANSITION_COUNT_EQUAL
        assertThat(draftSteps).hasSameSizeAs(sourceSteps);
        assertThat(draftTransitions).hasSameSizeAs(sourceTransitions);

        // NEW_STEP_IDS_DIFFER
        var sourceStepIds = sourceSteps.stream().map(WorkflowStep::id).collect(Collectors.toSet());
        var draftStepIds = draftSteps.stream().map(WorkflowStep::id).collect(Collectors.toSet());
        assertThat(draftStepIds).doesNotContainAnyElementsOf(sourceStepIds);

        // Business fields preserved per step (key/name/type/order/config).
        var sourceByKey = sourceSteps.stream()
                .collect(Collectors.toMap(WorkflowStep::stepKey, s -> s));
        for (var ds : draftSteps) {
            var src = sourceByKey.get(ds.stepKey());
            assertThat(src).as("step key %s must exist in source", ds.stepKey()).isNotNull();
            assertThat(ds.name()).isEqualTo(src.name());
            assertThat(ds.stepType()).isEqualTo(src.stepType());
            assertThat(ds.sequenceOrder()).isEqualTo(src.sequenceOrder());
            assertThat(ds.workflowDefinitionId()).isEqualTo(draft.id());
        }

        // NO_TRANSITION_REFERENCES_SOURCE_STEP_IDS + semantics preserved
        var sourceTransitionIds = sourceTransitions.stream()
                .map(WorkflowTransition::id).collect(Collectors.toSet());
        for (var dt : draftTransitions) {
            assertThat(dt.id()).isNotIn(sourceTransitionIds);
            assertThat(dt.workflowDefinitionId()).isEqualTo(draft.id());
            assertThat(dt.fromStepId()).isIn(draftStepIds);
            assertThat(dt.toStepId()).isIn(draftStepIds);
        }
        var keysByOutcome = draftTransitions.stream()
                .collect(Collectors.toMap(WorkflowTransition::transitionKey, WorkflowTransition::toStepId));
        assertThat(keysByOutcome.get("t1")).isEqualTo(stepIdForKey(draftSteps, "task"));
        assertThat(keysByOutcome.get("t2")).isEqualTo(stepIdForKey(draftSteps, "end"));

        // SOURCE_PUBLISHED_VERSION_UNCHANGED
        var reloadedSource = definitionService.findById(tenantId, sourceId).orElseThrow();
        assertThat(reloadedSource.version()).isEqualTo(1);
        assertThat(reloadedSource.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.PUBLISHED);
        assertThat(definitionService.findSteps(sourceId)).hasSize(3);
        assertThat(definitionService.findTransitions(tenantId, sourceId)).hasSize(2);

        // PUBLISHED_SOURCE_REMAINS_IMMUTABLE (graph mutations still rejected)
        assertThatThrownBy(() -> definitionService.addStep(WorkflowStep.create(
                        tenantId, sourceId, "extra", "Extra", WorkflowStep.StepType.ACTION,
                        9, "{}", null, null, null), userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runningInstanceRemainsPinnedToSourceVersion() {
        var now = Timestamp.from(Instant.now());
        var instanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, engine_generation,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                          CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, sourceId, userId, now, now);

        definitionService.createNextDraft(tenantId, sourceId, userId);

        var pinned = jdbc.queryForMap("""
                SELECT workflow_version, workflow_definition_id FROM workflow_instances
                WHERE id = ?
                """, instanceId);
        assertThat(((Number) pinned.get("workflow_version")).intValue()).isEqualTo(1);
        assertThat(UUID.fromString(pinned.get("workflow_definition_id").toString())).isEqualTo(sourceId);
    }

    @Test
    void crossTenantCloneDenied() {
        assertThatThrownBy(() ->
                definitionService.createNextDraft(otherTenantId, sourceId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        // No draft family growth happened in the source tenant either.
        Integer familyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE definition_family_id = ?",
                Integer.class, sourceId);
        assertThat(familyCount).isEqualTo(1);
    }

    @Test
    void failedCopyRollsBackEverything() {
        // Poison the SOURCE graph with a transition whose endpoints do not
        // differ (direct SQL bypasses the domain guard): the clone must fail
        // mid-way — after draft steps are already inserted — and the service
        // transaction must roll back EVERYTHING (definition + steps).
        jdbc.update("""
                INSERT INTO workflow_step_transitions
                    (id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                     transition_key, outcome, condition_ast, priority, metadata,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'POISON', 'SUCCESS', NULL, 0, CAST('{}' AS jsonb), NOW(), NOW())
                """, UUID.randomUUID(), tenantId, sourceId, taskStepId, taskStepId);

        // The domain guard on transition endpoints aborts the clone. Spring
        // wraps the guard exception in its data-access translation, so assert
        // the root cause semantics rather than the wrapper type.
        assertThatThrownBy(() ->
                definitionService.createNextDraft(tenantId, sourceId, userId))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("transition endpoints must differ");

        Integer familyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE definition_family_id = ?",
                Integer.class, sourceId);
        assertThat(familyCount)
                .as("no draft definition row may survive a failed clone")
                .isEqualTo(1);
        Integer orphanDraftSteps = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_steps WHERE workflow_definition_id <> ?
                  AND workflow_definition_id IN
                    (SELECT id FROM workflow_definitions WHERE definition_family_id = ?)
                """, Integer.class, sourceId, sourceId);
        assertThat(orphanDraftSteps).isZero();
    }

    private UUID stepIdForKey(List<WorkflowStep> steps, String key) {
        return steps.stream().filter(s -> s.stepKey().equals(key)).findFirst().orElseThrow().id();
    }
}
