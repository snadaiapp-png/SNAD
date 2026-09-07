package com.sanad.platform.globalization.country;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcPlatformCountryRepository implements PlatformCountryRepository {

    private final JdbcTemplate jdbc;

    public JdbcPlatformCountryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlatformCountry> findByCode(String countryCode) {
        return jdbc.query(
                "SELECT country_code, name_en, name_ar, default_locale, default_currency, status FROM platform_countries WHERE country_code = ?",
                (rs, rowNum) -> new PlatformCountry(
                        rs.getString("country_code"),
                        rs.getString("name_en"),
                        rs.getString("name_ar"),
                        rs.getString("default_locale"),
                        rs.getString("default_currency"),
                        rs.getString("status")
                ),
                countryCode
        ).stream().findFirst();
    }

    @Override
    public boolean existsByCode(String countryCode) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_countries WHERE country_code = ?",
                Integer.class, countryCode);
        return count != null && count > 0;
    }
}
