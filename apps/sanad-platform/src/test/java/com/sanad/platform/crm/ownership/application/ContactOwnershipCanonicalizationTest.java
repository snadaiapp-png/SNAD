package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentStatus;
import com.sanad.platform.crm.ownership.domain.AssignmentDecision;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.DistributionMethod;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipRecordPort;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import com.sanad.platform.crm.party.application.ContactTransferUseCases;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * C6-C behavioral negative control + centralized owner-type policy matrix.
 *
 * <p>This test class is intentionally a PURE-MOCK unit test. It is NOT a
 * runtime certification. The authoritative runtime proof lives in
 * {@code ContactOwnershipCanonicalizationSpringPostgresTest}.</p>
 *
 * <p>Historical truth: the original C6-C RED was STRUCTURAL_COMPILATION_ONLY.
 * {@code ORIGINAL_C6C_RED_TYPE=STRUCTURAL_COMPILATION_ONLY} and
 * {@code ORIGINAL_SEMANTIC_RED_PROVEN=NO} remain permanently reported.</p>
 *
 * <h3>C6-C requirements covered here</h3>
 * <ul>
 *   <li>SECTION 3 — behavioral negative control: CONTACT+USER reassign
 *       never invokes {@code OwnershipRecordPort.updateOwner} and routes
 *       to {@code ContactTransferUseCases.transferContact} exactly once,
 *       passing the expected version sourced from
 *       {@code ContactRepository.findById(...).version()}.</li>
 *   <li>SECTION 4 — unit policy matrix covering all four public
 *       entrypoints ({@code reassign}, {@code bulkReassign},
 *       {@code transfer}, {@code assignByDecision}) for the CONTACT
 *       record-type policy:
 *       <ul>
 *         <li>USER = allowed</li>
 *         <li>TEAM = rejected (pre-ledger, no SalesTeamRepository lookup,
 *             no QueueRepository lookup, no
 *             {@code AssignmentRepository.supersedeAndInsertExpected},
 *             no {@code ContactTransferUseCases} invocation,
 *             no {@code OwnershipRecordPort.updateOwner})</li>
 *         <li>QUEUE = rejected (same pre-ledger guards)</li>
 *       </ul>
 *   </li>
 * </ul>
 */
class ContactOwnershipCanonicalizationTest {

    private OwnershipRecordPort records;
    private AssignmentRepository assignments;
    private OwnershipUserValidationPort users;
    private SalesTeamRepository teams;
    private QueueRepository queues;
    private ContactRepository contactRepository;
    private ContactTransferUseCases contactTransferUseCases;
    private OwnershipCommandUseCases commands;

    @BeforeEach
    void setUp() {
        assignments = mock(AssignmentRepository.class);
        records = mock(OwnershipRecordPort.class);
        users = mock(OwnershipUserValidationPort.class);
        teams = mock(SalesTeamRepository.class);
        queues = mock(QueueRepository.class);
        AuditPort audit = mock(AuditPort.class);
        TimelineEventPort timeline = mock(TimelineEventPort.class);
        contactRepository = mock(ContactRepository.class);
        contactTransferUseCases = mock(ContactTransferUseCases.class);

        commands = new OwnershipCommandUseCases(
                assignments, records, users, teams, queues,
                audit, timeline, new ObjectMapper(),
                contactRepository, contactTransferUseCases);
    }

    private Assignment activeAssignment(UUID id, UUID tenantId, UUID recordId,
                                        OwnerType ownerType, UUID ownerId) {
        return new Assignment(
                id, tenantId, 0L,
                "CONTACT", recordId, ownerId, "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, "initial",
                ownerType,
                ownerType == OwnerType.USER ? ownerId : null,
                ownerType == OwnerType.TEAM ? ownerId : null,
                ownerType == OwnerType.QUEUE ? ownerId : null,
                AssignmentRecordType.CONTACT, recordId, null,
                ownerId, null, null, Instant.now(), null,
                Instant.now(), Instant.now(), ownerId, ownerId);
    }

