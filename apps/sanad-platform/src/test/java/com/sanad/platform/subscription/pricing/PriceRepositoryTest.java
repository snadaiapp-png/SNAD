package com.sanad.platform.subscription.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PriceRepository — tier JSON contract")
class PriceRepositoryTest {

    @Test
    @DisplayName("malformed tier JSON fails closed instead of becoming an empty tier list")
    void malformedTierJsonFailsClosed() {
        assertThatThrownBy(() -> PriceRepository.parseTiers("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed price tiers JSON");
    }
}
