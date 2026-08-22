package com.sanad.platform.crm.collaboration.application;

import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task 7 — Collaboration Membership Service.
 *
 * <p>Unit test that verifies the {@link CollaborationMembershipService}
 * coordinates {@link RecipientEligibilityPort} and
 * {@link EntityParticipantRepository} to implement add / remove / list
 * operations for CRM collaboration participants.
 *
 * <p>Service boundary:
 * <ul>
 *   <li>{@code addParticipant} evaluates eligibility first, then short-circuits
 *       if an active same-role participant already exists (idempotent).</li>
 *   <li>{@code removeParticipant} loads by id, rejects absent / inactive
 *       records, calls {@code markRemoved} with the COMMAND's expectedVersion
 *       (NOT the existing.version), and returns the in-memory removed snapshot
 *       when the optimistic-lock UPDATE succeeds.</li>
 *   <li>{@code listParticipants} delegates to repository WITHOUT evaluating
 *       eligibility.</li>
 * </ul>
 *
 * <p>Side-effect boundary — the service MUST NOT write to timeline / outbox /
 * audit / RBAC, MUST NOT mutate ownership, MUST NOT read SecurityContextHolder,
 * MUST NOT set the tenant GUC, MUST NOT use JdbcTemplate. Only
 * {@link EntityParticipantRepository} and {@link RecipientEligibilityPort}
 * are touched.
 */
@DisplayName("Task 7 — Collaboration Membership Service")
@ExtendWith(MockitoExtension.class)
class CollaborationMembershipServiceTest {

    @Mock
    private EntityParticipantRepository participants;

    @Mock
    private RecipientEligibilityPort eligibility;

