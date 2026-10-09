package com.sanad.platform.user.access;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ModuleUserAccessProjection(
        UUID userId,
        String email,
        String username,
        String displayName,
        String status,
        boolean effectiveAccess,
        List<String> assignedRoles,
        Set<String> effectiveCapabilities) {
}
