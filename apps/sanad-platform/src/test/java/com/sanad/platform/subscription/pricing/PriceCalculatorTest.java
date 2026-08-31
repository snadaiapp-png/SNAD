package com.sanad.platform.subscription.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PriceCalculator} — pure integer-minor-unit math.
 * Floating point monetary calculations are forbidden by governance.
 */
@DisplayName("PriceCalculator — pricing models")
class PriceCalculatorTest {

    private static final List<PriceTier> THREE_TIERS = List.of(
            new PriceTier(100L, 100L),   // first 100 units @ 100
            new PriceTier(500L, 80L),    // next 400 units @ 80
            new PriceTier(null, 60L));   // beyond 500 @ 60

    @Test
    @DisplayName("FLAT: returns the base amount regardless of quantity")
    void flat() {
        long amount = PriceCalculator.compute("FLAT", 9900L, null, null, 1, 0);
        assertThat(amount).isEqualTo(9900L);
    }

    @Test
    @DisplayName("PER_USER: base + unit * quantity")
    void perUser() {
        long amount = PriceCalculator.compute("PER_USER", 5000L, 1500L, null, 12, 0);
        assertThat(amount).isEqualTo(5000L + 12 * 1500L);
    }

    @Test
    @DisplayName("TIERED: marginal pricing across tier bands")
    void tieredMarginal() {
        // 600 units: 100*100 + 400*80 + 100*60 = 48_000
        long amount = PriceCalculator.compute("TIERED", 0L, null, THREE_TIERS, 1, 600);
        assertThat(amount).isEqualTo(48_000L);
    }

    @Test
    @DisplayName("VOLUME: cumulative tier — the whole quantity uses the reached tier price")
    void volumeCumulative() {
        long at250 = PriceCalculator.compute("VOLUME", 0L, null, THREE_TIERS, 1, 250);
        long at600 = PriceCalculator.compute("VOLUME", 0L, null, THREE_TIERS, 1, 600);
        assertThat(at250).isEqualTo(250L * 80L);   // lands in tier 2
        assertThat(at600).isEqualTo(600L * 60L);   // reaches the unlimited tier
    }

    @Test
    @DisplayName("USAGE_BASED: unit * usage")
    void usageBased() {
        long amount = PriceCalculator.compute("USAGE_BASED", 0L, 2L, null, 1, 1_500_000);
        assertThat(amount).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("HYBRID: base + unit * usage")
    void hybrid() {
        long amount = PriceCalculator.compute("HYBRID", 10000L, 3L, null, 1, 250);
        assertThat(amount).isEqualTo(10_000L + 750L);
    }

    @Test
    @DisplayName("CUSTOM_CONTRACT: contract-defined base amount")
    void customContract() {
        long amount = PriceCalculator.compute("CUSTOM_CONTRACT", 750_000L, null, null, 42, 99);
        assertThat(amount).isEqualTo(750_000L);
    }

    @Test
    @DisplayName("max_amount_minor clamps the computed amount")
    void maximumClamp() {
        long amount = PriceCalculator.computeWithBounds(
                "PER_USER", 5000L, 1500L, null, 100, 0, null, 20_000L);
        assertThat(amount).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("min_amount_minor floors the computed amount")
    void minimumFloor() {
        long amount = PriceCalculator.computeWithBounds(
                "FLAT", 100L, null, null, 1, 0, 5_000L, null);
        assertThat(amount).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("rejects unknown price models")
    void rejectsUnknownModel() {
        assertThatThrownBy(() -> PriceCalculator.compute("MAGIC", 1L, null, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price model");
    }

    @Test
    @DisplayName("rejects negative computed results defensively")
    void rejectsNegativeResult() {
        assertThatThrownBy(() -> PriceCalculator.compute("PER_USER", -5L, 10L, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
