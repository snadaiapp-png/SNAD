package com.sanad.platform.crm.collaboration.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Port for evaluating whether a user is eligible to receive a collaboration
 * invitation / participate in a CRM entity.
 *
 * <p>Implementations compose the platform's identity validation
 * (active user check) with the platform's RBAC capability evaluation
 * to produce a read-only decision. Implementations MUST NOT mutate
 * RBAC state (no role grants, no role writes, no capability writes).
 *
 * <p>The decision surface is intentionally minimal:
 * <ul>
 *   <li>{@code eligible} — true only when the user is active AND the
 *       capability evaluation returned {@code allowed=true}.</li>
 *   <li>{@code reason} — a stable machine-readable reason code.</li>
 * </ul>
 *
 * <p>The port does NOT expose roleId / roleCode / grantId — those are
 * RBAC internals that the collaboration domain has no business reading.
 */
public interface RecipientEligibilityPort {

    /**
     * Evaluate eligibility for the given user against the required
     * capability in the supplied tenant + organization scope.
     *
     * @param tenantId            the tenant scope (must be non-null)
     * @param userId              the user being evaluated (must be non-null)
     * @param organizationId      the organization scope — may be null
     *                             for tenant-wide capability grants
     * @param requiredCapability  the capability code (must be non-null + non-blank)
     * @return a non-null {@link EligibilityDecision}
     */
    EligibilityDecision evaluate(UUID tenantId,
                                  UUID userId,
                                  UUID organizationId,
                                  String requiredCapability);

    /**
     * Immutable eligibility decision. {@code reason} is always non-null
     * so callers can rely on it for downstream error reporting.
     */
    record EligibilityDecision(boolean eligible, String reason) {
        public EligibilityDecision {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
