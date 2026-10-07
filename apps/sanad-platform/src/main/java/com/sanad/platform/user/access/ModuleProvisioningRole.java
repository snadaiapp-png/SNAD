package com.sanad.platform.user.access;

import java.util.Set;
import java.util.UUID;

public record ModuleProvisioningRole(
        UUID roleId,
        String roleCode,
        String roleName,
        Set<String> capabilities) {
}
