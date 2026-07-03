package com.sanad.platform.organization.domain;

/**
 * Lifecycle status of an {@link Organization} within a {@code Tenant}.
 *
 * <p>An Organization is the operational aggregate that groups business
 * modules (ERP, CRM, HRM, Accounting, Commerce) under a single
 * tenant-scoped entity. The status drives visibility and routing
 * decisions across the platform.</p>
 */
public enum OrganizationStatus {

    /** Organization is operational and available for business transactions. */
    ACTIVE,

    /** Organization is temporarily deactivated (admin action, not deleted). */
    INACTIVE,

    /** Organization is permanently retired; data retained for audit only. */
    ARCHIVED
}
