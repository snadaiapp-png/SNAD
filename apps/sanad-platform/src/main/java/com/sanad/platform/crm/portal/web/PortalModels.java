package com.sanad.platform.crm.portal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request/Response DTOs for customer portal endpoints.
 */
public final class PortalModels {

    private PortalModels() {}

    /**
     * Request body for updating customer profile.
     */
    public record UpdateProfileRequest(
            @NotBlank String displayName,
            String email,
            String company,
            String phone
    ) {}

    /**
     * Request body for creating a support ticket.
     */
    public record CreateTicketRequest(
            @NotBlank String subject,
            @NotBlank String description
    ) {}
}
