package com.sanad.platform.commerce.domain;

import java.util.UUID;

/**
 * Commerce customer port (v20260816.5).
 *
 * <p>Decouples the commerce checkout flow from the concrete customer master
 * (CRM accounts / contacts). The default {@code CommerceCustomerAdapter}
 * returns the supplied email as the reference — suitable for guest checkout
 * in demo / test deployments. A production deployment would provide a real
 * implementation that resolves or creates a CRM account / contact and
 * returns its UUID.
 */
public interface CommerceCustomerPort {

    /**
     * Resolve or create a (guest) customer from an email + name.
     *
     * @param tenantId the tenant owning the store / order
     * @param email    the customer email (required, never null)
     * @param name     the customer display name (may be null)
     * @return a stable customer reference (e.g. CRM account UUID or email)
     */
    String resolveOrCreateGuest(UUID tenantId, String email, String name);

    /**
     * Resolve a customer by their existing CRM contact / account ID.
     *
     * @param tenantId the tenant owning the contact
     * @param contactId the CRM contact / account UUID
     * @return a stable customer reference, or {@code null} if not found
     */
    String resolveByContact(UUID tenantId, UUID contactId);
}
