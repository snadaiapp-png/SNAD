package com.sanad.platform.globalization.country;

import org.springframework.stereotype.Service;

@Service
public class PlatformCountryService {

    private final PlatformCountryRepository repository;

    public PlatformCountryService(PlatformCountryRepository repository) {
        this.repository = repository;
    }

    /**
     * Requires the country to exist and be ACTIVE.
     *
     * @throws IllegalArgumentException if the country is missing or inactive
     */
    public PlatformCountry requireActive(String countryCode) {
        CountryCode code = CountryCode.of(countryCode);
        PlatformCountry country = repository.findByCode(code.value())
                .orElseThrow(() -> new IllegalArgumentException("Country not registered: " + code.value()));
        if (!country.isActive()) {
            throw new IllegalArgumentException("Country is not active: " + code.value());
        }
        return country;
    }
}
