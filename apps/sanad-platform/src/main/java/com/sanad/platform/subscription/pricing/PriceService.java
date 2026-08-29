package com.sanad.platform.subscription.pricing;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Price management — create/list prices for plan versions and products.
 * Validation mirrors the {@code prices} CHECK constraints so callers get
 * clean 400s instead of SQL errors.
 */
@Service
public class PriceService {

    private final PriceRepository repository;
    private final JdbcTemplate jdbc;

    public PriceService(PriceRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional
    public PriceEntity createForPlanVersion(UUID planVersionId, PriceEntity price) {
        requireExists("plan_versions", planVersionId, "plan version");
        price.setPlanVersionId(planVersionId);
        return create(price);
    }

    @Transactional
    public PriceEntity createForProduct(UUID productId, PriceEntity price) {
        requireExists("products", productId, "product");
        price.setProductId(productId);
        return create(price);
    }

    private PriceEntity create(PriceEntity price) {
        validate(price);
        price.setId(UUID.randomUUID());
        Instant now = Instant.now();
        price.setEffectiveFrom(price.getEffectiveFrom() == null ? now : price.getEffectiveFrom());
        price.setCreatedAt(now);
        price.setUpdatedAt(now);
        repository.insert(price);
        return price;
    }

    private void validate(PriceEntity price) {
        if (!PriceCalculator.SUPPORTED_MODELS.contains(price.getPriceModel())) {
            throw new IllegalArgumentException("Unsupported price model: " + price.getPriceModel());
        }
        if (price.getCountryCode() == null || price.getCountryCode().isBlank()) {
            price.setCountryCode(PriceResolver.GLOBAL);
        }
        if (!price.getCountryCode().equals(PriceResolver.GLOBAL)
                && !price.getCountryCode().matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("countryCode must be ISO alpha-2 or GLOBAL");
        }
        if (price.getCurrencyCode() == null || !price.getCurrencyCode().matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currencyCode must be a 3-letter ISO code");
        }
        if (price.getBillingInterval() == null || price.getBillingInterval().isBlank()) {
            price.setBillingInterval("MONTHLY");
        }
        if (!List.of("MONTHLY", "ANNUAL", "ONE_TIME").contains(price.getBillingInterval())) {
            throw new IllegalArgumentException("Invalid billing interval: " + price.getBillingInterval());
        }
        if (price.getBaseAmountMinor() < 0) {
            throw new IllegalArgumentException("baseAmountMinor must be non-negative");
        }
        // Tier JSON must parse into valid tiers when provided
        PriceRepository.parseTiers(price.getTiersJson());
    }

    private void requireExists(String table, UUID id, String label) {
        Long count;
        try {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id);
        } catch (EmptyResultDataAccessException e) {
            count = 0L;
        }
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Unknown " + label + ": " + id);
        }
    }

    @Transactional(readOnly = true)
    public List<PriceEntity> listForPlanVersion(UUID planVersionId) {
        return repository.findByPlanVersion(planVersionId);
    }

    @Transactional(readOnly = true)
    public List<PriceEntity> listForProduct(UUID productId) {
        return repository.findByProduct(productId);
    }
}
