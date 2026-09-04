package com.sanad.platform.subscription.change;

import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceCalculator;
import com.sanad.platform.subscription.pricing.PriceEntity;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.pricing.PriceTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionChangeService} — the Preview → Validate →
 * Confirm → Execute change pipeline (mission §11).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionChangeService — preview/execute change pipeline")
class SubscriptionChangeServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private SubscriptionItemRepository itemRepository;
    @Mock
    private PriceResolver priceResolver;

    private SubscriptionChangeService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CURRENT_VERSION = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_VERSION = UUID.fromString("d1000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SubscriptionChangeService(jdbc, itemRepository, priceResolver);
    }

    private SubscriptionItemEntity planItem(UUID versionId, long monthlyMinor) {
        SubscriptionItemEntity e = new SubscriptionItemEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT_ID);
        e.setSubscriptionId(SUBSCRIPTION_ID);
        e.setItemType("PLAN");
        e.setPlanVersionId(versionId);
        e.setNameSnapshot("GROWTH");
        e.setQuantity(1);
        e.setUnitAmountMinor(monthlyMinor);
        e.setCurrencyCode("SAR");
        e.setStatus("ACTIVE");
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        return e;
    }

    private PriceEntity price(UUID versionId, long baseMinor) {
        PriceEntity p = new PriceEntity();
        p.setId(UUID.randomUUID());
        p.setPlanVersionId(versionId);
        p.setPriceModel("FLAT");
        p.setCountryCode("SA");
        p.setCurrencyCode("SAR");
        p.setBillingInterval("MONTHLY");
        p.setBaseAmountMinor(baseMinor);
        p.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
        return p;
    }

    private void activeSubscriptionRow() {
        lenient().when(jdbc.<UUID>queryForObject(
                contains("SELECT tenant_id, plan_id FROM tenant_subscriptions"),
                eq(UUID.class), eq(SUBSCRIPTION_ID))).thenReturn(TENANT_ID);
        lenient().when(jdbc.<String>queryForObject(
                contains("SELECT billing_cycle, country_code FROM tenant_subscriptions"),
                eq(String.class), eq(SUBSCRIPTION_ID))).thenReturn("MONTHLY");
    }

    private void stubPlanItem(SubscriptionItemEntity planItem) {
        lenient().when(itemRepository.findBySubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(planItem));
        lenient().when(itemRepository.findActiveBySubscriptionIdAndType(SUBSCRIPTION_ID, "PLAN"))
                .thenReturn(Optional.of(planItem));
    }

    @Test
    @DisplayName("preview: reports current items, target price and the delta")
    void previewComputesDelta() {
        activeSubscriptionRow();
        stubPlanItem(planItem(CURRENT_VERSION, 29900L));
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.of(price(TARGET_VERSION, 39900L)));

        SubscriptionChangeService.ChangePreview preview =
                service.preview(SUBSCRIPTION_ID, TARGET_VERSION, "SA", NOW);

        assertThat(preview.fromStatus()).isEqualTo("CURRENT");
        assertThat(preview.currentItems()).hasSize(1);
        assertThat(preview.currentMonthlyMinor()).isEqualTo(29900L);
        assertThat(preview.targetMonthlyMinor()).isEqualTo(39900L);
        assertThat(preview.deltaMonthlyMinor()).isEqualTo(10_000L);
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    @DisplayName("preview: warns when no target price exists for country/interval")
    void previewWarnsOnMissingPrice() {
        activeSubscriptionRow();
        stubPlanItem(planItem(CURRENT_VERSION, 29900L));
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.empty());

        SubscriptionChangeService.ChangePreview preview =
                service.preview(SUBSCRIPTION_ID, TARGET_VERSION, "SA", NOW);

        assertThat(preview.warnings()).anyMatch(w -> w.contains("price"));
        assertThat(preview.targetMonthlyMinor()).isNull();
        assertThat(preview.deltaMonthlyMinor()).isNull();
    }

    @Test
    @DisplayName("preview: downgrade direction is visible in the signed delta")
    void previewDowngradeNegativeDelta() {
        activeSubscriptionRow();
        stubPlanItem(planItem(CURRENT_VERSION, 29900L));
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.of(price(TARGET_VERSION, 9900L)));

        SubscriptionChangeService.ChangePreview preview =
                service.preview(SUBSCRIPTION_ID, TARGET_VERSION, "SA", NOW);

        assertThat(preview.deltaMonthlyMinor()).isEqualTo(-20_000L);
    }

    @Test
    @DisplayName("execute: rejects a change whose preview produced warnings")
    void executeRejectsWarningPreview() {
        activeSubscriptionRow();
        stubPlanItem(planItem(CURRENT_VERSION, 29900L));
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(SUBSCRIPTION_ID, TARGET_VERSION, "SA", "upgrade",
                null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warning");
    }

    @Test
    @DisplayName("execute: swaps the PLAN item version, cancels the old item, writes ledger")
    void executeSwapsPlanItem() {
        activeSubscriptionRow();
        SubscriptionItemEntity current = planItem(CURRENT_VERSION, 29900L);
        stubPlanItem(current);
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.of(price(TARGET_VERSION, 39900L)));
        when(jdbc.queryForMap(contains("SELECT pv.plan_id, pv.currency_code FROM plan_versions"),
                eq(TARGET_VERSION)))
                .thenReturn(Map.of("plan_id", UUID.randomUUID(), "currency_code", "SAR"));

        SubscriptionChangeService.ChangeResult result =
                service.execute(SUBSCRIPTION_ID, TARGET_VERSION, "SA", "upgrade", null, null);

        assertThat(result.status()).isEqualTo("EXECUTED");
        verify(itemRepository).updateStatus(current.getId(), "CANCELLED");
        verify(itemRepository).insert(any(SubscriptionItemEntity.class));
        verify(jdbc).update(contains("INSERT INTO subscription_commands"),
                any(), eq(SUBSCRIPTION_ID), eq(TENANT_ID), eq("PLAN_CHANGE"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("tiered target pricing flows through the calculator")
    void previewSupportsTieredTarget() {
        activeSubscriptionRow();
        SubscriptionItemEntity seats = planItem(CURRENT_VERSION, 0L);
        seats.setQuantity(250);
        stubPlanItem(seats);
        PriceEntity tiered = price(TARGET_VERSION, 0L);
        tiered.setPriceModel("TIERED");
        tiered.setTiersJson("[{\"upTo\":100,\"unitAmountMinor\":100},{\"upTo\":null,\"unitAmountMinor\":60}]");
        when(priceResolver.resolveForPlanVersion(eq(TARGET_VERSION), any(), eq("MONTHLY"), any()))
                .thenReturn(Optional.of(tiered));

        SubscriptionChangeService.ChangePreview preview =
                service.preview(SUBSCRIPTION_ID, TARGET_VERSION, "SA", NOW);

        // 250 seats: 100*100 + 150*60 = 19_000
        assertThat(preview.targetMonthlyMinor()).isEqualTo(19_000L);
    }
}
