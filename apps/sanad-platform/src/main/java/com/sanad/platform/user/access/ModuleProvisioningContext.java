package com.sanad.platform.user.access;

import java.util.List;
import java.util.Set;

public record ModuleProvisioningContext(
        String applicationCode,
        String name,
        String localizedName,
        Set<String> capabilityNamespaces,
        Set<String> declaredCapabilities,
        Set<String> supportedScopes,
        List<ModuleProvisioningRole> roles) {
}
