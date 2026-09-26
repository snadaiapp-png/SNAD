package com.sanad.platform.platformiam.domain;

/**
 * Lifecycle of a user's explicit Platform IAM membership.
 */
public enum PlatformMembershipStatus {
    INVITED,
    ACTIVE,
    SUSPENDED,
    LOCKED,
    DISABLED
}
