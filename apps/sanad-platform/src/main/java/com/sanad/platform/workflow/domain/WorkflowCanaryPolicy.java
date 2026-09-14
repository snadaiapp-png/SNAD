package com.sanad.platform.workflow.domain;

/**
 * R0.G8 — system canary classification.
 *
 * <p>The Workflow Y2 production write canary is RELEASE INFRASTRUCTURE, not
 * a business process. Its canonical semantic form is START → SUCCESS → END
 * and its identity marker is the reserved code prefix
 * {@code Y2-PROD-CANARY-} (scripts/production/verify-workflow-y2-production-write-canary.sh).
 * The default business Workflow workspace must clearly classify such
 * definitions so they can be separated from normal business workflows.</p>
 *
 * <p>Classification is derived, never stored: historical canary definitions
 * must never be mutated or deleted, and the marker cannot be lost by an
 * edit. Published canary versions remain fully immutable under the normal
 * publication contract.</p>
 */
public final class WorkflowCanaryPolicy {

    /** Reserved code prefix for Workflow Y2 production write canaries. */
    public static final String CANARY_CODE_PREFIX = "Y2-PROD-CANARY-";

    private WorkflowCanaryPolicy() {
    }

    /** @return true when the given definition code is a system canary. */
    public static boolean isSystemCanary(String code) {
        return code != null && code.startsWith(CANARY_CODE_PREFIX);
    }
}
