package com.sanad.platform.crm.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.application.ContactRelationshipUseCases;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContactRelationshipUseCasesTest {

    private final ContactRelationshipRepository repository = mock(ContactRelationshipRepository.class);
    private final OwnerValidationPort owners = mock(OwnerValidationPort.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final ContactRelationshipUseCases useCases = new ContactRelationshipUseCases(
            repository, owners, audit, timeline, new ObjectMapper());

    @Test
    void settingAnAlreadyPrimaryRelationshipIsAnIdempotentNoOp() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        RelationshipRecord primary = primaryRelationship(relationshipId, 7L);
        when(repository.findRelationship(tenantId, relationshipId)).thenReturn(primary);

        RelationshipRecord result = useCases.setPrimary(tenantId, actorId, relationshipId, 7L);

        assertThat(result).isSameAs(primary);
        verify(repository, never()).setPrimary(any(), any(), any(), anyLong());
        verifyNoInteractions(audit, timeline);
    }

    @Test
    void idempotentPrimaryCommandStillEnforcesOptimisticConcurrency() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(repository.findRelationship(tenantId, relationshipId))
                .thenReturn(primaryRelationship(relationshipId, 7L));

        assertThatThrownBy(() -> useCases.setPrimary(tenantId, actorId, relationshipId, 6L))
                .isInstanceOfSatisfying(CrmContractException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        verify(repository, never()).setPrimary(any(), any(), any(), anyLong());
        verifyNoInteractions(audit, timeline);
    }

    private static RelationshipRecord primaryRelationship(UUID id, long version) {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        return new RelationshipRecord(
                id,
                version,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Person",
                "Account",
                "DECISION_MAKER",
                null,
                null,
                null,
                "ACTIVE",
                true,
                null,
                null,
                null,
                null,
                "DECIDER",
                null,
                now,
                now);
    }
}
