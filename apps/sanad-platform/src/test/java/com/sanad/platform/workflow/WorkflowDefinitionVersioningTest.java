package com.sanad.platform.workflow;

import com.sanad.platform.workflow.domain.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 / Task 4 — immutable definition-family and publication metadata.
 *
 * <p>These are pure domain invariants (design decision I3): published
 * versions are immutable, next drafts stay in the same definition family
 * with an incremented version, and publication stamps the Y2 generation.</p>
 */
class WorkflowDefinitionVersioningTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private WorkflowDefinition fixturePublishedDefinition() {
        return WorkflowDefinition.create(TENANT, "WF-Y2-VER", "Y2 Versioning", "fixture",
                        "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR)
                .publish(ACTOR, "sha256:fixture-checksum");
    }

    @Test
    void publishedDefinitionCannotBeMutatedInPlace() {
        var published = fixturePublishedDefinition();
        assertThatThrownBy(() -> published.rename("mutated"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void draftCanBeRenamedBeforePublication() {
        var draft = WorkflowDefinition.create(TENANT, "WF-Y2-VER", "Y2 Versioning", "fixture",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR);
        assertThat(draft.rename("renamed").name()).isEqualTo("renamed");
    }

    @Test
    void nextDraftKeepsFamilyAndIncrementsVersion() {
        var published = fixturePublishedDefinition();
        var draft = published.nextDraft(ACTOR);
        assertThat(draft.definitionFamilyId()).isEqualTo(published.definitionFamilyId());
        assertThat(draft.version()).isEqualTo(published.version() + 1);
        assertThat(draft.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.DRAFT);
        assertThat(draft.id()).isNotEqualTo(published.id());
    }

    @Test
    void publishRejectsAlreadyPublishedVersion() {
        var published = fixturePublishedDefinition();
        assertThatThrownBy(() -> published.publish(ACTOR, "sha256:again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishStampsY2GenerationAndChecksum() {
        var draft = WorkflowDefinition.create(TENANT, "WF-Y2-VER", "Y2 Versioning", "fixture",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR);
        var published = draft.publish(ACTOR, "sha256:abc");
        assertThat(published.engineGeneration()).isEqualTo(WorkflowDefinition.EngineGeneration.Y2);
        assertThat(published.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.PUBLISHED);
        assertThat(published.publishedBy()).isEqualTo(ACTOR);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.validatedAt()).isNotNull();
        assertThat(published.definitionChecksum()).isEqualTo("sha256:abc");
        assertThat(published.schemaVersion()).isEqualTo(1);
    }

    @Test
    void createdDefinitionStartsAsLegacyGenerationWithOwnFamily() {
        var def = WorkflowDefinition.create(TENANT, "WF-Y2-VER", "Y2 Versioning", "fixture",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, ACTOR);
        assertThat(def.engineGeneration()).isEqualTo(WorkflowDefinition.EngineGeneration.LEGACY);
        assertThat(def.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.DRAFT);
        assertThat(def.definitionFamilyId()).isEqualTo(def.id());
    }

    @Test
    void publishedSourceSurvivesNextDraftUnchanged() {
        var published = fixturePublishedDefinition();
        var snapshot = published;
        published.nextDraft(ACTOR);
        assertThat(published).isEqualTo(snapshot);
        assertThat(published.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.PUBLISHED);
        assertThat(published.version()).isEqualTo(1);
    }
}
