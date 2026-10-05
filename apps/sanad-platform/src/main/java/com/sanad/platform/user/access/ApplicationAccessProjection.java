package com.sanad.platform.user.access;

import java.util.List;
import java.util.Set;

public record ApplicationAccessProjection(
        String applicationCode,
        String name,
        String localizedName,
        String lifecycleStatus,
        String contractVersion,
        boolean registryValid,
        boolean effectiveAccess,
        List<String> assignedRoles,
        Set<String> supportedScopes,
        Set<String> effectiveCapabilities,
        String reason) {
}
