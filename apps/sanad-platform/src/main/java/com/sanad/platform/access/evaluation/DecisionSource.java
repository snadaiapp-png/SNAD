package com.sanad.platform.access.evaluation;

/**
 * Sources a unified authorization decision may be attributed to (spec §4.1
 * Revision D).
 *
 * <p>There is deliberately NO {@code PROTECTED_SAFETY} source: protected
 * roles are mutation invariants (protected-role and last-admin guards), not a
 * runtime allow layer, and no allow source outranks an active explicit DENY.</p>
 */
public enum DecisionSource {
    /** Active direct user DENY override — capability-wide in v1; dominates every candidate ALLOW. */
    EXPLICIT_DENY,

    /** Active direct user ALLOW override (ordinary or break-glass grant evaluated normally). */
    EXPLICIT_ALLOW,

    /** Active role capability grant. */
    ROLE_GRANT,

    /** Applicable ABAC/ReBAC relationship policy match. */
    RELATIONSHIP_POLICY,

    /** Valid delegated-administration grant (W2 — absent in W1). */
    DELEGATED_GRANT,

    /** Terminal default deny (hard-guard failure or no matching candidate/scope). */
    DEFAULT_DENY
}
