package com.sanad.platform.crm.portal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for customer portal data persistence and retrieval.
 */
public interface PortalRepository {

    /**
     * Get customer profile by ID.
     */
    Optional<CustomerPortalProfile> getProfile(UUID tenantId, UUID customerId);

    /**
     * Update customer profile.
     */
    CustomerPortalProfile updateProfile(UUID tenantId, UUID customerId, CustomerPortalProfile profile);

    /**
     * Get tickets for a customer.
     */
    List<CustomerPortalTicket> getTickets(UUID tenantId, UUID customerId);

    /**
     * Create a new support ticket.
     */
    CustomerPortalTicket createTicket(UUID tenantId, UUID customerId, CustomerPortalTicket ticket);

    /**
     * Get ticket by ID.
     */
    Optional<CustomerPortalTicket> getTicket(UUID tenantId, UUID customerId, UUID ticketId);

    /**
     * Get customer's active opportunities.
     */
    List<java.util.Map<String, Object>> getCustomerOpportunities(UUID tenantId, UUID customerId);
}
