package com.sanad.platform.security.authorization;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Immediate in-process eviction for authorization mutations. */
@Component
public class AuthorizationInvalidationListener {
    private final AuthorizationDecisionCache cache;

    public AuthorizationInvalidationListener(AuthorizationDecisionCache cache) {
        this.cache = cache;
    }

    @EventListener
    public void onChanged(AuthorizationChangedEvent event) {
        if (event == null || event.tenantId() == null || event.userId() == null) return;
        cache.invalidateSubject(event.tenantId(), event.userId());
    }
}
