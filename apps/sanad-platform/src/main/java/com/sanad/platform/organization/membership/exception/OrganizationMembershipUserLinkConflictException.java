package com.sanad.platform.organization.membership.exception;

import java.util.UUID;

/** Raised when a membership-to-user link violates an application invariant. */
public class OrganizationMembershipUserLinkConflictException extends RuntimeException {

    private final UUID membershipId;
    private final UUID userId;

    public OrganizationMembershipUserLinkConflictException(
            UUID membershipId, UUID userId, String message) {
        super(message);
        this.membershipId = membershipId;
        this.userId = userId;
    }

    public UUID getMembershipId() { return membershipId; }
    public UUID getUserId() { return userId; }
}
