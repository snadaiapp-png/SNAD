package com.sanad.platform.subscription.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Country → default currency catalog ({@code country_currencies}).
 * Data-driven; no country/currency rules may be hardcoded in UI code.
 */
@Repository
public class CountryCurrencyRepository {

    public record CountryCurrency(String countryCode, String currencyCode, boolean isDefault) {
    }

    private final JdbcTemplate jdbc;

    public CountryCurrencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CountryCurrency> findAll() {
        return jdbc.query(
                "SELECT country_code, currency_code, is_default FROM country_currencies ORDER BY country_code",
                (rs, n) -> new CountryCurrency(
                        rs.getString("country_code"),
                        rs.getString("currency_code"),
                        rs.getBoolean("is_default")));
    }

    @Transactional(readOnly = true)
    public Optional<String> currencyForCountry(String countryCode) {
        List<String> codes = jdbc.queryForList(
                "SELECT currency_code FROM country_currencies WHERE country_code = ? "
                        + "ORDER BY is_default DESC LIMIT 1",
                String.class, countryCode == null ? "GLOBAL" : countryCode);
        return codes.stream().findFirst();
    }
}
