package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task C6-A — ContactUseCases Strategy A1 adapter unit tests.
 *
 * <p>Proves the A1 orchestration contract via Mockito stubs of
 * {@link ContactRepository}, {@link ContactTransferUseCases},
 * {@link AuditPort}, and {@link TimelineEventPort}.</p>
 */
class ContactUseCasesA1AdapterTest {

    private static final UUID TENANT_ID = UUID.fromString("c6a00000-0000-4000-8000-000000000001");
    private static final UUID CONTACT_ID = UUID.fromString("c6a00000-0000-4000-8000-000000000002");
    private static final UUID USER_A = UUID.fromString("c6a00000-0000-4000-8000-00000000a001");
    private static final UUID USER_B = UUID.fromString("c6a00000-0000-4000-8000-00000000b001");
    private static final UUID ACTOR_ID = UUID.fromString("c6a00000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private ContactRepository repo;
    private ContactTransferUseCases transferUseCases;
    private AuditPort audit;
    private TimelineEventPort timeline;
    private ContactUseCases useCases;

    @BeforeEach
    void setUp() {
        repo = mock(ContactRepository.class);
        transferUseCases = mock(ContactTransferUseCases.class);
        audit = mock(AuditPort.class);
        timeline = mock(TimelineEventPort.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        useCases = new ContactUseCases(repo, audit, timeline, mapper, transferUseCases);
    }

    private ContactRecord activeContact(UUID owner, long version) {
        return new ContactRecord(
                CONTACT_ID, version, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ACTIVE", owner, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    private ContactRecord archivedContact(UUID owner, long version) {
        return new ContactRecord(
                CONTACT_ID, version, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ARCHIVED", owner, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    // ── B. Owner-only delegates to ContactTransferUseCases ────────────────

    @Test
    @DisplayName("B. Owner-only update delegates to ContactTransferUseCases")
    void ownerOnlyDelegatesToTransfer() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));
        ContactRecord transferred = activeContact(USER_B, 6L);
        when(transferUseCases.transferContact(
                eq(TENANT_ID), eq(CONTACT_ID), eq(USER_B), eq(5L), eq(ACTOR_ID), any()))
                .thenReturn(transferred);

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, null, null, null, null, null, null, USER_B, null), 5L);

        assertThat(result.ownerUserId()).isEqualTo(USER_B);
        assertThat(result.version()).isEqualTo(6L);
        verify(transferUseCases).transferContact(eq(TENANT_ID), eq(CONTACT_ID), eq(USER_B), eq(5L), eq(ACTOR_ID), any());
        verify(repo, never()).update(any(), any(), any(), any(), anyLong());
    }

    // ── C. Ordinary-only does NOT call transfer ──────────────────────────

