package com.sanad.platform.security.authorization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Restricts cross-tenant control-plane operations to the configured control tenant.
 * The check is independent from normal tenant RBAC and therefore prevents a tenant
 * administrator from becoming a platform operator merely by holding an ADMIN role.
 */
@Component
public class ControlPlaneAccessGuard {

    private final UUID controlTenantId;

    public ControlPlaneAccessGuard(@Value("${sanad.control-plane.tenant-id:}") String configuredTenantId) {
        this.controlTenantId = parseConfiguredTenant(configuredTenantId);
    }

    public void require(Authentication authentication) {
        if (controlTenantId == null) {
            throw new AccessDeniedException("Control-plane access is not configured");
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        Object tenantValue = details.get("tenant_id");
        if (tenantValue == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        UUID authenticatedTenant;
        try {
            authenticatedTenant = UUID.fromString(tenantValue.toString());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid authenticated tenant context", exception);
        }
        if (!controlTenantId.equals(authenticatedTenant)) {
            throw new AccessDeniedException("Control-plane tenant required");
        }
    }

    private static UUID parseConfiguredTenant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SANAD_CONTROL_PLANE_TENANT_ID must be a UUID", exception);
        }
    }
}
