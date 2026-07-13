package com.sanad.platform.crm.integration.domain;

import java.util.UUID;

/**
 * Port for the centralized tenant context.
 * Extracts tenant ID and principal ID from the authenticated security context.
 * CRM modules never read tenant from request body or query parameters.
 */
public interface TenantContextPort {
    UUID getTenantId();
    UUID getPrincipalId();
    void assertAuthenticated();
}
