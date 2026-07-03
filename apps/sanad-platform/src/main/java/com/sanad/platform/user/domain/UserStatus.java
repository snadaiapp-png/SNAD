package com.sanad.platform.user.domain;

/**
 * Lifecycle status of a {@link User}.
 *
 * <p>The User lifecycle intentionally mirrors the Membership lifecycle
 * so that future linking logic can keep them in sync without awkward
 * status mapping tables.</p>
 */
public enum UserStatus {

    /** User is active and can interact with the platform. */
    ACTIVE,

    /** User is temporarily inactive (admin action, not removed). */
    INACTIVE,

    /** Invitation sent; user has not yet accepted. */
    INVITED,

    /** User is suspended (e.g. security hold, policy violation). */
    SUSPENDED,

    /** User is permanently archived; row retained for audit. */
    ARCHIVED
}
