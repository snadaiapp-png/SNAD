package com.sanad.platform.crm.portal.application;

import com.sanad.platform.crm.portal.domain.CustomerPortalProfile;
import com.sanad.platform.crm.portal.domain.CustomerPortalTicket;
import com.sanad.platform.crm.portal.domain.PortalRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service orchestrating customer portal operations.
 * Follows the thin facade pattern with no Spring annotations.
 */
public class PortalUseCases {

    private final PortalRepository portalRepository;

    public PortalUseCases(PortalRepository portalRepository) {
        this.portalRepository = portalRepository;
    }

    /**
     * Get customer profile.
     */
    public Optional<CustomerPortalProfile> getProfile(UUID tenantId, UUID customerId) {
        return portalRepository.getProfile(tenantId, customerId);
    }

    /**
     * Update customer profile.
     */
    public CustomerPortalProfile updateProfile(UUID tenantId, UUID customerId, CustomerPortalProfile profile) {
        return portalRepository.updateProfile(tenantId, customerId, profile);
    }

    /**
     * Get customer's support tickets.
     */
    public List<CustomerPortalTicket> getTickets(UUID tenantId, UUID customerId) {
        return portalRepository.getTickets(tenantId, customerId);
    }

    /**
     * Create a new support ticket.
     */
    public CustomerPortalTicket createTicket(UUID tenantId, UUID customerId, CustomerPortalTicket ticket) {
        return portalRepository.createTicket(tenantId, customerId, ticket);
    }

    /**
     * Get ticket by ID.
     */
    public Optional<CustomerPortalTicket> getTicket(UUID tenantId, UUID customerId, UUID ticketId) {
        return portalRepository.getTicket(tenantId, customerId, ticketId);
    }

    /**
     * Get customer's active opportunities.
     */
    public List<Map<String, Object>> getCustomerOpportunities(UUID tenantId, UUID customerId) {
        return portalRepository.getCustomerOpportunities(tenantId, customerId);
    }
}
