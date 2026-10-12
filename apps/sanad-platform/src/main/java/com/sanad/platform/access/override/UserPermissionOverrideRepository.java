package com.sanad.platform.access.override;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side access to {@code user_permission_overrides} for the decision
 * pipeline (Wave 1 Task 7). The write-side service and audited mutations are
 * completed by the override administration service (Wave 1 Task 9) on this
 * same canonical repository surface — no parallel override store is created.
 */
public interface UserPermissionOverrideRepository {

    /**
     * All override rows (ALLOW and DENY) for the subject/capability pair
     * regardless of validity window. Window filtering (valid_from/valid_until)
     * is performed by the decision pipeline so that expiry semantics stay
     * under test.
     */
    List<ActiveOverride> findOverrides(UUID tenantId, UUID userId, UUID capabilityId);

    /** Persists a new audited override row (Wave 1 Task 9 write side). */
    UserPermissionOverride insert(UserPermissionOverride override);

    /** Loads one override row within the tenant boundary. */
    Optional<UserPermissionOverride> find(UUID tenantId, UUID id);

    /** Lists override rows for one subject within the tenant boundary. */
    List<UserPermissionOverride> listForUser(UUID tenantId, UUID userId);

    /** Closes the validity window NOW (revoke). Returns false when absent/already closed. */
    boolean expire(UUID tenantId, UUID id);

    /**
     * Introspection read for identity surfaces (auth {@code /me} and login
     * bootstrap): effects of all in-window overrides for one subject joined
     * to their capability codes. Only overrides whose validity window covers
     * NOW and whose capability is ACTIVE are returned; DENY rows are included
     * so callers can mirror the decision pipeline's explicit-deny dominance.
     * This is a coarse view for display/bootstrap only — authoritative
     * enforcement remains the scope-aware, fail-closed decision pipeline.
     */
    List<OverrideCapabilityEffect> findActiveCapabilityEffects(UUID tenantId, UUID userId);

    /**
     * One joined {@code user_permission_overrides}/{@code access_capabilities}
     * projection for introspection.
     *
     * @param effect "ALLOW" or "DENY"
     * @param code   canonical capability code (e.g. {@code HRM.PAYROLL.VIEW})
     */
    record OverrideCapabilityEffect(String effect, String code) {
    }

    /**
     * One {@code user_permission_overrides} row.
     *
     * @param effect "ALLOW" or "DENY" (DENY is capability-wide by DB CHECK:
     *               scope fields are NULL for DENY rows)
     */
    record ActiveOverride(
            UUID id,
            String effect,
            String scopeType,
            UUID scopeReference,
            Instant validFrom,
            Instant validUntil) {
    }
}
