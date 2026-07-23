package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Validates users referenced by ownership and sales-team use cases.
 * Implementations must enforce both tenant identity and ACTIVE user status.
 */
public interface OwnershipUserValidationPort {

    boolean isActiveUser(UUID tenantId, UUID userId);
}
