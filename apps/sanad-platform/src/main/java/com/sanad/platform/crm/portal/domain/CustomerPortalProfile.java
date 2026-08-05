package com.sanad.platform.crm.portal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain value object representing a customer's portal profile.
 */
public record CustomerPortalProfile(
        UUID customerId,
        UUID tenantId,
        String displayName,
        String email,
        String company,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerPortalProfile of(UUID customerId, UUID tenantId, String displayName,
                                           String email, String company, String phone) {
        Instant now = Instant.now();
        return new CustomerPortalProfile(customerId, tenantId, displayName, email, company, phone, now, now);
    }
}