    private ContactRecord contactRecord(UUID contactId, UUID ownerUserId, long version) {
        return new ContactRecord(
                contactId, version, null, "Before", "Doe", "Before Doe",
                null, null, null, null, null, "ACTIVE", ownerUserId, "UNKNOWN",
                Instant.now(), Instant.now());
    }

    // ── SECTION 3: behavioral negative control ───────────────────────────

    @Test
    @DisplayName("CONTACT + USER reassign must NOT invoke OwnershipRecordPort.updateOwner; routes to ContactTransferUseCases with expected version from findById")
    void contactReassignMustNotUseLegacyProjection() {
        UUID tenantId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        long contactVersionBefore = 7L;

        when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                .thenReturn(true);
        when(users.isActiveUser(eq(tenantId), eq(userB))).thenReturn(true);

        Assignment current = activeAssignment(assignmentId, tenantId, contactId,
                OwnerType.USER, userA);
        when(assignments.findActive(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                .thenReturn(Optional.of(current));

        Assignment created = activeAssignment(UUID.randomUUID(), tenantId, contactId,
                OwnerType.USER, userB);
        when(assignments.supersedeAndInsertExpected(
                eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId),
                any(), eq(actorId), any(), any(), any(), any(),
                eq(assignmentId), eq(OwnerType.USER), eq(userA)))
                .thenReturn(created);

        // The expected version passed to ContactTransferUseCases MUST come from
        // ContactRepository.findById(...).version(). This is the C6-C contract.
        when(contactRepository.findById(eq(tenantId), eq(contactId)))
                .thenReturn(contactRecord(contactId, userA, contactVersionBefore));

        when(contactTransferUseCases.transferContact(
                eq(tenantId), eq(contactId), eq(userB),
                eq(contactVersionBefore), eq(actorId), any()))
                .thenReturn(contactRecord(contactId, userB, contactVersionBefore + 1));

        var command = new OwnershipCommandUseCases.ReassignCommand(
                tenantId, AssignmentRecordType.CONTACT, contactId,
                OwnerType.USER, userB, actorId, "test",
                UUID.randomUUID(), UUID.randomUUID(),
                assignmentId, null);

        commands.reassign(command);

        verify(records, never()).updateOwner(
                eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId),
                any(), any());

        verify(contactTransferUseCases, times(1)).transferContact(
                eq(tenantId), eq(contactId), eq(userB),
                eq(contactVersionBefore), eq(actorId), any());
    }

    // ── SECTION 4: policy matrix (reassign / bulkReassign / transfer / assignByDecision) ──

    @Nested
    @DisplayName("4.1 reassign CONTACT owner-type policy")
    class ReassignPolicy {

        @Test
        @DisplayName("CONTACT + TEAM rejected before ledger mutation; no team/queue/assignment/transfer/record-port calls")
        void contactTeamReassignRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.ReassignCommand(
                    tenantId, AssignmentRecordType.CONTACT, contactId,
                    OwnerType.TEAM, teamId, actorId, "test",
                    UUID.randomUUID(), UUID.randomUUID(), null, null);

