package com.sanad.platform.access.override;

import java.time.Instant;
import java.util.List;
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
