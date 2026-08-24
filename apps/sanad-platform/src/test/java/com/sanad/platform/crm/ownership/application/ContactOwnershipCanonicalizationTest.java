package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentStatus;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipRecordPort;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.application.ContactTransferUseCases;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * C6-C RED — CONTACT + USER generic reassign must NOT invoke
 * OwnershipRecordPort.updateOwner for Contact projection.
 * Must route to ContactTransferUseCases instead.
 */
class ContactOwnershipCanonicalizationTest {

    private OwnershipRecordPort records;
    private AssignmentRepository assignments;
    private OwnershipUserValidationPort users;
    private ContactRepository contactRepository;
    private ContactTransferUseCases contactTransferUseCases;
    private OwnershipCommandUseCases commands;

    @BeforeEach
    void setUp() {
        assignments = mock(AssignmentRepository.class);
        records = mock(OwnershipRecordPort.class);
        users = mock(OwnershipUserValidationPort.class);
        var teams = mock(SalesTeamRepository.class);
        var queues = mock(QueueRepository.class);
        var audit = mock(AuditPort.class);
        var timeline = mock(TimelineEventPort.class);
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

    @Test
    @DisplayName("CONTACT + USER reassign must NOT invoke OwnershipRecordPort.updateOwner")
    void contactReassignMustNotUseLegacyProjection() {
        UUID tenantId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

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

        when(contactRepository.findById(eq(tenantId), eq(contactId)))
                .thenReturn(new ContactRecord(
                        contactId, 0L, null, "Jane", "Doe", "Jane Doe",
                        null, null, null, null, null, "ACTIVE", userA, "UNKNOWN",
                        Instant.now(), Instant.now()));

        when(contactTransferUseCases.transferContact(
                eq(tenantId), eq(contactId), eq(userB), eq(0L), eq(actorId), any()))
                .thenReturn(new ContactRecord(
                        contactId, 1L, null, "Jane", "Doe", "Jane Doe",
                        null, null, null, null, null, "ACTIVE", userB, "UNKNOWN",
                        Instant.now(), Instant.now()));

        var command = new OwnershipCommandUseCases.ReassignCommand(
                tenantId, AssignmentRecordType.CONTACT, contactId,
                OwnerType.USER, userB, actorId, "test",
                UUID.randomUUID(), UUID.randomUUID(),
                assignmentId, null);

        commands.reassign(command);

        verify(records, never()).updateOwner(
                eq(tenantId), eq(AssignmentRecordType.CONTACT), eq(contactId),
                any(), any());

        verify(contactTransferUseCases).transferContact(
                eq(tenantId), eq(contactId), eq(userB), eq(0L), eq(actorId), any());
    }

    @Test
    @DisplayName("CONTACT + TEAM reassign must be rejected before ledger mutation")
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

        verify(assignments, never()).supersedeAndInsertExpected(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(contactTransferUseCases, never()).transferContact(
                any(), any(), any(), anyLong(), any(), any());
    }
}
