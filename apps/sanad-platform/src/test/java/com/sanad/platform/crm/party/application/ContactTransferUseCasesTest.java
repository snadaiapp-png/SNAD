package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationConflictException;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.AddParticipantCommand;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.EligibilityPolicy;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.RemoveParticipantCommand;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * Task C5 — Canonical Contact owner transfer — focused unit tests.
 *
 * <p>Proves the C5 transfer contract via Mockito stubs of
 * {@link ContactRepository}, {@link CollaborationMembershipService}, and
 * {@link RecipientEligibilityPort}. The PostgreSQL Direct proof is in
 * {@code ContactTransferPostgresTest}.</p>
 *
 * <h3>Test matrix (per C5 spec Section 7)</h3>
 * <ul>
 *   <li>A. successful transfer changes owner.</li>
 *   <li>B. successful transfer increments Contact version exactly once.</li>
 *   <li>C. target user eligibility uses tenantId + null org + CRM.CONTACT.READ.</li>
 *   <li>D. ineligible target owner rejects transfer before owner mutation.</li>
 *   <li>E. archived Contact rejects transfer.</li>
 *   <li>F. stale expectedVersion rejects transfer.</li>
 *   <li>G. active COLLABORATOR target: removed before becoming owner.</li>
 *   <li>H. active WATCHER target: removed before becoming owner.</li>
 *   <li>I. historical/removed participant: does not block owner transfer.</li>
 *   <li>J. target user == current owner: idempotent no-op.</li>
 *   <li>K. previous owner becomes WATCHER when retention=true.</li>
 *   <li>L. default API uses retainPreviousOwnerAsWatcher=true.</li>
 *   <li>M. retainPreviousOwnerAsWatcher=false: no WATCHER added.</li>
 *   <li>N. previousOwnerId=null: no WATCHER attempt.</li>
 *   <li>O. previousOwner == newOwner: no WATCHER mutation.</li>
 *   <li>P. previous owner existing COLLABORATOR after ownership change is
 *       normalized to WATCHER if retention=true.</li>
 *   <li>Q. recipient eligibility failure leaves everything unchanged.</li>
 *   <li>R. participant optimistic conflict causes entire transfer to fail.</li>
 *   <li>S. required command values are validated.</li>
 * </ul>
 */
class ContactTransferUseCasesTest {

    private static final UUID TENANT_ID = UUID.fromString("c5c50000-0000-4000-8000-000000000001");
    private static final UUID CONTACT_ID = UUID.fromString("c5c50000-0000-4000-8000-000000000002");
    private static final UUID USER_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a001");
    private static final UUID USER_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b001");
    private static final UUID USER_C = UUID.fromString("c5c50000-0000-4000-8000-00000000c001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private ContactRepository contactRepository;
    private CollaborationMembershipService membershipService;
    private RecipientEligibilityPort eligibilityPort;
    private ContactTransferUseCases service;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        membershipService = mock(CollaborationMembershipService.class);
        eligibilityPort = mock(RecipientEligibilityPort.class);
        service = new ContactTransferUseCases(contactRepository, membershipService, eligibilityPort);
    }

    private ContactRecord activeContact(UUID ownerUserId, long version) {
        return new ContactRecord(
                CONTACT_ID, version, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ACTIVE", ownerUserId, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    private ContactRecord archivedContact(UUID ownerUserId, long version) {
        return new ContactRecord(
                CONTACT_ID, version, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ARCHIVED", ownerUserId, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    private EntityParticipant stubActiveParticipant(UUID userId, ParticipantRole role, long version) {
        return new EntityParticipant(
                UUID.randomUUID(), TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID,
                userId, role, USER_A, OCCURRED_AT, null, null, version);
    }

    private EntityParticipant stubRemovedParticipant(UUID userId, ParticipantRole role, long version) {
        return new EntityParticipant(
                UUID.randomUUID(), TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID,
                userId, role, USER_A, OCCURRED_AT.minusSeconds(60), USER_A, OCCURRED_AT, version);
    }

    private void eligible(UUID userId) {
        when(eligibilityPort.evaluate(eq(TENANT_ID), eq(userId), any(), eq("CRM.CONTACT.READ")))
                .thenReturn(new EligibilityDecision(true, "ELIGIBLE"));
    }

    private void ineligible(UUID userId, String reason) {
        when(eligibilityPort.evaluate(eq(TENANT_ID), eq(userId), any(), eq("CRM.CONTACT.READ")))
                .thenReturn(new EligibilityDecision(false, reason));
    }

    // ── A. successful transfer changes owner ─────────────────────────────

    @Test
    @DisplayName("A. successful transfer changes owner")
    void a_successfulTransferChangesOwner() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        ContactRecord after = new ContactRecord(
                CONTACT_ID, 1L, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ACTIVE", USER_B, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
        when(contactRepository.transferOwner(TENANT_ID, USER_A, CONTACT_ID, USER_B, 0L, OCCURRED_AT))
                .thenReturn(after);

        ContactRecord result = service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true));

        assertThat(result.ownerUserId()).isEqualTo(USER_B);
        verify(contactRepository).transferOwner(TENANT_ID, USER_A, CONTACT_ID, USER_B, 0L, OCCURRED_AT);
    }

