package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowDefinitionValidator;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P03 release-gate regression — published definition versions are
 * immutable at the SERVICE boundary, not only in the domain model.
 *
 * <p>Root cause fixed here: {@code WorkflowDefinitionService.addStep}
 * previously persisted steps regardless of {@code publicationState}, so a
 * PUBLISHED version silently accepted new graph nodes (HTTP 200) instead
 * of failing closed with a conflict. The release contract requires a
 * conflict on every graph mutation of a non-DRAFT version; graph changes
 * must go through {@code next-draft}.</p>
 */
class WorkflowDefinitionImmutabilityTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void addStepOnPublishedDefinitionFailsClosed() {
        var published = WorkflowDefinition
                .create(TENANT, "WF-P03-IMM", "P03 Immutability", "fixture",
                        "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR)
                .publish(ACTOR, "sha256:fixture");
        var repo = new StubDefinitionRepository(published);
        var service = new WorkflowDefinitionService(repo, new StubAuditRepository(),
                new WorkflowDefinitionValidator(null, null));

        var step = WorkflowStep.create(TENANT, published.id(), "extra", "Extra",
                WorkflowStep.StepType.ACTION, 99, "{}", null, null, null);

        assertThatThrownBy(() -> service.addStep(step, ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
        // The repository must not have persisted anything.
        assertThat(repo.savedSteps).isEmpty();
    }

    @Test
    void addStepOnDraftDefinitionStillWorks() {
        var draft = WorkflowDefinition.create(TENANT, "WF-P03-DRAFT", "P03 Draft", "fixture",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR);
        var repo = new StubDefinitionRepository(draft);
        var service = new WorkflowDefinitionService(repo, new StubAuditRepository(),
                new WorkflowDefinitionValidator(null, null));

        var step = WorkflowStep.create(TENANT, draft.id(), "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", null, null, null);
        var saved = service.addStep(step, ACTOR);

        assertThat(saved.stepKey()).isEqualTo("start");
        assertThat(repo.savedSteps).hasSize(1);
    }

    // ===== Minimal stubs (pure unit test — no Spring context, no DB) =====

    private static final class StubDefinitionRepository implements WorkflowDefinitionRepository {
        private final WorkflowDefinition definition;
        final List<WorkflowStep> savedSteps = new java.util.ArrayList<>();

        private StubDefinitionRepository(WorkflowDefinition definition) {
            this.definition = definition;
        }

        @Override
        public WorkflowDefinition save(WorkflowDefinition def) {
            return def;
        }

        @Override
        public Optional<WorkflowDefinition> findById(UUID tenantId, UUID id) {
            return definition != null && definition.tenantId().equals(tenantId) && definition.id().equals(id)
                    ? Optional.of(definition) : Optional.empty();
        }

        @Override
        public Optional<WorkflowDefinition> findByCode(UUID tenantId, String code, int version) {
            return Optional.empty();
        }

        @Override
        public Optional<WorkflowDefinition> findActiveByCode(UUID tenantId, String code) {
            return Optional.empty();
        }

        @Override
        public List<WorkflowDefinition> findByTenant(UUID tenantId, int limit) {
            return List.of();
        }

        @Override
        public List<WorkflowDefinition> findByTenantAndStatus(UUID tenantId,
                WorkflowDefinition.Status status, int limit) {
            return List.of();
        }

        @Override
        public List<WorkflowDefinition> findVersions(UUID tenantId, UUID definitionFamilyId) {
            return List.of();
        }

        @Override
        public Optional<WorkflowDefinition> findPublishedByFamily(UUID tenantId, UUID definitionFamilyId) {
            return Optional.empty();
        }

        @Override
        public List<WorkflowStep> findSteps(UUID workflowDefinitionId) {
            return List.of();
        }

        @Override
        public WorkflowStep saveStep(WorkflowStep step) {
            savedSteps.add(step);
            return step;
        }

        @Override
        public List<WorkflowTransition> findTransitions(UUID workflowDefinitionId) {
            return List.of();
        }

        @Override
        public WorkflowTransition saveTransition(WorkflowTransition transition) {
            return transition;
        }
    }

    private static final class StubAuditRepository implements WorkflowTransitionAuditRepository {
        @Override
        public WorkflowTransitionAudit save(WorkflowTransitionAudit audit) {
            return audit;
        }

        @Override
        public List<WorkflowTransitionAudit> findByInstance(UUID tenantId, UUID workflowInstanceId) {
            return List.of();
        }

        @Override
        public List<WorkflowTransitionAudit> findByTenant(UUID tenantId, int limit) {
            return List.of();
        }
    }
}
