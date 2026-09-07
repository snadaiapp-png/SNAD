package com.sanad.platform.globalization.country;

import java.util.Locale;
import java.util.Objects;

/**
 * ISO-3166-1 alpha-2 country code value object.
 *
 * <p>Normalizes input by trimming whitespace and uppercasing.
 * Rejects 3-letter codes (alpha-3) and non-alphabetic input.</p>
 */
public final class CountryCode {

    private final String value;

    public CountryCode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("country code must be ISO alpha-2: " + raw);
        }
        this.value = normalized;
    }

    public static CountryCode of(String raw) {
        return new CountryCode(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CountryCode that = (CountryCode) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
