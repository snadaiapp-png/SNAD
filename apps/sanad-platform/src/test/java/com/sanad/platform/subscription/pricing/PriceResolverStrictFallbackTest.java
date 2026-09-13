package com.sanad.platform.subscription.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceResolverStrictFallbackTest {

    @Mock
    private PriceRepository repository;

    @Test
    void doesNotFallBackToAnotherCountryWhenExactAndGlobalAreMissing() {
        UUID versionId = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-13T00:00:00Z");
        PriceEntity ae = new PriceEntity();
        ae.setId(UUID.randomUUID());
        ae.setPlanVersionId(versionId);
        ae.setCountryCode("AE");
        ae.setCurrencyCode("AED");
        ae.setBillingInterval("MONTHLY");
        ae.setPriceModel("FLAT");
        ae.setBaseAmountMinor(10_000L);
        ae.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findEffective(versionId, null, "MONTHLY", at)).thenReturn(List.of(ae));

        Optional<PriceEntity> resolved = new PriceResolver(repository)
                .resolveForPlanVersion(versionId, "SA", "MONTHLY", at);

        assertThat(resolved).isEmpty();
    }
}
