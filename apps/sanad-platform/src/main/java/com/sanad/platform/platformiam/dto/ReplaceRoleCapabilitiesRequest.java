package com.sanad.platform.platformiam.dto;

import java.util.List;
import java.util.UUID;

public record ReplaceRoleCapabilitiesRequest(List<UUID> capabilityIds) {
    public ReplaceRoleCapabilitiesRequest {
        capabilityIds = capabilityIds == null ? List.of() : List.copyOf(capabilityIds);
    }
}
