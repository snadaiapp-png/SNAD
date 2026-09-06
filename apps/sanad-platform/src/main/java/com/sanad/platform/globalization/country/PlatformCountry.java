package com.sanad.platform.globalization.country;

/**
 * Platform country master projection.
 *
 * <p>Not tenant-owned. Reference data for ISO-3166-1 alpha-2 countries.</p>
 */
public record PlatformCountry(
        String countryCode,
        String nameEn,
        String nameAr,
        String defaultLocale,
        String defaultCurrency,
        String status
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
