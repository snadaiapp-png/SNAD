package com.sanad.platform.crm.party.domain;

import java.util.UUID;

/** Maintains the CRM-005 account-address read model during the compatibility window. */
public interface LegacyAddressProjectionPort {
    void upsert(UUID tenantId, UUID actorId, AddressCommunicationRepository.AddressRecord address);
}
