package com.sanad.platform.security.authorization;

/**
 * Explicit boundary used only by test configurations that intentionally disable
 * HTTP authentication. Production does not provide an implementation, so
 * capability enforcement remains deny-by-default.
 */
@FunctionalInterface
public interface CapabilityAuthorizationBypass {
    boolean isEnabled();
}
