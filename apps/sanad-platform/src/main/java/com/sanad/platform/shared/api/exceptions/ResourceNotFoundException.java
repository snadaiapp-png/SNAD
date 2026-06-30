package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Raised when a requested resource does not exist (or is not visible to the caller).
 * Maps to HTTP 404 / {@link ErrorCode#SANAD_RES_001}.
 */
public class ResourceNotFoundException extends TypedBusinessException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        this(resourceType, identifier, java.util.Map.of());
    }

    public ResourceNotFoundException(String resourceType, Object identifier,
                                      java.util.Map<String, String> diagnostics) {
        super(ErrorCode.SANAD_RES_001,
              "The requested " + resourceType + " was not found.",
              merge(diagnostics, "resourceType", resourceType,
                    "identifier", String.valueOf(identifier)));
    }

    private static java.util.Map<String, String> merge(
            java.util.Map<String, String> base, String k1, String v1, String k2, String v2) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>(base);
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
