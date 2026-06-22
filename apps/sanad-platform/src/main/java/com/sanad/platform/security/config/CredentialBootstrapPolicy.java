package com.sanad.platform.security.config;

import java.util.UUID;

/**
 * Validates the explicit one-time administrative enrollment contract.
 *
 * <p>Supports two tenant-resolution modes:</p>
 * <ul>
 *   <li>Explicit {@code tenant-id} (UUID) of an existing ACTIVE tenant.</li>
 *   <li>Auto-create from {@code tenant-name} + {@code tenant-subdomain}.</li>
 * </ul>
 */
public final class CredentialBootstrapPolicy {

    private static final int MIN_CREDENTIAL_LENGTH = 14;
    private static final int MAX_AUDIT_ACTOR_LENGTH = 100;
    private static final int MAX_TENANT_NAME_LENGTH = 200;
    private static final int MAX_TENANT_SUBDOMAIN_LENGTH = 63;

    private CredentialBootstrapPolicy() {
    }

    /**
     * Resolves the bootstrap tenant identifier.
     *
     * <p>If {@code tenantIdValue} is non-blank, it is parsed as a UUID and returned
     * (the bootstrap service will look it up and require it to be ACTIVE).</p>
     *
     * <p>If {@code tenantIdValue} is blank, both {@code tenantName} and
     * {@code tenantSubdomain} must be non-blank — the bootstrap service will look
     * up the tenant by subdomain and create it if absent.</p>
     *
     * @return the resolved tenant UUID, or {@code null} when bootstrap is disabled
     *         or when auto-create mode is requested (tenantId is null)
     */
    public static UUID resolveTenantId(
            boolean enabled,
            String tenantIdValue,
            String tenantName,
            String tenantSubdomain
    ) {
        if (!enabled) {
            return null;
        }

        boolean hasTenantId = tenantIdValue != null && !tenantIdValue.isBlank();
        boolean hasTenantName = tenantName != null && !tenantName.isBlank();
        boolean hasTenantSubdomain = tenantSubdomain != null && !tenantSubdomain.isBlank();

        if (hasTenantId) {
            try {
                return UUID.fromString(tenantIdValue.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("BOOTSTRAP_TENANT_ID must be a UUID", exception);
            }
        }

        // Auto-create mode: require both name and subdomain
        if (!hasTenantName && !hasTenantSubdomain) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_ID is required when bootstrap is enabled, "
                            + "or provide both BOOTSTRAP_TENANT_NAME and BOOTSTRAP_TENANT_SUBDOMAIN "
                            + "for tenant auto-creation");
        }
        if (!hasTenantName) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_NAME is required for tenant auto-creation "
                            + "when BOOTSTRAP_TENANT_ID is not set");
        }
        if (!hasTenantSubdomain) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_SUBDOMAIN is required for tenant auto-creation "
                            + "when BOOTSTRAP_TENANT_ID is not set");
        }

        String trimmedName = tenantName.trim();
        String trimmedSubdomain = tenantSubdomain.trim();
        if (trimmedName.length() > MAX_TENANT_NAME_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_NAME must not exceed " + MAX_TENANT_NAME_LENGTH + " characters");
        }
        if (trimmedSubdomain.length() > MAX_TENANT_SUBDOMAIN_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_SUBDOMAIN must not exceed "
                            + MAX_TENANT_SUBDOMAIN_LENGTH + " characters");
        }
        if (!trimmedSubdomain.matches("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$")) {
            throw new IllegalStateException(
                    "BOOTSTRAP_TENANT_SUBDOMAIN must be 1-63 chars, lowercase alphanumeric "
                            + "with optional internal hyphens");
        }

        // Signal "auto-create mode" to the service by returning null
        return null;
    }

    public static void requireAdminInput(boolean enabled, String email, String password) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is required when bootstrap is enabled");
        }
        if (password == null || password.length() < MIN_CREDENTIAL_LENGTH) {
            throw new IllegalStateException(
                    "Bootstrap admin password must contain at least " + MIN_CREDENTIAL_LENGTH + " characters");
        }
    }

    public static String requireAuditActor(boolean enabled, String actor) {
        if (!enabled) {
            return null;
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_AUDIT_ACTOR is required when bootstrap is enabled");
        }
        String normalized = actor.trim();
        if (normalized.length() > MAX_AUDIT_ACTOR_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_AUDIT_ACTOR must not exceed 100 characters");
        }
        return normalized;
    }
}
