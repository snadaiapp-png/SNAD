package com.sanad.platform.security.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stage 04A.3 §5 — Configuration for tenant RLS enforcement.
 *
 * <p>When {@code sanad.tenant.rls.enabled=true} (PostgreSQL), RLS binding
 * failures throw {@link TenantRlsBindingException} and fail the transaction
 * immediately. When {@code false} (H2 local profile), RLS binding is a no-op.</p>
 */
@Component
@ConfigurationProperties(prefix = "sanad.tenant.rls")
public class TenantRlsProperties {

    /**
     * Whether RLS transaction binding is enabled. Set to true in
     * production and tenant-postgres-test profiles; false in local (H2).
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
