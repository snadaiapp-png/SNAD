package com.sanad.platform.crm.portal.web;

import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.portal.application.PortalUseCases;
import com.sanad.platform.crm.portal.domain.CustomerPortalProfile;
import com.sanad.platform.crm.portal.domain.CustomerPortalTicket;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for customer portal endpoints.
 * Mounted at /api/v2/crm/portal.
 */
@RestController
@RequestMapping("/api/v2/crm/portal")
public class PortalController {

    private final PortalUseCases portalUseCases;

    public PortalController(PortalUseCases portalUseCases) {
        this.portalUseCases = portalUseCases;
    }

    /**
     * Get customer profile.
     */
    @GetMapping("/profile")
    @RequireCapability("CRM.PORTAL.READ")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> getProfile(
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        return portalUseCases.getProfile(tenantId, customerId)
                .map(profile -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("customerId", profile.customerId().toString());
                    data.put("displayName", profile.displayName());
                    data.put("email", profile.email() != null ? profile.email() : "");
                    data.put("company", profile.company() != null ? profile.company() : "");
                    data.put("phone", profile.phone() != null ? profile.phone() : "");
                    return ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(data, UUID.randomUUID()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Update customer profile.
     */
    @PutMapping("/profile")
    @RequireCapability("CRM.PORTAL.WRITE")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> updateProfile(
            @RequestBody PortalModels.UpdateProfileRequest request,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        CustomerPortalProfile profile = CustomerPortalProfile.of(
                customerId, tenantId, request.displayName(), request.email(), request.company(), request.phone());
        CustomerPortalProfile updated = portalUseCases.updateProfile(tenantId, customerId, profile);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("customerId", updated.customerId().toString());
        data.put("displayName", updated.displayName());
        data.put("email", updated.email() != null ? updated.email() : "");
        data.put("company", updated.company() != null ? updated.company() : "");
        data.put("phone", updated.phone() != null ? updated.phone() : "");
        return ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(data, UUID.randomUUID()));
    }

    /**
     * Get customer's support tickets.
     */
    @GetMapping("/tickets")
    @RequireCapability("CRM.PORTAL.READ")
    public ResponseEntity<CrmEnvelopes.ListResponse<Map<String, Object>>> getTickets(
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        List<Map<String, Object>> tickets = portalUseCases.getTickets(tenantId, customerId).stream()
                .map(this::toTicketMap)
                .toList();

        return ResponseEntity.ok(CrmEnvelopes.ListResponse.of(
                tickets,
                CrmEnvelopes.Page.empty(50),
                UUID.randomUUID()));
    }

    /**
     * Create a new support ticket.
     */
    @PostMapping("/tickets")
    @RequireCapability("CRM.PORTAL.WRITE")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> createTicket(
            @RequestBody PortalModels.CreateTicketRequest request,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        CustomerPortalTicket ticket = CustomerPortalTicket.of(customerId, tenantId, request.subject(), request.description());
        CustomerPortalTicket created = portalUseCases.createTicket(tenantId, customerId, ticket);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CrmEnvelopes.SingleResponse.of(toTicketMap(created), UUID.randomUUID()));
    }

    /**
     * Get ticket by ID.
     */
    @GetMapping("/tickets/{ticketId}")
    @RequireCapability("CRM.PORTAL.READ")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> getTicket(
            @PathVariable UUID ticketId,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        return portalUseCases.getTicket(tenantId, customerId, ticketId)
                .map(ticket -> ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(toTicketMap(ticket), UUID.randomUUID())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get customer's active opportunities.
     */
    @GetMapping("/opportunities")
    @RequireCapability("CRM.PORTAL.READ")
    public ResponseEntity<CrmEnvelopes.ListResponse<Map<String, Object>>> getOpportunities(
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID customerId = customerId(authentication);

        List<Map<String, Object>> opportunities = portalUseCases.getCustomerOpportunities(tenantId, customerId);

        return ResponseEntity.ok(CrmEnvelopes.ListResponse.of(
                opportunities,
                CrmEnvelopes.Page.empty(50),
                UUID.randomUUID()));
    }

    private Map<String, Object> toTicketMap(CustomerPortalTicket ticket) {
        return Map.of(
                "ticketId", ticket.ticketId().toString(),
                "subject", ticket.subject(),
                "description", ticket.description(),
                "status", ticket.status(),
                "priority", ticket.priority(),
                "createdAt", ticket.createdAt().toString(),
                "updatedAt", ticket.updatedAt().toString()
        );
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    /**
     * Extract customer ID from authentication context.
     *
     * <p>IMPORTANT: In the customer portal scenario, the authenticated user IS the customer.
     * The {@code user_id} claim in the JWT token IS the CRM contact/customer ID.
     * This is by design — portal users are mapped 1:1 to CRM contacts during registration.
     * If a portal user is not a CRM contact, the profile lookup returns 404.
     */
    private static UUID customerId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
