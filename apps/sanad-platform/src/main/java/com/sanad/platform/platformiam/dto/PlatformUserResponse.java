package com.sanad.platform.platformiam.dto;

import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record PlatformUserResponse(
        UUID userId,
        String email,
        String displayName,
        UserStatus accountStatus,
        PlatformMembershipStatus membershipStatus,
        Instant lastLoginAt,
        Instant joinedAt) {
}
