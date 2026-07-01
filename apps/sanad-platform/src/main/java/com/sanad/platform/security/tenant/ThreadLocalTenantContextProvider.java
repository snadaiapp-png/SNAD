package com.sanad.platform.security.tenant;

import com.sanad.platform.shared.api.exceptions.TenantContextException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stage 04 §7-8 — ThreadLocal-backed implementation of
 * {@link TenantContextProvider}.
 *
 * <p>Lifecycle rules (Stage 04 §8):</p>
 * <ul>
 *   <li>The context is set by {@link TenantContextFilter} AFTER the JWT
 *       has been verified.</li>
 *   <li>The filter clears the context in a {@code finally} block — never
 *       trust an inherited context on a pooled thread.</li>
 *   <li>This implementation uses a ThreadLocal so that context never
 *       propagates to child threads (no {@code InheritableThreadLocal}).</li>
 *   <li>MDC values are NOT trusted as a source of authority — they are
 *       for logging only.</li>
 * </ul>
 *
 * <p>Marked {@code Component} so it participates in Spring's DI. The
 * {@link TenantContextFilter} autowires it and calls {@link #setContext}
 * / {@link #clear}.</p>
 */
@Component
public class ThreadLocalTenantContextProvider implements TenantContextProvider {

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    @Override
    public TenantContext requireContext() {
        TenantContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new TenantContextException();
        }
        return ctx;
    }

    @Override
    public Optional<TenantContext> currentContext() {
        return Optional.ofNullable(HOLDER.get());
    }

    @Override
    public void setContext(TenantContext context) {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext must not be null");
        }
        HOLDER.set(context);
    }

    @Override
    public void clear() {
        HOLDER.remove();
    }
}
