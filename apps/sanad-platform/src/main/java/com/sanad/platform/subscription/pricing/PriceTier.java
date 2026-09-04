package com.sanad.platform.subscription.pricing;

/**
 * One tier of a TIERED/VOLUME price: quantities up to {@code upTo} (null =
 * unlimited) are priced at {@code unitAmountMinor} per unit.
 *
 * @param upTo            inclusive upper bound of the tier band (null = infinity)
 * @param unitAmountMinor per-unit price in minor units
 */
public record PriceTier(Long upTo, long unitAmountMinor) {

    public PriceTier {
        if (unitAmountMinor < 0) {
            throw new IllegalArgumentException("tier unit amount must be non-negative");
        }
        if (upTo != null && upTo <= 0) {
            throw new IllegalArgumentException("tier upper bound must be positive");
        }
    }

    public boolean isUnlimited() {
        return upTo == null;
    }
}
