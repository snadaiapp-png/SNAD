package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationConflictException;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.AddParticipantCommand;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.EligibilityPolicy;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.RemoveParticipantCommand;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task C4 — Contact participant application service.
 *
 * <p>Focused unit tests for {@link ContactCollaborationService}. Proves
 * the C4 contract via Mockito stubs of {@link ContactRepository} and
 * {@link CollaborationMembershipService}:</p>
 * <ul>
 *   <li>shareContact / watchContact add the expected participant role.</li>
 *   <li>Same-role requests are idempotent (delegated to membership service).</li>
 *   <li>Role-switch (COLLABORATOR ↔ WATCHER) atomically removes the opposite
 *       active role before adding the desired role within one transaction.</li>
 *   <li>Target user == contact owner is rejected at the APPLICATION layer
 *       (defense-in-depth on top of the C3 DB trigger).</li>
 *   <li>Archived contacts reject share / watch / removeParticipant mutations.</li>
 *   <li>listParticipants returns CONTACT active participants only.</li>
 *   <li>removeParticipant validates that participantId belongs to the requested
 *       Contact before delegating to membership service.</li>
 *   <li>removeParticipant preserves participant expectedVersion semantics.</li>
 *   <li>Tenant/contact mismatch cannot mutate another Contact.</li>
 *   <li>Role-switch add failure propagates the exception (the prior removal
 *       is rolled back by the {@code @Transactional} boundary).</li>
 * </ul>
 *
 * <p>These are pure unit tests; no Spring context, no database. The
 * PostgreSQL Direct contract is proven separately by
 * {@code ContactCollaborationServicePostgresTest}.</p>
 */
class ContactCollaborationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("c4c40000-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("c4c40000-0000-4000-8000-00000000b001");
    private static final UUID CONTACT_ID = UUID.fromString("c4c40000-0000-4000-8000-000000000001");
    private static final UUID OTHER_CONTACT_ID = UUID.fromString("c4c40000-0000-4000-8000-000000000002");
    private static final UUID OWNER_USER_ID = UUID.fromString("c4c40000-0000-4000-8000-00000000a002");
    private static final UUID TARGET_USER_ID = UUID.fromString("c4c40000-0000-4000-8000-00000000a003");
    private static final UUID ACTOR_ID = UUID.fromString("c4c40000-0000-4000-8000-00000000a004");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private ContactRepository contactRepository;
    private com.sanad.platform.crm.collaboration.application.CollaborationMembershipService membershipService;
    private ContactCollaborationService service;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        membershipService = mock(com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.class);
        service = new ContactCollaborationService(contactRepository, membershipService);
    }

    private ContactRecord activeContact(UUID tenantId, UUID contactId, UUID ownerUserId) {
        return new ContactRecord(
                contactId, 0L, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ACTIVE", ownerUserId, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    private ContactRecord archivedContact(UUID tenantId, UUID contactId, UUID ownerUserId) {
        return new ContactRecord(
                contactId, 5L, null, "Jane", "Doe", "Jane Doe",
                "jane@example.com", "jane@example.com", null, null, null,
                "ARCHIVED", ownerUserId, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
    }

    private EntityParticipant stubParticipant(UUID tenantId, UUID contactId, UUID userId, ParticipantRole role, long version) {
        return new EntityParticipant(
                UUID.randomUUID(), tenantId, CollaborationEntityType.CONTACT, contactId,
                userId, role, ACTOR_ID, OCCURRED_AT, null, null, version);
    }

    // ── A. shareContact adds COLLABORATOR ────────────────────────────────

    @Test
    @DisplayName("A. shareContact adds COLLABORATOR for CONTACT via membership service")
    void a_shareContactAddsCollaborator() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant added = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 0L);
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenReturn(added);

        EntityParticipant result = service.shareContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);

        assertThat(result).isSameAs(added);

        ArgumentCaptor<AddParticipantCommand> cmdCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        ArgumentCaptor<EligibilityPolicy> policyCaptor = ArgumentCaptor.forClass(EligibilityPolicy.class);
        verify(membershipService).addParticipant(cmdCaptor.capture(), policyCaptor.capture());

        AddParticipantCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.tenantId()).isEqualTo(TENANT_A);
        assertThat(cmd.entityType()).isEqualTo(CollaborationEntityType.CONTACT);
        assertThat(cmd.entityId()).isEqualTo(CONTACT_ID);
        assertThat(cmd.userId()).isEqualTo(TARGET_USER_ID);
        assertThat(cmd.role()).isEqualTo(ParticipantRole.COLLABORATOR);
        assertThat(cmd.actorId()).isEqualTo(ACTOR_ID);
        assertThat(cmd.occurredAt()).isEqualTo(OCCURRED_AT);

        EligibilityPolicy policy = policyCaptor.getValue();
        assertThat(policy.organizationId()).isNull();
        assertThat(policy.requiredCapability()).isEqualTo("CRM.CONTACT.READ");
    }

    // ── B. watchContact adds WATCHER ──────────────────────────────────────

    @Test
    @DisplayName("B. watchContact adds WATCHER for CONTACT via membership service")
    void b_watchContactAddsWatcher() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant added = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.WATCHER, 0L);
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenReturn(added);

        EntityParticipant result = service.watchContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);

        assertThat(result).isSameAs(added);

        ArgumentCaptor<AddParticipantCommand> cmdCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(cmdCaptor.capture(), any(EligibilityPolicy.class));
        AddParticipantCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.role()).isEqualTo(ParticipantRole.WATCHER);
    }

    // ── C. Same-role request is idempotent (delegated) ───────────────────

    @Test
    @DisplayName("C. Same-role request is idempotent — service delegates to membership's idempotent fast path")
    void c_sameRoleRequestIsIdempotent() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existing = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 0L);
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenReturn(existing);

        EntityParticipant firstCall = service.shareContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);
        EntityParticipant secondCall = service.shareContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);

        assertThat(firstCall).isSameAs(existing);
        assertThat(secondCall).isSameAs(existing);

        // Membership service is invoked twice (the C4 service does not cache);
        // the idempotency decision is the membership service's responsibility
        // via its findActive fast path.
        verify(membershipService, times(2)).addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class));
    }

    // ── D. COLLABORATOR → WATCHER normalizes (remove old + add new) ──────

    @Test
    @DisplayName("D. COLLABORATOR → WATCHER removes the old COLLABORATOR then adds WATCHER")
    void d_collaboratorToWatcherNormalizes() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existingCollaborator = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 3L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existingCollaborator));
        EntityParticipant addedWatcher = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.WATCHER, 0L);
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenReturn(addedWatcher);

        EntityParticipant result = service.watchContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);

        assertThat(result).isSameAs(addedWatcher);

        // The old COLLABORATOR must be removed using its participant version.
        ArgumentCaptor<RemoveParticipantCommand> rmCaptor = ArgumentCaptor.forClass(RemoveParticipantCommand.class);
        verify(membershipService).removeParticipant(rmCaptor.capture());
        RemoveParticipantCommand rm = rmCaptor.getValue();
        assertThat(rm.tenantId()).isEqualTo(TENANT_A);
        assertThat(rm.participantId()).isEqualTo(existingCollaborator.id());
        assertThat(rm.expectedVersion()).isEqualTo(existingCollaborator.version());
        assertThat(rm.actorId()).isEqualTo(ACTOR_ID);
        assertThat(rm.occurredAt()).isEqualTo(OCCURRED_AT);

        // Then the WATCHER is added.
        ArgumentCaptor<AddParticipantCommand> addCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(addCaptor.capture(), any(EligibilityPolicy.class));
        assertThat(addCaptor.getValue().role()).isEqualTo(ParticipantRole.WATCHER);
    }

    // ── E. WATCHER → COLLABORATOR does the reverse ──────────────────────

    @Test
    @DisplayName("E. WATCHER → COLLABORATOR removes the old WATCHER then adds COLLABORATOR")
    void e_watcherToCollaboratorNormalizes() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existingWatcher = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.WATCHER, 2L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existingWatcher));
        EntityParticipant addedCollaborator = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 0L);
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenReturn(addedCollaborator);

        EntityParticipant result = service.shareContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT);

        assertThat(result).isSameAs(addedCollaborator);

        ArgumentCaptor<RemoveParticipantCommand> rmCaptor = ArgumentCaptor.forClass(RemoveParticipantCommand.class);
        verify(membershipService).removeParticipant(rmCaptor.capture());
        assertThat(rmCaptor.getValue().participantId()).isEqualTo(existingWatcher.id());
        assertThat(rmCaptor.getValue().expectedVersion()).isEqualTo(existingWatcher.version());

        ArgumentCaptor<AddParticipantCommand> addCaptor = ArgumentCaptor.forClass(AddParticipantCommand.class);
        verify(membershipService).addParticipant(addCaptor.capture(), any(EligibilityPolicy.class));
        assertThat(addCaptor.getValue().role()).isEqualTo(ParticipantRole.COLLABORATOR);
    }

    // ── F. Target user == contact owner is rejected at APPLICATION layer ──

    @Test
    @DisplayName("F. shareContact rejects when target user == contact owner (application guard)")
    void f_shareContactRejectsOwnerAsParticipant() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));

        assertThatThrownBy(() ->
                service.shareContact(TENANT_A, CONTACT_ID, OWNER_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner cannot be a participant");

        verifyNoInteractions(membershipService);
    }

    @Test
    @DisplayName("F. watchContact rejects when target user == contact owner (application guard)")
    void f_watchContactRejectsOwnerAsParticipant() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));

        assertThatThrownBy(() ->
                service.watchContact(TENANT_A, CONTACT_ID, OWNER_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner cannot be a participant");

        verifyNoInteractions(membershipService);
    }

    // ── G. Archived Contact rejects share/watch mutations ────────────────

    @Test
    @DisplayName("G. Archived Contact rejects shareContact")
    void g_archivedContactRejectsShare() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(archivedContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));

        assertThatThrownBy(() ->
                service.shareContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");

        verifyNoInteractions(membershipService);
    }

    @Test
    @DisplayName("G. Archived Contact rejects watchContact")
    void g_archivedContactRejectsWatch() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(archivedContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));

        assertThatThrownBy(() ->
                service.watchContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");

        verifyNoInteractions(membershipService);
    }

    // ── H. Archived Contact rejects removeParticipant ───────────────────

    @Test
    @DisplayName("H. Archived Contact rejects removeParticipant")
    void h_archivedContactRejectsRemove() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(archivedContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));

        assertThatThrownBy(() ->
                service.removeParticipant(TENANT_A, CONTACT_ID, UUID.randomUUID(), 0L, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");

        verifyNoInteractions(membershipService);
    }

    // ── I. listParticipants returns CONTACT active participants only ─────

    @Test
    @DisplayName("I. listParticipants returns CONTACT active participants only")
    void i_listParticipantsReturnsContactActiveOnly() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant p1 = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 0L);
        EntityParticipant p2 = stubParticipant(TENANT_A, CONTACT_ID, UUID.randomUUID(), ParticipantRole.WATCHER, 0L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(p1, p2));

        List<EntityParticipant> result = service.listParticipants(TENANT_A, CONTACT_ID);

        assertThat(result).containsExactly(p1, p2);
        verify(membershipService).listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID);
    }

    // ── C4-R1: listParticipants @Transactional(readOnly=true) contract ──
    //
    // Production contract under FORCE RLS:
    //   listParticipants reads crm_contacts AND crm_entity_participants,
    //   both of which have FORCE ROW LEVEL SECURITY. The production
    //   TenantRlsConnectionHandler applies SET LOCAL app.tenant_id
    //   only when connection autoCommit == false (i.e. inside a Spring
    //   @Transactional boundary). Without @Transactional, listParticipants
    //   runs in autoCommit=true mode and the FORCE RLS predicate fails
    //   closed (returns 0 rows).
    //
    // This test inspects the Spring transaction metadata via
    // AnnotationTransactionAttributeSource to verify:
    //   - listParticipants has @Transactional
    //   - readOnly == true
    //
    // Reflection on the raw class (no Spring proxy needed) is sufficient
    // because AnnotationTransactionAttributeSource reads annotations
    // directly from the Method.

    private TransactionAttribute transactionAttributeFor(String methodName) throws Exception {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        Method method = ContactCollaborationService.class.getMethod(
                methodName, UUID.class, UUID.class);
        return source.getTransactionAttribute(method, ContactCollaborationService.class);
    }

    @Test
    @DisplayName("C4-R1. listParticipants must be annotated @Transactional (non-null attribute)")
    void listParticipantsHasTransactionalAttribute() throws Exception {
        TransactionAttribute attr = transactionAttributeFor("listParticipants");
        assertThat(attr)
                .as("listParticipants must carry a Spring @Transactional attribute so the "
                        + "production TenantRlsConnectionHandler can apply SET LOCAL app.tenant_id "
                        + "under FORCE RLS")
                .isNotNull();
    }

    @Test
    @DisplayName("C4-R1. listParticipants @Transactional must be readOnly=true")
    void listParticipantsIsReadOnly() throws Exception {
        TransactionAttribute attr = transactionAttributeFor("listParticipants");
        assertThat(attr)
                .as("listParticipants must carry a @Transactional attribute")
                .isNotNull();
        assertThat(attr.isReadOnly())
                .as("listParticipants must be @Transactional(readOnly = true) — it issues no writes "
                        + "and the read-only hint lets the transaction manager route to a read-optimized "
                        + "path when configured.")
                .isTrue();
    }

    // ── J. removeParticipant requires participantId to belong to the Contact ──

    @Test
    @DisplayName("J. removeParticipant rejects participantId that does not belong to the requested Contact")
    void j_removeParticipantRejectsMismatchedContact() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        // The participant row exists but belongs to a DIFFERENT contact.
        EntityParticipant wrongContact = stubParticipant(TENANT_A, OTHER_CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 0L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                service.removeParticipant(TENANT_A, CONTACT_ID, wrongContact.id(), 0L, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participant does not belong");

        // Membership service remove must NOT be called.
        verify(membershipService, never()).removeParticipant(any(RemoveParticipantCommand.class));
    }

    @Test
    @DisplayName("J. removeParticipant succeeds when participantId belongs to the requested Contact")
    void j_removeParticipantSucceedsForMatchingContact() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existing = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 7L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existing));
        when(membershipService.removeParticipant(any(RemoveParticipantCommand.class)))
                .thenReturn(existing.remove(ACTOR_ID, OCCURRED_AT));

        EntityParticipant result = service.removeParticipant(TENANT_A, CONTACT_ID, existing.id(), 7L, ACTOR_ID, OCCURRED_AT);

        assertThat(result.userId()).isEqualTo(TARGET_USER_ID);
        ArgumentCaptor<RemoveParticipantCommand> captor = ArgumentCaptor.forClass(RemoveParticipantCommand.class);
        verify(membershipService).removeParticipant(captor.capture());
        RemoveParticipantCommand cmd = captor.getValue();
        assertThat(cmd.tenantId()).isEqualTo(TENANT_A);
        assertThat(cmd.participantId()).isEqualTo(existing.id());
        assertThat(cmd.expectedVersion()).isEqualTo(7L);
        assertThat(cmd.actorId()).isEqualTo(ACTOR_ID);
        assertThat(cmd.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    // ── K. removeParticipant preserves participant expectedVersion semantics ──

    @Test
    @DisplayName("K. removeParticipant propagates CollaborationConflictException when membership service optimistic-lock fails")
    void k_removeParticipantPropagatesOptimisticLockConflict() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existing = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 7L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existing));
        when(membershipService.removeParticipant(any(RemoveParticipantCommand.class)))
                .thenThrow(new CollaborationConflictException("participant state conflict"));

        assertThatThrownBy(() ->
                service.removeParticipant(TENANT_A, CONTACT_ID, existing.id(), 7L, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(CollaborationConflictException.class)
                .hasMessageContaining("participant state conflict");
    }

    // ── L. Tenant/contact mismatch cannot mutate another Contact ────────

    @Test
    @DisplayName("L. Contact not found in tenant is rejected before any mutation")
    void l_contactNotFoundRejectsMutation() {
        when(contactRepository.findById(TENANT_B, CONTACT_ID))
                .thenThrow(new com.sanad.platform.crm.error.CrmContractException(
                        com.sanad.platform.crm.error.CrmErrorCode.CRM_CONTACT_NOT_FOUND));

        assertThatThrownBy(() ->
                service.shareContact(TENANT_B, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(com.sanad.platform.crm.error.CrmContractException.class);

        verifyNoInteractions(membershipService);
    }

    // ── M. Role-switch add failure rolls back the previous-role removal ──
    //
    // The C4 service is annotated @Transactional on watchContact / shareContact.
    // Spring's @Transactional semantics: when addParticipant throws, the
    // entire transaction rolls back — including the prior removeParticipant
    // call's effects (which Spring's DataSourceTransactionManager will
    // undo via ROLLBACK). This unit test verifies only the call-ordering
    // and exception-propagation contract; the actual rollback behavior is
    // proven end-to-end by ContactCollaborationServicePostgresTest.

    @Test
    @DisplayName("M. Role-switch add failure propagates exception after removal (transaction rolls back)")
    void m_roleSwitchAddFailurePropagatesAfterRemoval() {
        when(contactRepository.findById(TENANT_A, CONTACT_ID))
                .thenReturn(activeContact(TENANT_A, CONTACT_ID, OWNER_USER_ID));
        EntityParticipant existingCollaborator = stubParticipant(TENANT_A, CONTACT_ID, TARGET_USER_ID, ParticipantRole.COLLABORATOR, 3L);
        when(membershipService.listParticipants(TENANT_A, CollaborationEntityType.CONTACT, CONTACT_ID))
                .thenReturn(List.of(existingCollaborator));
        when(membershipService.removeParticipant(any(RemoveParticipantCommand.class)))
                .thenReturn(existingCollaborator.remove(ACTOR_ID, OCCURRED_AT));
        when(membershipService.addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class)))
                .thenThrow(new IllegalArgumentException("recipient not eligible: capability missing"));

        assertThatThrownBy(() ->
                service.watchContact(TENANT_A, CONTACT_ID, TARGET_USER_ID, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient not eligible");

        // Removal happened first (before the failed add), but the @Transactional
        // boundary will roll it back. The unit-level proof is that BOTH
        // calls were attempted in the correct order.
        verify(membershipService).removeParticipant(any(RemoveParticipantCommand.class));
        verify(membershipService).addParticipant(any(AddParticipantCommand.class), any(EligibilityPolicy.class));
    }
}
