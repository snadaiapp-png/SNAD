package com.sanad.platform.crm.party.domain;

import java.util.UUID;

/**
 * Port for validating account/contact owners.
 * Implemented by infrastructure adapter to check user existence and tenant membership.
 */
public interface OwnerValidationPort {
    /** Check if the given user exists and belongs to the given tenant. */
    boolean isValidOwner(UUID tenantId, UUID ownerUserId);
}