    @Test
    @DisplayName("C. Ordinary-only update does NOT call ContactTransferUseCases")
    void ordinaryOnlyDoesNotCallTransfer() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));
        when(repo.update(any(), any(), any(), any(), anyLong()))
                .thenReturn(activeContact(USER_A, 6L));

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, "NewName", null, null, null, null, null, null, null), 5L);

        assertThat(result.version()).isEqualTo(6L);
        verifyNoInteractions(transferUseCases);
        verify(repo).update(any(), any(), any(), any(), eq(5L));
    }

    // ── D+E. Mixed: transfer first, then ordinary update with ownerUserId=null ──

    @Test
    @DisplayName("D+E. Mixed update: transfer first (N→N+1), then ordinary with ownerUserId=null and expectedVersion=N+1")
    void mixedUpdateTransferFirstThenOrdinary() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));
        ContactRecord afterTransfer = activeContact(USER_B, 6L);
        when(transferUseCases.transferContact(
                eq(TENANT_ID), eq(CONTACT_ID), eq(USER_B), eq(5L), eq(ACTOR_ID), any()))
                .thenReturn(afterTransfer);
        when(repo.update(any(), any(), any(), any(), anyLong()))
                .thenReturn(new ContactRecord(
                        CONTACT_ID, 7L, null, "NewName", "Doe", "NewName Doe",
                        "jane@example.com", "jane@example.com", null, null, null,
                        "ACTIVE", USER_B, "UNKNOWN", OCCURRED_AT, OCCURRED_AT));

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, "NewName", null, null, null, null, null, USER_B, null), 5L);

        assertThat(result.version()).isEqualTo(7L);
        assertThat(result.givenName()).isEqualTo("NewName");

        // Transfer called first with expectedVersion=5 (current.version)
        verify(transferUseCases).transferContact(eq(TENANT_ID), eq(CONTACT_ID), eq(USER_B), eq(5L), eq(ACTOR_ID), any());

        // Then ordinary update with sanitized command (ownerUserId=null) and expectedVersion=6 (afterTransfer.version)
        ArgumentCaptor<UpdateContactCommand> cmdCaptor = ArgumentCaptor.forClass(UpdateContactCommand.class);
        ArgumentCaptor<Long> versionCaptor = ArgumentCaptor.forClass(Long.class);
        verify(repo).update(any(), any(), any(), cmdCaptor.capture(), versionCaptor.capture());
        assertThat(cmdCaptor.getValue().ownerUserId())
                .as("Sanitized ordinary update command must have ownerUserId=null")
                .isNull();
        assertThat(versionCaptor.getValue())
                .as("Ordinary update must use afterTransfer.version() as expectedVersion")
                .isEqualTo(6L);
    }

    // ── F. Same-owner-only is no-op ───────────────────────────────────────

    @Test
    @DisplayName("F. Same-owner-only is no-op (no transfer, no repo.update, no version change)")
    void sameOwnerOnlyIsNoOp() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, null, null, null, null, null, null, USER_A, null), 5L);

        assertThat(result.version()).isEqualTo(5L);
        verifyNoInteractions(transferUseCases);
        verify(repo, never()).update(any(), any(), any(), any(), anyLong());
    }

    // ── G. Empty update is no-op ─────────────────────────────────────────

    @Test
    @DisplayName("G. Empty update is no-op (no transfer, no repo.update, no version change)")
    void emptyUpdateIsNoOp() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, null, null, null, null, null, null, null, null), 5L);

        assertThat(result.version()).isEqualTo(5L);
        verifyNoInteractions(transferUseCases);
        verify(repo, never()).update(any(), any(), any(), any(), anyLong());
    }

    // ── H. Same-owner + ordinary performs ordinary update only ───────────

    @Test
    @DisplayName("H. Same-owner + ordinary performs ordinary update only (no transfer)")
    void sameOwnerWithOrdinaryPerformsOrdinaryOnly() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));
        when(repo.update(any(), any(), any(), any(), anyLong()))
                .thenReturn(activeContact(USER_A, 6L));

        ContactRecord result = useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, "NewName", null, null, null, null, null, USER_A, null), 5L);

        assertThat(result.version()).isEqualTo(6L);
        verifyNoInteractions(transferUseCases);
        verify(repo).update(any(), any(), any(), any(), eq(5L));
    }

    // ── I. Stale expectedVersion rejects before mutation ─────────────────

    @Test
    @DisplayName("I. Stale expectedVersion rejects before any mutation")
    void staleVersionRejectsBeforeMutation() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(activeContact(USER_A, 5L));

        assertThatThrownBy(() -> useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, "NewName", null, null, null, null, null, USER_B, null), 3L))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        verifyNoInteractions(transferUseCases);
        verify(repo, never()).update(any(), any(), any(), any(), anyLong());
    }

    // ── Archived Contact rejects mutation ────────────────────────────────

    @Test
    @DisplayName("Archived Contact rejects update")
    void archivedContactRejectsUpdate() {
        when(repo.findById(TENANT_ID, CONTACT_ID)).thenReturn(archivedContact(USER_A, 5L));

        assertThatThrownBy(() -> useCases.update(TENANT_ID, ACTOR_ID, CONTACT_ID,
                new UpdateContactCommand(null, "NewName", null, null, null, null, null, USER_B, null), 5L))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CONFLICT));

        verifyNoInteractions(transferUseCases);
        verify(repo, never()).update(any(), any(), any(), any(), anyLong());
    }
}
