package com.sanad.platform.crm.calls.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Call aggregate record (G8-03 §6, G8-ADR-003: source of truth for call
 * lifecycle; CRM activity/timeline are business projections).
 *
 * <p>Only NORMALIZED phone numbers are stored; raw numbers are never
 * persisted (G8-03 §7) and the number is masked in logs/audit.
 */
public record CallEvent(
        UUID id,
        UUID tenantId,
        long version,

        String provider,
        String providerCallId,

        CallDirection direction,
        CallerSourceOfRecord source,

        String fromNumberNormalized,
        String toNumberNormalized,

        String matchStatus,
        String matchedEntityType,
        UUID matchedEntityId,
        UUID matchedContactId,
        UUID matchedAccountId,
        String matchSource,

        UUID agentUserId,
        UUID deviceId,

        CallStatus status,

        Instant ringingAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSeconds,
        CallDisposition disposition,

        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Provider-neutral source contract (reuses the G8 lookup source enum
     * values; adapters for PBX/VoIP arrive in Track G).
     */
    public enum CallerSourceOfRecord { MANUAL, ANDROID_CALL, IOS_CALLER_EXTENSION, PBX, VOIP }

    public static final String MATCH_EXACT = "EXACT";
    public static final String MATCH_AMBIGUOUS = "AMBIGUOUS";
    public static final String MATCH_UNKNOWN = "UNKNOWN";
    public static final String MATCH_PRIVATE = "PRIVATE_NUMBER";
    public static final String MATCH_INVALID = "INVALID_NUMBER";
    public static final String MATCH_RESTRICTED = "RESTRICTED";

    /** Business-significant timeline events (G8-03 §23) — no retry noise. */
    public static final String EVENT_STARTED = "crm.call.started";
    public static final String EVENT_ANSWERED = "crm.call.answered";
    public static final String EVENT_COMPLETED = "crm.call.completed";
    public static final String EVENT_MISSED = "crm.call.missed";
    public static final String EVENT_FAILED = "crm.call.failed";

    public static String timelineEventFor(CallStatus status) {
        return switch (status) {
            case ANSWERED -> EVENT_ANSWERED;
            case COMPLETED -> EVENT_COMPLETED;
            case MISSED -> EVENT_MISSED;
            case FAILED -> EVENT_FAILED;
            case RINGING -> EVENT_STARTED;
            case REJECTED -> "crm.call.rejected";
            case BUSY -> "crm.call.busy";
        };
    }
}
