package com.sanad.platform.crm.party.domain;

import java.util.UUID;

/** Records lifecycle changes caused indirectly by primary/preferred reassignment. */
public interface AddressCommunicationHistoryPort {
    void addressChanged(
            UUID tenantId,
            UUID actorId,
            AddressCommunicationRepository.AddressRecord record,
            String eventType,
            Long previousVersion);

    void communicationChanged(
            UUID tenantId,
            UUID actorId,
            AddressCommunicationRepository.CommunicationMethodRecord record,
            String eventType,
            Long previousVersion);
}
