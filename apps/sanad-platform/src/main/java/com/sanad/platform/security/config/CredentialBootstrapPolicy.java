package com.sanad.platform.security.config;

import java.util.UUID;

/** Validates the explicit one-time administrative enrollment contract. */
public final class CredentialBootstrapPolicy {
    private CredentialBootstrapPolicy() {
    }

    public static UUID requireTenantId(boolean enabled, String value) {
        if (!enabled) {
            return null;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_TENANT_ID is required when bootstrap is enabled");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("BOOTSTRAP_TENANT_ID must be a UUID", exception);
        }
    }

    public static void requireAdminInput(boolean enabled, String email, String credential) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is required when bootstrap is enabled");
        }
        if (credential == null || credential.length() < 14) {
            throw new IllegalStateException("Bootstrap credential must contain at least 14 characters");
        }
    }
}
