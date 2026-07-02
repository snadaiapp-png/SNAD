package com.sanad.platform.tenant.domain;

/** Lifecycle state of a SNAD tenant. */
public enum TenantStatus {

    /** Tenant has been created but provisioning is incomplete. */
    PENDING,

    /** Tenant is operating inside a time-limited evaluation period. */
    TRIAL,

    /** Tenant is fully operational. */
    ACTIVE,

    /** Tenant remains retained but financial or contractual action is required. */
    PAST_DUE,

    /** Tenant access is temporarily disabled by an authorized operator. */
    SUSPENDED,

    /** Tenant subscription or service relationship has been cancelled. */
    CANCELLED,

    /** Tenant is retired and retained only for audit and retention obligations. */
    ARCHIVED
}
