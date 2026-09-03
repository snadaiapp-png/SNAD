package com.sanad.platform.security.scope;

public record ScopedAuthorizationDecision(
        boolean allowed,
        String reason,
        AccessScopeType matchedScopeType) {

    public static ScopedAuthorizationDecision allow(AccessScopeType scopeType) {
        return new ScopedAuthorizationDecision(true, "SCOPE_MATCH", scopeType);
    }

    public static ScopedAuthorizationDecision deny(String reason) {
        return new ScopedAuthorizationDecision(false, reason, null);
    }
}
