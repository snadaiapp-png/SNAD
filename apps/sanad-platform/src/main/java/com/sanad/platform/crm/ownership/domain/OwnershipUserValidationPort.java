package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Validates users referenced by ownership and sales-team use cases.
 * Implementations must enforce both tenant identity and ACTIVE user status.
 */
public interface OwnershipUserValidationPort {

    boolean isActiveUser(UUID tenantId, UUID userId);

    /**
     * Locks the ACTIVE tenant user for the current transaction.
     * Returns false when the user is absent, inactive, or belongs to another tenant.
     */
    boolean lockActiveUser(UUID tenantId, UUID userId);
}
