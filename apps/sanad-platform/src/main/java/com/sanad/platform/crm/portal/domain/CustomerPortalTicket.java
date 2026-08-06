package com.sanad.platform.crm.portal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain value object representing a customer support ticket.
 */
public record CustomerPortalTicket(
        UUID ticketId,
        UUID customerId,
        UUID tenantId,
        String subject,
        String description,
        String status,
        String priority,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerPortalTicket of(UUID customerId, UUID tenantId, String subject, String description) {
        Instant now = Instant.now();
        return new CustomerPortalTicket(UUID.randomUUID(), customerId, tenantId, subject, description, "OPEN", "MEDIUM", now, now);
    }
}
