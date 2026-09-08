package com.sanad.platform.subscription.lifecycle;

import org.springframework.stereotype.Service;

/**
 * R0C-10 — EXPIRED successor feature gate (default OFF).
 *
 * <p>Repository convention (dunning / trial-expiry schedulers): a system
 * property with an environment-variable fallback, default {@code false}.</p>
 *
 * <ul>
 *   <li>property: {@code sanad.scp.subscription.expired-successor.enabled}</li>
 *   <li>env var: {@code SANAD_SCP_EXPIRED_SUCCESSOR_ENABLED}</li>
 *   <li>default: OFF — no EXPIRED successor creation, no second-row runtime
 *       activation (the R0C-9 dead-end behavior is preserved bit-for-bit).</li>
 * </ul>
 *
 * <p>Gate ON: only the approved EXPIRED continuation path
 * (tenant has NO effective subscription, deterministic latest historical
 * state EXPIRED, request satisfies the existing creation contract) may insert
 * a successor row. The gate is read lazily on every decision so operators can
 * toggle it without a restart and tests can control it deterministically.</p>
 */
@Service
public class ExpiredSuccessorGate {

    static final String PROPERTY = "sanad.scp.subscription.expired-successor.enabled";
    static final String ENV_VAR = "SANAD_SCP_EXPIRED_SUCCESSOR_ENABLED";

    public boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY,
                System.getenv() == null ? "false"
                        : System.getenv().getOrDefault(ENV_VAR, "false")));
    }
}
