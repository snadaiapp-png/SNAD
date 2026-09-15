package com.sanad.platform.hr.recruitment.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 — HrCandidate aggregate root (design §5.1, §10).
 *
 * <p>Recruitment-scoped identity — NOT an hr_people/Person row. Contact PII
 * is minimized per the G0 pattern: channels are stored ONLY as
 * platform-crypto ciphertext + HMAC blind-index hash (never plaintext).
 * Duplicate contact matches are advisory (warning + audit) and never merge
 * rows. National-ID-grade identity is NEVER collected here (Person domain,
 * G0-owned, conversion-time only).</p>
 */
public record HrCandidate(
        UUID id,
        UUID tenantId,
        String candidateNumber,
        String displayName,
        StoredContact email,
        StoredContact phone,
        String compensationExpectationsCiphertext,
        String poolState,
        long version) {

    /** Pool states (§5.1): ACTIVE | HIRED | WITHDRAWN | ARCHIVED (history retained). */
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_HIRED = "HIRED";
    public static final String STATE_WITHDRAWN = "WITHDRAWN";
    public static final String STATE_ARCHIVED = "ARCHIVED";

    /** ARCHIVED is terminal; HIRED candidates are retained as history (fail closed). */
    public static boolean canArchive(String poolState) {
        return STATE_ACTIVE.equals(poolState) || STATE_WITHDRAWN.equals(poolState);
    }

    /**
     * Minimized contact channel: ciphertext + blind-index hash only.
     *
     * @param ciphertext versioned platform-crypto payload ({@code enc:...})
     * @param hash       deterministic tenant-scoped HMAC blind index (dedup key)
     */
    public record StoredContact(String ciphertext, String hash) {
        public StoredContact {
            Objects.requireNonNull(ciphertext, "ciphertext");
            Objects.requireNonNull(hash, "hash");
        }
    }

    public HrCandidate {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(poolState, "poolState");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("HRM_CANDIDATE_NAME_REQUIRED: display name must not be blank");
        }
    }
}
