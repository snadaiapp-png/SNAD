package com.sanad.platform.globalization.country;

import java.util.Optional;

/**
 * Repository for platform country master data.
 *
 * <p>Not tenant-scoped — countries are reference data.</p>
 */
public interface PlatformCountryRepository {

    Optional<PlatformCountry> findByCode(String countryCode);

    boolean existsByCode(String countryCode);
}
