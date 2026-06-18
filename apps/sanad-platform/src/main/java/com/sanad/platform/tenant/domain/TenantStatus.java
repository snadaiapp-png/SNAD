package com.sanad.platform.tenant.domain;

/**
 * Lifecycle status of a {@link Tenant}.
 *
 * <p>The state machine is intentionally minimal at this stage of the platform.
 * Future stages will introduce explicit transitions (e.g. PENDING to ACTIVE
 * on verification, ACTIVE to SUSPENDED on billing failure, etc.) enforced by
 * the application layer.</p>
 */
public enum TenantStatus {

    /** Tenant is fully operational and can be used by end-users. */
    ACTIVE,

    /** Tenant is temporarily disabled (e.g. billing issue, admin action). */
    SUSPENDED,

    /** Tenant has been created but not yet provisioned / verified. */
    PENDING,

    /** Tenant is permanently retired; data retained for audit only. */
    ARCHIVED
}
