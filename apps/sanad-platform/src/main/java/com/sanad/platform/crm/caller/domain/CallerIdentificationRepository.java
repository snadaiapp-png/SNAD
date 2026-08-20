package com.sanad.platform.crm.caller.domain;

import java.util.List;
import java.util.UUID;

/**
 * Dedicated caller-candidate persistence contract (G8-02 §6).
 *
 * <p>Both lookups are EXACT, normalized-value equality queries scoped by
 * {@code tenant_id} — the generic communication-methods search API is NOT
 * used, and no LIKE / contains / fuzzy matching is permitted.
 */
public interface CallerIdentificationRepository {

    /** Maximum candidates fetched for one lookup (defensive bound, G8-02 §34). */
    int CANDIDATE_LOOKUP_LIMIT = 20;

    /**
     * Exact reverse lookup over the canonical phone source
     * {@code crm_communication_methods} via {@code idx_crm_communication_methods_lookup}
     * ({@code tenant_id, method_type, normalized_value, status}).
     *
     * <p>Only ACTIVE {@code PHONE}/{@code MOBILE} methods owned by an ACTIVE
     * person or account are returned, ordered by
     * {@code verified DESC, preferred DESC, updated_at ASC, id ASC} and
     * bounded to {@value #CANDIDATE_LOOKUP_LIMIT} + 1 rows (overflow => AMBIGUOUS).
     */
    List<CallerCandidate> findActiveCallerCandidates(UUID tenantId, String normalizedPhone);

    /**
     * G8-ADR-002 lead fallback (explicit, lower priority, tenant-scoped,
     * exact forms only): ACTIVE leads whose stored {@code phone} equals one of
     * the exact legacy representations derived from the normalized E.164 input.
     * Only consulted when the canonical lookup returns no candidate.
     */
    List<CallerCandidate> findActiveLeadCandidates(UUID tenantId, String normalizedPhone);

    /**
     * Exact legacy representations of a normalized E.164 number used for the
     * lead fallback (G8-02 §10): {@code +966541234567}, {@code 966541234567},
     * {@code 541234567}, {@code 0541234567}. Derived deterministically — this
     * is exact equality, not fuzzy matching.
     */
    static List<String> legacyLeadPhoneForms(String normalizedE164) {
        if (normalizedE164 == null || !normalizedE164.startsWith("+")) return List.of();
        String digits = normalizedE164.substring(1);
        String national = digits.startsWith("966") && digits.length() > 3 ? digits.substring(3) : digits;
        return List.of(normalizedE164, digits, national, "0" + national).stream().distinct().toList();
    }
}
