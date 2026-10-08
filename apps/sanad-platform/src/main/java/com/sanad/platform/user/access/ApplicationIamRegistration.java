package com.sanad.platform.user.access;

import java.util.Map;
import java.util.Set;

public record ApplicationIamRegistration(
        String applicationCode,
        String name,
        String localizedName,
        String status,
        String contractVersion,
        Set<String> capabilityNamespaces,
        Set<String> supportedScopes,
        Set<String> declaredCapabilities,
        Set<String> roleTemplates,
        Map<String, Object> compatibilityMetadata) {
}
