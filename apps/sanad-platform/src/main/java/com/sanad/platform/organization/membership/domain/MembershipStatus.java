package com.sanad.platform.organization.membership.domain;

/**
 * Lifecycle status of an {@link OrganizationMembership}.
 *
 * <p>The membership lifecycle is intentionally decoupled from any future
 * User domain: a membership is identified by an email address (the
 * invitation target) and may transition through these states without
 * a User record existing yet.</p>
 */
public enum MembershipStatus {

    /** Member can access the organization. */
    ACTIVE,

    /** Member temporarily cannot access (admin action, not removed). */
    INACTIVE,

    /** Invitation sent; recipient has not yet accepted. */
    INVITED,

    /** Member permanently removed; row retained for audit. */
    REMOVED
}