            assertThatThrownBy(() -> commands.reassign(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }

        @Test
        @DisplayName("CONTACT + QUEUE rejected before ledger mutation; no team/queue/assignment/transfer/record-port calls")
        void contactQueueReassignRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.ReassignCommand(
                    tenantId, AssignmentRecordType.CONTACT, contactId,
                    OwnerType.QUEUE, queueId, actorId, "test",
                    UUID.randomUUID(), UUID.randomUUID(), null, null);

            assertThatThrownBy(() -> commands.reassign(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }
    }

    @Nested
    @DisplayName("4.2 bulkReassign CONTACT owner-type policy")
    class BulkReassignPolicy {

        @Test
        @DisplayName("CONTACT + TEAM bulk rejected pre-ledger; zero downstream mutation")
        void contactTeamBulkReassignRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.BulkReassignCommand(
                    tenantId, AssignmentRecordType.CONTACT, List.of(contactId),
                    OwnerType.TEAM, teamId, actorId, "bulk-team",
                    UUID.randomUUID());

            assertThatThrownBy(() -> commands.bulkReassign(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }

        @Test
        @DisplayName("CONTACT + QUEUE bulk rejected pre-ledger; zero downstream mutation")
        void contactQueueBulkReassignRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.BulkReassignCommand(
                    tenantId, AssignmentRecordType.CONTACT, List.of(contactId),
                    OwnerType.QUEUE, queueId, actorId, "bulk-queue",
                    UUID.randomUUID());

            assertThatThrownBy(() -> commands.bulkReassign(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }
    }

    @Nested
    @DisplayName("4.3 transfer CONTACT owner-type policy")
    class TransferPolicy {

        @Test
        @DisplayName("CONTACT + TEAM transfer rejected pre-ledger; zero downstream mutation")
        void contactTeamTransferRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.TransferAssignmentCommand(
                    tenantId, AssignmentRecordType.CONTACT, List.of(contactId),
                    OwnerType.TEAM, teamId, actorId, UUID.randomUUID(),
                    UUID.randomUUID(), "transfer-team", null);

            assertThatThrownBy(() -> commands.transfer(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }

        @Test
        @DisplayName("CONTACT + QUEUE transfer rejected pre-ledger; zero downstream mutation")
        void contactQueueTransferRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            var command = new OwnershipCommandUseCases.TransferAssignmentCommand(
                    tenantId, AssignmentRecordType.CONTACT, List.of(contactId),
                    OwnerType.QUEUE, queueId, actorId, UUID.randomUUID(),
                    UUID.randomUUID(), "transfer-queue", null);

            assertThatThrownBy(() -> commands.transfer(command))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }
    }

    @Nested
    @DisplayName("4.4 assignByDecision CONTACT owner-type policy")
    class AssignByDecisionPolicy {

        @Test
        @DisplayName("CONTACT + TEAM assignByDecision rejected pre-ledger; zero downstream mutation")
        void contactTeamAssignByDecisionRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            AssignmentDecision decision = new AssignmentDecision(
                    true, null, 1, DistributionMethod.DIRECT_OWNER,
                    OwnerType.TEAM, teamId, false, List.of("test"));

            assertThatThrownBy(() -> commands.assignByDecision(
                    tenantId, actorId, AssignmentRecordType.CONTACT, contactId,
                    decision, UUID.randomUUID(), UUID.randomUUID(), "RULE_MATCH"))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }

        @Test
        @DisplayName("CONTACT + QUEUE assignByDecision rejected pre-ledger; zero downstream mutation")
        void contactQueueAssignByDecisionRejected() {
            UUID tenantId = UUID.randomUUID();
            UUID contactId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();

            when(records.exists(eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId)))
                    .thenReturn(true);

            AssignmentDecision decision = new AssignmentDecision(
                    true, null, 1, DistributionMethod.DIRECT_OWNER,
                    OwnerType.QUEUE, queueId, false, List.of("test"));

            assertThatThrownBy(() -> commands.assignByDecision(
                    tenantId, actorId, AssignmentRecordType.CONTACT, contactId,
                    decision, UUID.randomUUID(), UUID.randomUUID(), "RULE_MATCH"))
                    .isInstanceOf(OwnershipDomainException.class)
                    .hasMessageContaining("CONTACT ownership supports USER owners only");

            verifyNoLedgerMutation();
            verifyNoTransfer();
            verifyNoRecordPortUpdate();
            verify(teams, never()).findById(any(), any());
            verify(queues, never()).findById(any(), any());
        }
    }

    // ── Verification helpers ──────────────────────────────────────────────

    private void verifyNoLedgerMutation() {
        verify(assignments, never()).supersedeAndInsertExpected(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(assignments, never()).supersedeAndInsert(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    private void verifyNoTransfer() {
        verify(contactTransferUseCases, never()).transferContact(
                any(), any(), any(), anyLong(), any(), any());
        verify(contactTransferUseCases, never()).transferContact(
                any(ContactTransferUseCases.TransferContactCommand.class));
    }

    private void verifyNoRecordPortUpdate() {
        verify(records, never()).updateOwner(
                any(), any(), any(), any(), any());
    }
}
