package com.sanad.platform.crm.caller.domain;

/**
 * Official caller lookup match states (G8-02 §12, G8-ADR-005).
 *
 * <p>NO fuzzy matching and NO {@code FIRST_MATCH_WINS}: ties resolve to
 * {@link #AMBIGUOUS} with count-only disclosure.
 */
public enum CallerMatchStatus {
    EXACT,
    AMBIGUOUS,
    UNKNOWN,
    PRIVATE_NUMBER,
    INVALID_NUMBER,
    RESTRICTED
}
