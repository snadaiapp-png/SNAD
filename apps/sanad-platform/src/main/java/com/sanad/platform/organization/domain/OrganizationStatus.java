package com.sanad.platform.organization.domain;

/**
 * Lifecycle status of an {@link Organization} within a tenant.
 *
 * <p>An Organization is the first operational aggregate linked to a
 * Tenant. Multiple Organizations may exist under a single Tenant,
 * each representing a distinct business unit (e.g. a branch, a
 * subsidiary, a brand). The status here governs whether the
 * Organization is currently usable by the operational modules
 * (ERP, CRM, HRM, Accounting, Commerce) that will be built on top
 * of it in subsequent stages.</p>
 *
 * <p>State transitions are intentionally minimal at this stage:
 * the application service layer (to be added later) will enforce
 * the rules (e.g. ACTIVE -> INACTIVE on administrative action,
 * INACTIVE -> ARCHIVED after retention period).</p>
 */
public enum OrganizationStatus {

    /** Organization is operational and can be used by all modules. */
    ACTIVE,

    /** Organization is temporarily disabled (e.g. billing hold, audit). */
    INACTIVE,

    /** Organization is permanently retired; data retained for audit only. */
    ARCHIVED
}
