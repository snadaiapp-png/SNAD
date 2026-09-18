package com.sanad.platform.hr.recruitment.domain;

import java.util.UUID;

/**
 * HRM-G1 — transition context carried by every domain-level guard invocation
 * (design §5.3, TENANT_GATES: identity tuples always carry tenant_id).
 *
 * <p>Pure domain value object — no persistence. {@code reasonCode} may be null
 * only for transitions whose matrix entry does not require one.</p>
 *
 * @param tenantId   owning tenant (never null/blank — fail closed)
 * @param actorId    attributed actor (tenant user via IAM, or candidate surface ref)
 * @param reasonCode registry-validated reason code, when the transition requires one
 */
public record HrTransitionContext(UUID tenantId, String actorId, String reasonCode) {

    public HrTransitionContext {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for every G1 transition");
        }
    }
}
