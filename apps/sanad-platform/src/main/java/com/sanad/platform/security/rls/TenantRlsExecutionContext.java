package com.sanad.platform.security.rls;

import java.util.UUID;

/**
 * Explicit tenant scope for narrowly classified operations that execute before
 * Spring Security has installed an authenticated principal (for example login
 * after the request tenant has been validated as a lookup scope).
 *
 * <p>The scope does not authorize a user. It only constrains SQL visibility;
 * authentication and authorization checks remain mandatory.</p>
 */
public final class TenantRlsExecutionContext {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantRlsExecutionContext() {
    }

    public static UUID currentTenantId() {
        return TENANT.get();
    }

    public static Scope open(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant scope must not be null");
        }
        UUID current = TENANT.get();
        if (current != null && !current.equals(tenantId)) {
            throw new IllegalStateException("tenant scope cannot switch within an active operation");
        }
        TENANT.set(tenantId);
        return new Scope(current, tenantId);
    }

    public static final class Scope implements AutoCloseable {
        private final UUID previous;
        private final UUID opened;
        private boolean closed;

        private Scope(UUID previous, UUID opened) {
            this.previous = previous;
            this.opened = opened;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            UUID current = TENANT.get();
            if (!opened.equals(current)) {
                throw new IllegalStateException("tenant scope changed unexpectedly");
            }
            if (previous == null) {
                TENANT.remove();
            } else {
                TENANT.set(previous);
            }
            closed = true;
        }
    }
}
