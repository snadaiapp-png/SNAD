package com.sanad.platform.user.access;

import java.util.List;
import java.util.UUID;

public record ModuleCapabilityGrantRequest(
        UUID targetUserId,
        String routeRoot,
        List<String> capabilityCodes) {
}
