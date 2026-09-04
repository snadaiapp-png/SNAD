package com.sanad.platform.subscription.pricing;

import java.util.List;
import java.util.Set;

/**
 * Pure pricing math over integer minor units — no floating point (repo
 * standard for money). All models from the {@code prices.price_model}
 * CHECK constraint are supported here.
 */
public final class PriceCalculator {

    public static final Set<String> SUPPORTED_MODELS = Set.of(
            "FLAT", "PER_USER", "PER_EMPLOYEE", "PER_BRANCH", "PER_TRANSACTION",
            "PER_API_REQUEST", "PER_AI_TOKEN", "TIERED", "VOLUME",
            "USAGE_BASED", "HYBRID", "CUSTOM_CONTRACT");

    private static final Set<String> UNIT_QUANTITY_MODELS = Set.of(
            "PER_USER", "PER_EMPLOYEE", "PER_BRANCH");

    private PriceCalculator() {
    }

    /**
     * Computes the period amount for a price definition.
     *
     * @param priceModel  one of {@link #SUPPORTED_MODELS}
     * @param baseMinor   fixed base amount (contract/subscription base)
     * @param unitMinor   per-unit amount (null when the model has none)
     * @param tiers       TIERED/VOLUME tiers (null otherwise)
     * @param quantity    licensed quantity (seats/employees/branches)
     * @param usage       metered usage quantity for the period
     */
    public static long compute(String priceModel, long baseMinor, Long unitMinor,
                               List<PriceTier> tiers, int quantity, long usage) {
        return computeWithBounds(priceModel, baseMinor, unitMinor, tiers, quantity, usage, null, null);
    }

    /**
     * As {@link #compute} with minimum/maximum clamps (from the price row).
     */
    public static long computeWithBounds(String priceModel, long baseMinor, Long unitMinor,
                                         List<PriceTier> tiers, int quantity, long usage,
                                         Long minMinor, Long maxMinor) {
        if (priceModel == null || !SUPPORTED_MODELS.contains(priceModel)) {
            throw new IllegalArgumentException("Unsupported price model: " + priceModel);
        }
        if (baseMinor < 0) {
            throw new IllegalArgumentException("base amount must be non-negative");
        }
        long amount = switch (priceModel) {
            case "FLAT", "CUSTOM_CONTRACT" -> baseMinor;
            case "TIERED" -> tieredMarginal(tiers, usage > 0 ? usage : quantity);
            case "VOLUME" -> volumeCumulative(tiers, usage > 0 ? usage : quantity);
            case "USAGE_BASED" -> multiply(unitMinor == null ? 0L : unitMinor, usage);
            case "HYBRID" -> baseMinor + multiply(unitMinor == null ? 0L : unitMinor, usage);
            // PER_USER / PER_EMPLOYEE / PER_BRANCH / PER_TRANSACTION /
            // PER_API_REQUEST / PER_AI_TOKEN all share base + unit * count
            default -> baseMinor + multiply(
                    unitMinor == null ? 0L : unitMinor,
                    UNIT_QUANTITY_MODELS.contains(priceModel) ? quantity : usage);
        };
        if (minMinor != null && amount < minMinor) {
            amount = minMinor;
        }
        if (maxMinor != null && amount > maxMinor) {
            amount = maxMinor;
        }
        if (amount < 0) {
            throw new IllegalArgumentException("computed amount must be non-negative");
        }
        return amount;
    }

    /**
     * TIERED (marginal): each tier band is priced at its own unit price.
     * Tiers must be ordered ascending by upper bound; the last tier may be
     * unlimited (null upTo).
     */
    private static long tieredMarginal(List<PriceTier> tiers, long totalQuantity) {
        requireTiers(tiers);
        long remaining = totalQuantity;
        long lowerBound = 0;
        long amount = 0;
        for (PriceTier tier : tiers) {
            if (remaining <= 0) {
                break;
            }
            long bandWidth = tier.isUnlimited()
                    ? remaining
                    : Math.min(remaining, tier.upTo() - lowerBound);
            if (bandWidth > 0) {
                amount += multiply(bandWidth, tier.unitAmountMinor());
                remaining -= bandWidth;
            }
            lowerBound = tier.isUnlimited() ? lowerBound : tier.upTo();
        }
        return amount;
    }

    /**
     * VOLUME (cumulative): the whole quantity is priced at the unit price of
     * the tier the quantity reaches.
     */
    private static long volumeCumulative(List<PriceTier> tiers, long totalQuantity) {
        requireTiers(tiers);
        for (PriceTier tier : tiers) {
            if (tier.isUnlimited() || totalQuantity <= tier.upTo()) {
                return multiply(totalQuantity, tier.unitAmountMinor());
            }
        }
        return multiply(totalQuantity, tiers.get(tiers.size() - 1).unitAmountMinor());
    }

    private static void requireTiers(List<PriceTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("TIERED/VOLUME prices require tiers");
        }
    }

    /** Overflow-safe integer multiplication for monetary amounts. */
    private static long multiply(long a, long b) {
        if (a == 0 || b == 0) {
            return 0L;
        }
        long result = a * b;
        if (result / b != a) {
            throw new ArithmeticException("monetary amount overflow");
        }
        return result;
    }
}
