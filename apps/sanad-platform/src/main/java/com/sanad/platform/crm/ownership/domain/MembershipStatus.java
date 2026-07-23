package com.sanad.platform.crm.ownership.domain;

/**
 * Membership lifecycle values used by CRM-008B persistence.
 * Team memberships use ACTIVE/ENDED; queue memberships use ACTIVE/REMOVED.
 */
public enum MembershipStatus {
    ACTIVE,
    ENDED,
    REMOVED
}
