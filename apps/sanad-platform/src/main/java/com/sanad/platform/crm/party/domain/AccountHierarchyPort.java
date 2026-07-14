package com.sanad.platform.crm.party.domain;

import java.util.UUID;

/**
 * Port for account hierarchy validation.
 * Implemented by infrastructure adapter to check parent existence and cycles.
 */
public interface AccountHierarchyPort {
    /** Check if parent account exists in the same tenant. */
    boolean parentExists(UUID tenantId, UUID parentAccountId);

    /** Check if setting parentAccountId would create a cycle. */
    boolean wouldCreateCycle(UUID tenantId, UUID accountId, UUID parentAccountId);

    /** Check if account has active child accounts. */
    boolean hasActiveChildren(UUID tenantId, UUID accountId);
}
