package com.sanad.platform.crm.caller.application;

import com.sanad.platform.crm.caller.domain.CallerMatchStatus;

import java.util.UUID;

/**
 * Outcomes of a caller lookup (G8-02 §25–§28).
 *
 * <p>Data-minimized by construction: AMBIGUOUS carries only the candidate
 * count, UNKNOWN/PRIVATE_NUMBER/RESTRICTED carry no card fields, and EXACT
 * carries only the minimal caller card.
 */
public record CallerLookupResult(
        CallerMatchStatus matchStatus,
        String entityType,          // CONTACT | ACCOUNT | LEAD (EXACT and disclosed only)
        UUID entityId,
        String displayName,          // masked when CONFIDENTIAL without READ_RESTRICTED
        UUID accountId,
        String accountName,          // masked when CONFIDENTIAL without READ_RESTRICTED
        String phoneLabel,
        Boolean verified,
        Boolean preferred,
        String lifecycleStatus,
        String privacyLevel,         // effective privacy classification
        String matchSource,          // CANONICAL_COMMUNICATION_METHOD | LEGACY_LEAD_PHONE
        Integer candidateCount) {

    public static CallerLookupResult empty(CallerMatchStatus status) {
        return new CallerLookupResult(status, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /** AMBIGUOUS outcome with the count-only disclosure contract (G8-02 §16). */
    public static CallerLookupResult ambiguous(int candidateCount) {
        return new CallerLookupResult(CallerMatchStatus.AMBIGUOUS, null, null, null, null, null,
                null, null, null, null, null, null, candidateCount);
    }
}
