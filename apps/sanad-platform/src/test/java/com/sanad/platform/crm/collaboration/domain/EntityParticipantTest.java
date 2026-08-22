package com.sanad.platform.crm.collaboration.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityParticipantTest {

    private static final UUID T = UUID.randomUUID();
    private static final UUID E = UUID.randomUUID();
    private static final UUID U = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final Instant AD = Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant RD = Instant.parse("2026-08-21T21:00:00Z");

    @Test void activeParticipantCanBeRemovedExactlyOnce() {
        var active = EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD);
        var removed = active.remove(A, RD);
        assertThat(active.isActive()).isTrue();
        assertThat(removed.isActive()).isFalse();
        assertThat(removed.removedByUserId()).isEqualTo(A);
        assertThat(removed.removedAt()).isEqualTo(RD);
        assertThat(removed.version()).isEqualTo(1L);
        assertThatThrownBy(() -> removed.remove(A, RD.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test void nullTenantIdRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), null, CollaborationEntityType.CONTACT, E, U, ParticipantRole.WATCHER, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullIdRejected() { assertThatThrownBy(() -> EntityParticipant.active(null, T, CollaborationEntityType.CONTACT, E, U, ParticipantRole.WATCHER, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullEntityTypeRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, null, E, U, ParticipantRole.WATCHER, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullEntityIdRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, null, U, ParticipantRole.WATCHER, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullUserIdRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, E, null, ParticipantRole.WATCHER, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullRoleRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, E, U, null, A, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullAddedByRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, E, U, ParticipantRole.WATCHER, null, AD)).isInstanceOf(NullPointerException.class); }
    @Test void nullAddedAtRejected() { assertThatThrownBy(() -> EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, E, U, ParticipantRole.WATCHER, A, null)).isInstanceOf(NullPointerException.class); }

    @Test void removedByWithoutAtRejected() { assertThatThrownBy(() -> new EntityParticipant(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD, A, null, 0L)).isInstanceOf(IllegalArgumentException.class); }
    @Test void removedAtWithoutByRejected() { assertThatThrownBy(() -> new EntityParticipant(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD, null, RD, 0L)).isInstanceOf(IllegalArgumentException.class); }
    @Test void negativeVersionRejected() { assertThatThrownBy(() -> new EntityParticipant(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD, null, null, -1L)).isInstanceOf(IllegalArgumentException.class); }

    @Test void collaborationEntityTypeHasExactValues() { assertThat(EnumSet.allOf(CollaborationEntityType.class)).containsExactlyInAnyOrder(CollaborationEntityType.CONTACT, CollaborationEntityType.TASK, CollaborationEntityType.CASE); }
    @Test void participantRoleHasExactValues() {
        assertThat(EnumSet.allOf(ParticipantRole.class)).containsExactlyInAnyOrder(ParticipantRole.COLLABORATOR, ParticipantRole.WATCHER);
        assertThat(Arrays.stream(ParticipantRole.values()).map(Enum::name).toList()).doesNotContain("OWNER").doesNotContain("REVIEWER");
    }

    @Test void activeVersionZero() { var p = EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CONTACT, E, U, ParticipantRole.WATCHER, A, AD); assertThat(p.version()).isZero(); assertThat(p.isActive()).isTrue(); }
    @Test void removeDoesNotMutateOriginal() { var active = EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.CASE, E, U, ParticipantRole.COLLABORATOR, A, AD); var removed = active.remove(A, RD); assertThat(active.isActive()).isTrue(); assertThat(active.version()).isZero(); assertThat(removed.version()).isEqualTo(1L); assertThat(active).isNotSameAs(removed); }
    @Test void removeNullActorRejected() { var p = EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD); assertThatThrownBy(() -> p.remove(null, RD)).isInstanceOf(NullPointerException.class); }
    @Test void removeNullAtRejected() { var p = EntityParticipant.active(UUID.randomUUID(), T, CollaborationEntityType.TASK, E, U, ParticipantRole.COLLABORATOR, A, AD); assertThatThrownBy(() -> p.remove(A, null)).isInstanceOf(NullPointerException.class); }
}
