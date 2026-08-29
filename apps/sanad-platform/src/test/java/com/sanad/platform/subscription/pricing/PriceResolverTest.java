package com.sanad.platform.subscription.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriceResolver} — country-aware price selection with
 * GLOBAL fallback and effective-window filtering.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PriceResolver — country/currency price selection")
class PriceResolverTest {

    @Mock
    private PriceRepository repository;

    private PriceResolver resolver;

    private static final UUID VERSION_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @BeforeEach
    void setUp() {
        resolver = new PriceResolver(repository);
    }

    private PriceEntity price(String countryCode, long baseMinor) {
        PriceEntity p = new PriceEntity();
        p.setId(UUID.randomUUID());
        p.setPlanVersionId(VERSION_ID);
        p.setPriceModel("FLAT");
        p.setCountryCode(countryCode);
        p.setCurrencyCode("SA".equals(countryCode) ? "SAR" : "USD");
        p.setBillingInterval("MONTHLY");
        p.setBaseAmountMinor(baseMinor);
        p.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
        return p;
    }

    @Test
    @DisplayName("prefers the exact country price over the GLOBAL fallback")
    void prefersExactCountryMatch() {
        when(repository.findEffective(eq(VERSION_ID), eq(null), eq("MONTHLY"), any()))
                .thenReturn(List.of(price("GLOBAL", 1200L), price("SA", 9900L)));

        Optional<PriceEntity> resolved = resolver.resolveForPlanVersion(VERSION_ID, "SA", "MONTHLY", NOW);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getCountryCode()).isEqualTo("SA");
        assertThat(resolved.get().getBaseAmountMinor()).isEqualTo(9900L);
    }

    @Test
    @DisplayName("falls back to GLOBAL when no country-specific price exists")
    void fallsBackToGlobal() {
        when(repository.findEffective(eq(VERSION_ID), eq(null), eq("MONTHLY"), any()))
                .thenReturn(List.of(price("GLOBAL", 1200L)));

        Optional<PriceEntity> resolved = resolver.resolveForPlanVersion(VERSION_ID, "SA", "MONTHLY", NOW);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getCountryCode()).isEqualTo("GLOBAL");
    }

    @Test
    @DisplayName("skips prices whose effective window has not started")
    void skipsNotYetEffective() {
        PriceEntity future = price("SA", 9900L);
        future.setEffectiveFrom(Instant.parse("2027-01-01T00:00:00Z"));
        lenient().when(repository.findEffective(eq(VERSION_ID), eq(null), eq("MONTHLY"), any()))
                .thenReturn(List.of(future, price("GLOBAL", 1200L)));

        Optional<PriceEntity> resolved = resolver.resolveForPlanVersion(VERSION_ID, "SA", "MONTHLY", NOW);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getCountryCode()).isEqualTo("GLOBAL");
    }

    @Test
    @DisplayName("skips expired prices")
    void skipsExpired() {
        PriceEntity expired = price("SA", 9900L);
        expired.setEffectiveTo(Instant.parse("2026-02-01T00:00:00Z"));
        when(repository.findEffective(eq(VERSION_ID), eq(null), eq("MONTHLY"), any()))
                .thenReturn(List.of(expired));

        Optional<PriceEntity> resolved = resolver.resolveForPlanVersion(VERSION_ID, "SA", "MONTHLY", NOW);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("resolves product prices for add-ons with the same fallback chain")
    void resolvesProductPrices() {
        when(repository.findEffective(eq(null), eq(null), eq("MONTHLY"), any()))
                .thenReturn(List.of());

        Optional<PriceEntity> resolved = resolver.resolveForProduct(null, "AE", "MONTHLY", NOW);

        assertThat(resolved).isEmpty();
    }
}
