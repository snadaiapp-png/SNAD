package com.sanad.platform.hr.identity;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifier Normalizer — input canonicalization for HR Person identifiers.
 *
 * <p>Normalizes raw user input before cryptographic processing and uniqueness
 * checks. Specifically:
 * <ul>
 *   <li>{@code identifierType}: trims surrounding whitespace and uppercases</li>
 *   <li>{@code issuingCountryCode}: trims, uppercases, ISO 3166-1 alpha-2 form;
 *       {@code null} remains {@code null} (NULLS NOT DISTINCT uniqueness)</li>
 *   <li>{@code plaintextValue}: trims surrounding whitespace only (no case
 *       change — identifiers are case-sensitive)</li>
 * </ul>
 * </p>
 */
public final class IdentifierNormalizer {

    public String normalizeIdentifierType(String raw) {
        return Objects.requireNonNull(raw, "identifierType")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public String normalizeCountryCode(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeValue(String raw) {
        return Objects.requireNonNull(raw, "plaintextValue").trim();
    }
}
