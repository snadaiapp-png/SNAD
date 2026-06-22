package com.sanad.platform.security.config;

import java.util.UUID;

/** Validates the explicit one-time administrative enrollment contract. */
public final class CredentialBootstrapPolicy {

    private static final int MIN_CREDENTIAL_LENGTH = 14;
    private static final int MAX_AUDIT_ACTOR_LENGTH = 100;

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
        if (credential == null || credential.length() < MIN_CREDENTIAL_LENGTH) {
            throw new IllegalStateException(
                    "Bootstrap credential must contain at least " + MIN_CREDENTIAL_LENGTH + " characters");
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
