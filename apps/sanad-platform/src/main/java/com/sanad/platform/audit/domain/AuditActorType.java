package com.sanad.platform.audit.domain;

/**
 * Stage 05 §4 — The type of actor that initiated the audited action.
 *
 * <p>Used for attribution: every audit event records WHO (or WHAT)
 * performed the action. The actor type determines how the actor
 * identity fields are interpreted.</p>
 */
public enum AuditActorType {

    /** An authenticated end-user acting through the UI or API. */
    USER,

    /** A named backend service acting on its own behalf (e.g. notification service). */
    SERVICE,

    /** The platform itself (e.g. startup bootstrap, scheduled maintenance). */
    SYSTEM,

    /** A background job (e.g. cron-triggered cleanup, async retry). */
    BACKGROUND_JOB,

    /** An external integration calling the platform via API. */
    EXTERNAL_INTEGRATION
}