    // ── B. successful transfer increments Contact version exactly once ────

    @Test
    @DisplayName("B. successful transfer increments Contact version exactly once")
    void b_successfulTransferIncrementsVersionOnce() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 7L));
        eligible(USER_B);
        ContactRecord after = new ContactRecord(
                CONTACT_ID, 8L, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ACTIVE", USER_B, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
        when(contactRepository.transferOwner(TENANT_ID, USER_A, CONTACT_ID, USER_B, 7L, OCCURRED_AT))
                .thenReturn(after);

        ContactRecord result = service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 7L, USER_A, OCCURRED_AT, true));

        assertThat(result.version()).isEqualTo(8L);
        // transferOwner primitive called exactly once
        verify(contactRepository).transferOwner(any(), any(), any(), any(), anyLong(), any());
    }

    // ── C. target user eligibility uses tenantId + null org + CRM.CONTACT.READ ──

    @Test
    @DisplayName("C. target eligibility uses tenantId + null organizationId + CRM.CONTACT.READ")
    void c_targetEligibilityPolicy() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true));

        // Eligibility port MUST be invoked with (tenantId, newOwnerUserId, null, "CRM.CONTACT.READ")
        verify(eligibilityPort).evaluate(TENANT_ID, USER_B, null, "CRM.CONTACT.READ");
    }

    // ── D. ineligible target owner rejects transfer before owner mutation ──

    @Test
    @DisplayName("D. ineligible target owner rejects transfer before owner mutation")
    void d_ineligibleTargetRejectsTransferBeforeMutation() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        ineligible(USER_B, "USER_NOT_ACTIVE_IN_TENANT");

        assertThatThrownBy(() ->
                service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                        TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient not eligible")
                .hasMessageContaining("USER_NOT_ACTIVE_IN_TENANT");

        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).removeParticipant(any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── E. archived Contact rejects transfer ──────────────────────────────

    @Test
    @DisplayName("E. archived Contact rejects transfer")
    void e_archivedContactRejectsTransfer() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(archivedContact(USER_A, 5L));

        assertThatThrownBy(() ->
                service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                        TENANT_ID, CONTACT_ID, USER_B, 5L, USER_A, OCCURRED_AT, true)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CONFLICT));

        verify(eligibilityPort, never()).evaluate(any(), any(), any(), any());
        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).removeParticipant(any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── F. stale expectedVersion rejects transfer ──────────────────────────

    @Test
    @DisplayName("F. stale expectedVersion rejects transfer")
    void f_staleExpectedVersionRejectsTransfer() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 5L));
        // Caller passes 3L but actual is 5L → conflict

        assertThatThrownBy(() ->
                service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                        TENANT_ID, CONTACT_ID, USER_B, 3L, USER_A, OCCURRED_AT, true)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        verify(eligibilityPort, never()).evaluate(any(), any(), any(), any());
        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).removeParticipant(any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── G. active COLLABORATOR target: removed before becoming owner ─────

    @Test
    @DisplayName("G. active COLLABORATOR target: removed before becoming owner")
    void g_activeCollaboratorTargetRemovedBeforeOwnerUpdate() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        EntityParticipant existingCollaborator = stubActiveParticipant(USER_B, ParticipantRole.COLLABORATOR, 3L);
        when(membershipService.listParticipants(TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existingCollaborator));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, false));

        ArgumentCaptor<RemoveParticipantCommand> captor = ArgumentCaptor.forClass(RemoveParticipantCommand.class);
        verify(membershipService).removeParticipant(captor.capture());
        assertThat(captor.getValue().participantId()).isEqualTo(existingCollaborator.id());
        assertThat(captor.getValue().expectedVersion()).isEqualTo(existingCollaborator.version());
    }

    // ── H. active WATCHER target: removed before becoming owner ───────────

    @Test
    @DisplayName("H. active WATCHER target: removed before becoming owner")
    void h_activeWatcherTargetRemovedBeforeOwnerUpdate() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        EntityParticipant existingWatcher = stubActiveParticipant(USER_B, ParticipantRole.WATCHER, 2L);
        when(membershipService.listParticipants(TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existingWatcher));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, false));

        verify(membershipService).removeParticipant(any(RemoveParticipantCommand.class));
    }

    // ── I. historical/removed participant: does not block owner transfer ──

    @Test
    @DisplayName("I. historical/removed participant does not block owner transfer")
    void i_historicalParticipantDoesNotBlockTransfer() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        EntityParticipant historical = stubRemovedParticipant(USER_B, ParticipantRole.COLLABORATOR, 4L);
        when(membershipService.listParticipants(TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(historical));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, false));

        // No removal of the historical participant (it's already removed_at != null)
        verify(membershipService, never()).removeParticipant(any());
        verify(contactRepository).transferOwner(any(), any(), any(), any(), anyLong(), any());
    }

    // ── J. target user == current owner: idempotent no-op ────────────────

    @Test
    @DisplayName("J. target user == current owner: idempotent no-op")
    void j_targetEqualsCurrentOwnerIsNoOp() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 7L));

        ContactRecord result = service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_A, 7L, USER_A, OCCURRED_AT, true));

        assertThat(result.ownerUserId()).isEqualTo(USER_A);
        assertThat(result.version()).isEqualTo(7L);
        // No eligibility evaluation, no participant removal, no transfer
        verify(eligibilityPort, never()).evaluate(any(), any(), any(), any());
        verify(membershipService, never()).removeParticipant(any());
        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── K. previous owner becomes WATCHER when retention=true ────────────

    @Test
    @DisplayName("K. previous owner becomes WATCHER when retention=true")
    void k_previousOwnerBecomesWatcherWhenRetentionTrue() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));
        when(membershipService.listParticipants(TENANT_ID, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of()); // previous owner has no active participant row
        EntityParticipant addedWatcher = stubActiveParticipant(USER_A, ParticipantRole.WATCHER, 0L);
        when(membershipService.addParticipant(any(), any())).thenReturn(addedWatcher);

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true));

        ArgumentCaptor<AddParticipantCommand> addCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(addCaptor.capture(), any(EligibilityPolicy.class));
        assertThat(addCaptor.getValue().role()).isEqualTo(ParticipantRole.WATCHER);
        assertThat(addCaptor.getValue().userId()).isEqualTo(USER_A);
    }

    // ── L. default API uses retainPreviousOwnerAsWatcher=true ─────────────

    @Test
    @DisplayName("L. default API uses retainPreviousOwnerAsWatcher=true")
    void l_defaultApiUsesRetentionTrue() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));
        when(membershipService.listParticipants(any(), any(), any()))
                .thenReturn(List.of());
        when(membershipService.addParticipant(any(), any()))
                .thenReturn(stubActiveParticipant(USER_A, ParticipantRole.WATCHER, 0L));

        // Use the default-on overload (retainPreviousOwnerAsWatcher defaults to true)
        ContactRecord result = service.transferContact(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT);

        // WATCHER add was attempted for USER_A (the previous owner)
        ArgumentCaptor<AddParticipantCommand> captor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(captor.capture(), any(EligibilityPolicy.class));
        assertThat(captor.getValue().userId()).isEqualTo(USER_A);
        assertThat(captor.getValue().role()).isEqualTo(ParticipantRole.WATCHER);
    }

    // ── M. retainPreviousOwnerAsWatcher=false: no WATCHER added ───────────

    @Test
    @DisplayName("M. retainPreviousOwnerAsWatcher=false: no WATCHER added")
    void m_retentionFalseNoWatcher() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, false));

        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── N. previousOwnerId=null: no WATCHER attempt ──────────────────────

    @Test
    @DisplayName("N. previousOwnerId=null: no WATCHER attempt")
    void n_previousOwnerNullNoWatcher() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(null, 0L)); // previous owner is null
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true));

        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── O. previousOwner == newOwner: no WATCHER mutation ───────────────

    @Test
    @DisplayName("O. previousOwner == newOwner: no WATCHER mutation")
    void o_previousEqualsNewOwnerNoWatcherMutation() {
        // This case is covered by J (idempotent no-op), but exercise it
        // explicitly with retention=true to be defensive.
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 5L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_A, 5L, USER_A, OCCURRED_AT, true));

        // J path: no-op. No WATCHER add, no participant mutation, no transfer.
        verify(membershipService, never()).addParticipant(any(), any());
        verify(membershipService, never()).removeParticipant(any());
        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
    }

    // ── P. previous owner COLLABORATOR after ownership change → normalized to WATCHER ──

    @Test
    @DisplayName("P. previous owner COLLABORATOR after ownership change → normalized to WATCHER")
    void p_previousOwnerCollaboratorNormalizedToWatcher() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        eligible(USER_B);
        when(contactRepository.transferOwner(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(activeContact(USER_B, 1L));
        // After ownership change, USER_A is somehow still an active COLLABORATOR
        // (e.g., they were both owner AND COLLABORATOR before C3 — should never
        // happen given C3 invariants, but the service must be defensive).
        EntityParticipant priorOwnerCollab = stubActiveParticipant(USER_A, ParticipantRole.COLLABORATOR, 9L);
        when(membershipService.listParticipants(any(), any(), any()))
                .thenReturn(List.of(priorOwnerCollab));
        when(membershipService.addParticipant(any(), any()))
                .thenReturn(stubActiveParticipant(USER_A, ParticipantRole.WATCHER, 0L));

        service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true));

        // The COLLABORATOR row must be removed
        ArgumentCaptor<RemoveParticipantCommand> rmCaptor = ArgumentCaptor.forClass(RemoveParticipantCommand.class);
        verify(membershipService).removeParticipant(rmCaptor.capture());
        assertThat(rmCaptor.getValue().participantId()).isEqualTo(priorOwnerCollab.id());

        // Then a WATCHER must be added for USER_A
        ArgumentCaptor<AddParticipantCommand> addCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(addCaptor.capture(), any(EligibilityPolicy.class));
        assertThat(addCaptor.getValue().role()).isEqualTo(ParticipantRole.WATCHER);
        assertThat(addCaptor.getValue().userId()).isEqualTo(USER_A);
    }

    // ── Q. recipient eligibility failure leaves everything unchanged ─────

    @Test
    @DisplayName("Q. recipient eligibility failure leaves everything unchanged")
    void q_recipientEligibilityFailureLeavesEverythingUnchanged() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        ineligible(USER_B, "USER_NOT_ACTIVE_IN_TENANT");

        assertThatThrownBy(() ->
                service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                        TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipService, never()).removeParticipant(any());
        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── R. participant optimistic conflict causes entire transfer to fail ──

    @Test
    @DisplayName("R. participant optimistic conflict causes entire transfer to fail")
    void r_participantOptimisticConflictCausesTransferFailure() {
        when(contactRepository.findByIdForUpdate(TENANT_ID, CONTACT_ID))
                .thenReturn(activeContact(USER_A, 0L));
        EntityParticipant existing = stubActiveParticipant(USER_B, ParticipantRole.COLLABORATOR, 3L);
        when(membershipService.listParticipants(any(), any(), any()))
                .thenReturn(List.of(existing));
        eligible(USER_B);
        // removeParticipant throws CollaborationConflictException — the entire
        // @Transactional transfer must roll back.
        when(membershipService.removeParticipant(any()))
                .thenThrow(new CollaborationConflictException("participant state conflict"));

        assertThatThrownBy(() ->
                service.transferContact(new ContactTransferUseCases.TransferContactCommand(
                        TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true)))
                .isInstanceOf(CollaborationConflictException.class)
                .hasMessageContaining("participant state conflict");

        verify(contactRepository, never()).transferOwner(any(), any(), any(), any(), anyLong(), any());
        verify(membershipService, never()).addParticipant(any(), any());
    }

    // ── S. required command values are validated ──────────────────────────

    @Test
    @DisplayName("S. required command values are validated")
    void s_commandValidation() {
        // null tenantId
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                null, CONTACT_ID, USER_B, 0L, USER_A, OCCURRED_AT, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
        // null contactId
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, null, USER_B, 0L, USER_A, OCCURRED_AT, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("contactId");
        // null newOwnerUserId
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, null, 0L, USER_A, OCCURRED_AT, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("newOwnerUserId");
        // null actorId
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, null, OCCURRED_AT, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actorId");
        // null occurredAt
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, 0L, USER_A, null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("occurredAt");
        // negative expectedVersion
        assertThatThrownBy(() -> new ContactTransferUseCases.TransferContactCommand(
                TENANT_ID, CONTACT_ID, USER_B, -1L, USER_A, OCCURRED_AT, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }

    // ── C5-R1: default overload @Transactional metadata contract ──
    //
    // Production contract: the default public overload
    //   transferContact(UUID, UUID, UUID, long, UUID, Instant)
    // is the externally-callable delegating entrypoint. Without
    // @Transactional, Spring's proxy does not intercept it, so no
    // transaction is started — meaning TenantRlsConnectionHandler does
    // not apply SET LOCAL app.tenant_id, FOR UPDATE lifetime is not
    // transaction-scoped, and the inner command overload's
    // @Transactional annotation is NOT re-intercepted (Spring proxy
    // self-invocation). This breaks CONTACT_ROW_FIRST lock semantics,
    // RLS fail-closed, participant normalization atomicity, owner
    // UPDATE rollback, and previous-owner WATCHER rollback.
    //
    // The fix is to annotate the default overload with @Transactional
    // so the external entry begins a transaction before delegating to
    // the command overload (which joins the SAME transaction via
    // PROPAGATION_REQUIRED default semantics).

    private TransactionAttribute transactionAttributeFor(String methodName,
                                                          Class<?>... paramTypes) throws Exception {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        Method method = ContactTransferUseCases.class.getMethod(methodName, paramTypes);
        return source.getTransactionAttribute(method, ContactTransferUseCases.class);
    }

    @Test
    @DisplayName("C5-R1. Command overload transferContact(TransferContactCommand) has @Transactional metadata")
    void commandOverloadHasTransactionalAttribute() throws Exception {
        TransactionAttribute attr = transactionAttributeFor(
                "transferContact", ContactTransferUseCases.TransferContactCommand.class);
        assertThat(attr)
                .as("transferContact(TransferContactCommand) must carry @Transactional metadata so the "
                        + "Spring proxy begins a transaction on external invocation")
                .isNotNull();
    }

    @Test
    @DisplayName("C5-R1. Default overload transferContact(UUID, UUID, UUID, long, UUID, Instant) has @Transactional metadata")
    void defaultOverloadHasTransactionalAttribute() throws Exception {
        TransactionAttribute attr = transactionAttributeFor(
                "transferContact",
                UUID.class, UUID.class, UUID.class, long.class, UUID.class, Instant.class);
        // RED before fix: attr will be null because no @Transactional is on
        // the default overload, so the default public entrypoint is not
        // transaction-safe (self-invocation does not cross Spring proxy).
        assertThat(attr)
                .as("default transferContact(UUID, UUID, UUID, long, UUID, Instant) must carry "
                        + "@Transactional metadata so an external caller enters a Spring transaction")
                .isNotNull();
    }

    @Test
    @DisplayName("C5-R1. Default overload transaction semantics: PROPAGATION_REQUIRED, readOnly=false")
    void defaultOverloadTransactionSemantics() throws Exception {
        TransactionAttribute attr = transactionAttributeFor(
                "transferContact",
                UUID.class, UUID.class, UUID.class, long.class, UUID.class, Instant.class);
        // RED before fix: attr is null, so we cannot inspect propagation /
        // readOnly. Use softNull check first so the assertion message is
        // readable rather than NPE.
        assertThat(attr)
                .as("default overload must carry @Transactional metadata before we can inspect semantics")
                .isNotNull();
        assertThat(attr.isReadOnly())
                .as("default overload must NOT be readOnly (it issues Contact UPDATE + participant mutations)")
                .isFalse();
        // Default Spring @Transactional is PROPAGATION_REQUIRED, which is what
        // we need: external call starts a new transaction, internal self-call
        // joins the existing transaction.
        // TransactionAttribute.getPropagationBehavior() returns the int constant
        // from org.springframework.transaction.TransactionDefinition.
        // PROPAGATION_REQUIRED == 0.
        assertThat(attr.getPropagationBehavior())
                .as("default overload must use PROPAGATION_REQUIRED (default Spring semantics)")
                .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
    }
}
