package com.sanad.platform.security.authorization;

/**
 * Stage 05A.2.2 §4 — Thrown when a capability check denies access.
 *
 * <p>Thrown by {@link CapabilityAuthorizationAspect} instead of writing
 * the HTTP response directly. This ensures the controller method is
 * NEVER executed when access is denied. A central exception handler
 * catches this and returns HTTP 403 SANAD-SEC-001.</p>
 */
public class CapabilityDeniedException extends RuntimeException {

    private final java.util.UUID tenantId;
    private final java.util.UUID userId;
    private final String capabilityCode;
    private final String reason;

    public CapabilityDeniedException(java.util.UUID tenantId,
                                       java.util.UUID userId,
                                       String capabilityCode,
                                       String reason) {
        super("Capability denied: tenant=" + tenantId
                + " user=" + userId
                + " capability=" + capabilityCode
                + " reason=" + reason);
        this.tenantId = tenantId;
        this.userId = userId;
        this.capabilityCode = capabilityCode;
        this.reason = reason;
    }

    public java.util.UUID getTenantId() { return tenantId; }
    public java.util.UUID getUserId() { return userId; }
    public String getCapabilityCode() { return capabilityCode; }
    public String getReason() { return reason; }
}
