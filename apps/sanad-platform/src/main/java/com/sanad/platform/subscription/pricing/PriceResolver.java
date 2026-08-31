package com.sanad.platform.subscription.pricing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective price for a plan version or product in a country,
 * currency, and billing interval.
 *
 * <p>Fallback chain: exact country match → GLOBAL default. Within the same
 * country the most recently effective price wins (allows scheduled future
 * prices and supersedes).
 */
@Service
public class PriceResolver {

    public static final String GLOBAL = "GLOBAL";

    private final PriceRepository repository;

    public PriceResolver(PriceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<PriceEntity> resolveForPlanVersion(UUID planVersionId, String countryCode,
                                                       String billingInterval, Instant at) {
        return resolve(planVersionId, null, countryCode, billingInterval, at);
    }

    @Transactional(readOnly = true)
    public Optional<PriceEntity> resolveForProduct(UUID productId, String countryCode,
                                                   String billingInterval, Instant at) {
        return resolve(null, productId, countryCode, billingInterval, at);
    }

    private Optional<PriceEntity> resolve(UUID planVersionId, UUID productId, String countryCode,
                                          String billingInterval, Instant at) {
        List<PriceEntity> candidates = repository.findEffective(
                planVersionId, productId, billingInterval, at);
        return pick(candidates, countryCode, at)
                .or(() -> pick(candidates, GLOBAL, at))
                .or(() -> candidates.stream()
                        // last resort: any window-valid price regardless of country annotation
                        .filter(p -> inWindow(p, at))
                        .max(Comparator.comparing(PriceEntity::getEffectiveFrom,
                                Comparator.nullsFirst(Comparator.naturalOrder()))));
    }

    private Optional<PriceEntity> pick(List<PriceEntity> candidates, String countryCode, Instant at) {
        return candidates.stream()
                .filter(p -> countryCode.equals(p.getCountryCode()))
                .filter(p -> inWindow(p, at))
                .max(Comparator.comparing(PriceEntity::getEffectiveFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private boolean inWindow(PriceEntity p, Instant at) {
        if (p.getEffectiveFrom() != null && p.getEffectiveFrom().isAfter(at)) {
            return false;
        }
        return p.getEffectiveTo() == null || p.getEffectiveTo().isAfter(at);
    }
}