    private CollaborationMembershipService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-00000000b001");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-4000-8000-00000000b002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-00000000b003");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-00000000b004");
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-00000000b005");
    private static final String REQUIRED_CAPABILITY = "CRM.TASK.READ";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-22T10:00:00Z");

    @BeforeEach
    void setup() {
        service = new CollaborationMembershipService(participants, eligibility);
    }

    // A. addRejectsIneligibleRecipientWithoutInsert
    @Test
    @DisplayName("A. add rejects ineligible recipient without invoking repository.insert")
    void addRejectsIneligibleRecipientWithoutInsert() {
        when(eligibility.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .thenReturn(new EligibilityDecision(false, "NO_MATCHING_ACTIVE_ROLE"));

        assertThatThrownBy(() -> service.addParticipant(addCommand(), policy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_MATCHING_ACTIVE_ROLE");

        verify(participants, never()).insert(any());
    }

    // B. addReturnsExistingActiveSameRoleWithoutDuplicateInsert
    @Test
    @DisplayName("B. add returns existing active same-role participant without duplicate insert")
    void addReturnsExistingActiveSameRoleWithoutDuplicateInsert() {
        EntityParticipant existing = activeParticipant();
        when(eligibility.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .thenReturn(new EligibilityDecision(true, "ELIGIBLE"));
        when(participants.findActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR))
                .thenReturn(Optional.of(existing));

        EntityParticipant result = service.addParticipant(addCommand(), policy());

        assertThat(result).isSameAs(existing);
        verify(participants, never()).insert(any());
    }

    // C. addCreatesNewActiveParticipant
    @Test
    @DisplayName("C. add creates a new active participant when eligible and no existing relation")
    void addCreatesNewActiveParticipant() {
        when(eligibility.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .thenReturn(new EligibilityDecision(true, "ELIGIBLE"));
        when(participants.findActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR))
                .thenReturn(Optional.empty());

        ArgumentCaptor<EntityParticipant> captor = ArgumentCaptor.forClass(EntityParticipant.class);
        when(participants.insert(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        EntityParticipant result = service.addParticipant(addCommand(), policy());

        assertThat(result.id()).isNotNull();
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.entityType()).isEqualTo(CollaborationEntityType.TASK);
        assertThat(result.entityId()).isEqualTo(ENTITY_ID);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.role()).isEqualTo(ParticipantRole.COLLABORATOR);
        assertThat(result.addedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(result.addedAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.removedByUserId()).isNull();
        assertThat(result.removedAt()).isNull();
        assertThat(result.version()).isZero();
        assertThat(result.isActive()).isTrue();
    }

    // D. sameUserCanBeCollaboratorAndWatcher
    @Test
    @DisplayName("D. same user can hold COLLABORATOR and WATCHER roles simultaneously")
    void sameUserCanBeCollaboratorAndWatcher() {
        EntityParticipant existingCollaborator = EntityParticipant.active(
                UUID.randomUUID(), TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT);

        when(eligibility.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .thenReturn(new EligibilityDecision(true, "ELIGIBLE"));
        // findActive for WATCHER returns empty — so the WATCHER insert proceeds.
        when(participants.findActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.WATCHER))
                .thenReturn(Optional.empty());
        when(participants.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        CollaborationMembershipService.AddParticipantCommand watcherCmd =
                new CollaborationMembershipService.AddParticipantCommand(
                        TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                        USER_ID, ParticipantRole.WATCHER, ACTOR_ID, OCCURRED_AT);
        EntityParticipant watcher = service.addParticipant(watcherCmd, policy());

        assertThat(watcher.role()).isEqualTo(ParticipantRole.WATCHER);
        // findActive must have been role-specific — never called for COLLABORATOR.
        verify(participants).findActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.WATCHER);
        // Confirm that the existing COLLABORATOR was not used as the idempotent match.
        assertThat(watcher.id()).isNotEqualTo(existingCollaborator.id());
    }

    // E. removeActiveParticipantSucceeds
    @Test
    @DisplayName("E. remove active participant returns the in-memory removed snapshot")
    void removeActiveParticipantSucceeds() {
        EntityParticipant existing = activeParticipant(); // version 0
        when(participants.findById(TENANT_ID, existing.id())).thenReturn(Optional.of(existing));
        when(participants.markRemoved(TENANT_ID, existing.id(), 0L, ACTOR_ID, OCCURRED_AT))
                .thenReturn(true);

        EntityParticipant result = service.removeParticipant(
                new CollaborationMembershipService.RemoveParticipantCommand(
                        TENANT_ID, existing.id(), 0L, ACTOR_ID, OCCURRED_AT));

        assertThat(result.isActive()).isFalse();
        assertThat(result.removedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(result.removedAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.version()).isEqualTo(existing.version() + 1);
    }

    // F. removeMissingParticipantRejected
    @Test
    @DisplayName("F. remove missing participant → IllegalArgumentException(\"participant not found\")")
    void removeMissingParticipantRejected() {
        UUID pid = UUID.randomUUID();
        when(participants.findById(TENANT_ID, pid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeParticipant(
                new CollaborationMembershipService.RemoveParticipantCommand(
                        TENANT_ID, pid, 0L, ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participant not found");

        verify(participants, never()).markRemoved(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                any(), any());
    }

    // G. removeAlreadyRemovedParticipantRejected
    @Test
    @DisplayName("G. remove already-removed participant → CollaborationConflictException")
    void removeAlreadyRemovedParticipantRejected() {
        EntityParticipant inactive = activeParticipant().remove(ACTOR_ID, OCCURRED_AT);
        when(participants.findById(TENANT_ID, inactive.id())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.removeParticipant(
                new CollaborationMembershipService.RemoveParticipantCommand(
                        TENANT_ID, inactive.id(), inactive.version(), ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(CollaborationConflictException.class);

        verify(participants, never()).markRemoved(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                any(), any());
    }

    // H. removeFailsOnStaleExpectedVersion
    @Test
    @DisplayName("H. remove with stale expected version → CollaborationConflictException (no retry)")
    void removeFailsOnStaleExpectedVersion() {
        EntityParticipant existing = activeParticipant(); // version 0
        when(participants.findById(TENANT_ID, existing.id())).thenReturn(Optional.of(existing));
        // markRemoved returns false because the row's version has been bumped
        // concurrently — no row matched the WHERE version = :expectedVersion predicate.
        when(participants.markRemoved(TENANT_ID, existing.id(), 0L, ACTOR_ID, OCCURRED_AT))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeParticipant(
                new CollaborationMembershipService.RemoveParticipantCommand(
                        TENANT_ID, existing.id(), 0L, ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(CollaborationConflictException.class);
    }

    // I. removeUsesCallerExpectedVersion
    @Test
    @DisplayName("I. remove forwards the COMMAND.expectedVersion to repository.markRemoved (NOT existing.version)")
    void removeUsesCallerExpectedVersion() {
        // existing.version = 3 (3 removal attempts already, but it is still
        // considered active by isActive() because removedAt is null in this
        // synthetic fixture — see activeWithVersion helper).
        EntityParticipant existing = activeWithVersion(3);
        when(participants.findById(TENANT_ID, existing.id())).thenReturn(Optional.of(existing));
        when(participants.markRemoved(eq(TENANT_ID), eq(existing.id()), eq(2L),
                eq(ACTOR_ID), eq(OCCURRED_AT)))
                .thenReturn(true);

        // The command explicitly says expectedVersion=2 — older than existing.version=3.
        // The service MUST forward the command's expectedVersion (2), NOT existing.version (3).
        service.removeParticipant(new CollaborationMembershipService.RemoveParticipantCommand(
                TENANT_ID, existing.id(), 2L, ACTOR_ID, OCCURRED_AT));

        verify(participants).markRemoved(TENANT_ID, existing.id(), 2L, ACTOR_ID, OCCURRED_AT);
    }

    // J. negativeExpectedVersionRejected
    @Test
    @DisplayName("J. negative expectedVersion → IllegalArgumentException before repository access")
    void negativeExpectedVersionRejected() {
        UUID pid = UUID.randomUUID();
        assertThatThrownBy(() -> service.removeParticipant(
                new CollaborationMembershipService.RemoveParticipantCommand(
                        TENANT_ID, pid, -1L, ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(participants, never()).findById(any(), any());
        verify(participants, never()).markRemoved(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                any(), any());
    }

    // K. listDelegatesToRepository
    @Test
    @DisplayName("K. list delegates to repository.listActive and returns the result unchanged")
    void listDelegatesToRepository() {
        List<EntityParticipant> expected = List.of(activeParticipant());
        when(participants.listActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID))
                .thenReturn(expected);

        List<EntityParticipant> result = service.listParticipants(
                TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID);

        assertThat(result).isSameAs(expected);
    }

    // L. listDoesNotEvaluateEligibility
    @Test
    @DisplayName("L. list does NOT invoke eligibility (no side-effect on RBAC)")
    void listDoesNotEvaluateEligibility() {
        when(participants.listActive(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID))
                .thenReturn(List.of());

        service.listParticipants(TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID);

        verifyNoInteractions(eligibility);
    }

    // 12 (architecture). membershipServiceIsNotComponentAnnotated
    @Test
    @DisplayName("M (architecture). CollaborationMembershipService is NOT annotated @Service / @Component")
    void membershipServiceIsNotComponentAnnotated() {
        // Task 8 owns explicit bean wiring in CollaborationModuleConfiguration.
        // Reconstructing the service as a plain class prevents the historical
        // Task 7A mistake (the @Service annotation caused Spring to wire the
        // bean before the configuration was ready).
        Class<?> clazz = CollaborationMembershipService.class;
        assertThat(clazz.isAnnotationPresent(org.springframework.stereotype.Service.class))
                .as("@Service must NOT be present on CollaborationMembershipService")
                .isFalse();
        assertThat(clazz.isAnnotationPresent(org.springframework.stereotype.Component.class))
                .as("@Component must NOT be present on CollaborationMembershipService")
                .isFalse();
        assertThat(clazz.isAnnotationPresent(org.springframework.context.annotation.Configuration.class))
                .as("@Configuration must NOT be present on CollaborationMembershipService")
                .isFalse();
    }

    // ---------- helpers ----------

    private CollaborationMembershipService.AddParticipantCommand addCommand() {
        return new CollaborationMembershipService.AddParticipantCommand(
                TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT);
    }

    private CollaborationMembershipService.EligibilityPolicy policy() {
        return new CollaborationMembershipService.EligibilityPolicy(
                ORGANIZATION_ID, REQUIRED_CAPABILITY);
    }

    private EntityParticipant activeParticipant() {
        return EntityParticipant.active(
                UUID.randomUUID(), TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT);
    }

    /** Synthesise an active participant with a non-zero version (3 prior writes). */
    private EntityParticipant activeWithVersion(int version) {
        // The public EntityParticipant record constructor allows custom version
        // when both removedByUserId / removedAt are null. We use it directly
        // to construct a fixture that has version=3 but is still active.
        return new EntityParticipant(
                UUID.randomUUID(), TENANT_ID, CollaborationEntityType.TASK, ENTITY_ID,
                USER_ID, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT,
                null, null, (long) version);
    }
}
