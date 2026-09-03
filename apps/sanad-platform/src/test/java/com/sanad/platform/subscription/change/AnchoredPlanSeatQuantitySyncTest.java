package com.sanad.platform.subscription.change;

import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSeatsRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.PlanResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionResponse;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.item.SubscriptionItemService;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R0C-6 — UNIT-01..UNIT-08: the canonical anchored seat-quantity sync and
 * the changeSeats wiring, at unit level (mocked persistence).
 *
 * <p>UNIT-01..UNIT-06 target {@link SubscriptionChangeService#syncAnchoredPlanSeatQuantity};
 * UNIT-07/UNIT-08 target the {@code SaasAdministrationService.changeSeats}
 * wiring with a mocked canonical authority — proving delegation and the
 * unchanged seat-based billing math (PRORATION_AMOUNT_DELTA = 0).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("R0C-6 — anchored PLAN seat quantity convergence (units)")
class AnchoredPlanSeatQuantitySyncTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private SubscriptionItemRepository itemRepository;
    @Mock
    private PriceResolver priceResolver;

    private SubscriptionChangeService changeService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ANCHOR_PLAN_ID = UUID.fromString("c2000000-0000-0000-0000-000000000001");
    private static final UUID ANCHOR_VERSION_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_VERSION_ID = UUID.fromString("d1000000-0000-0000-0000-000000000009");
    private static final long UNIT_PRICE = 30000L;
    private static final Instant PERIOD_START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        changeService = new SubscriptionChangeService(jdbc, itemRepository, priceResolver);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private SubscriptionItemEntity planItem(UUID planId, UUID versionId, int quantity) {
        SubscriptionItemEntity e = new SubscriptionItemEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT_ID);
        e.setSubscriptionId(SUBSCRIPTION_ID);
        e.setItemType("PLAN");
        e.setPlanId(planId);
        e.setPlanVersionId(versionId);
        e.setNameSnapshot("PLAN " + planId);
        e.setQuantity(quantity);
        e.setUnitAmountMinor(UNIT_PRICE);
        e.setCurrencyCode("SAR");
        e.setStatus("ACTIVE");
        e.setCreatedAt(PERIOD_START);
        e.setUpdatedAt(PERIOD_START);
        return e;
    }

    private SubscriptionItemEntity typedItem(String itemType) {
        SubscriptionItemEntity e = planItem(null, null, 2);
        e.setItemType(itemType);
        e.setProductId(UUID.randomUUID());
        return e;
    }

    /** Stubs the anchor-row lookup by invoking the REAL RowMapper against a
     *  mocked ResultSet (also pins the column contract). */
    private void stubAnchorRow(UUID planId, UUID versionId) throws java.sql.SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(TENANT_ID);
        when(rs.getObject("plan_id", UUID.class)).thenReturn(planId);
        when(rs.getObject("plan_version_id", UUID.class)).thenReturn(versionId);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            return mapper.mapRow(rs, 0);
        }).when(jdbc).queryForObject(anyString(), any(RowMapper.class), eq(SUBSCRIPTION_ID));
    }

    // ---------------------------------------------------------------
    // UNIT-01 — sync updates the anchored PLAN quantity, price preserved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-01: syncAnchoredPlanSeatQuantity updates quantity only (unit amount preserved)")
    void unit01_syncUpdatesAnchoredPlanQuantity() throws Exception {
        stubAnchorRow(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID);
        SubscriptionItemEntity anchored = planItem(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID, 5);
        when(itemRepository.findActiveBySubscriptionIdAndPlanId(SUBSCRIPTION_ID, ANCHOR_PLAN_ID))
                .thenReturn(Optional.of(anchored));

        changeService.syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 8);

        verify(itemRepository, times(1))
                .updateQuantityAndAmount(anchored.getId(), 8, UNIT_PRICE);
    }

    // ---------------------------------------------------------------
    // UNIT-02 — secondary quantities untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-02: secondary PLAN items are never read or written by the sync")
    void unit02_secondaryQuantitiesUntouched() throws Exception {
        stubAnchorRow(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID);
        SubscriptionItemEntity anchored = planItem(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID, 5);
        when(itemRepository.findActiveBySubscriptionIdAndPlanId(SUBSCRIPTION_ID, ANCHOR_PLAN_ID))
                .thenReturn(Optional.of(anchored));

        changeService.syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 9);

        // Only the anchored item is addressed — no other lookups, no
        // cancel/insert, exactly one quantity write.
        verify(itemRepository, times(1)).findActiveBySubscriptionIdAndPlanId(
                eq(SUBSCRIPTION_ID), eq(ANCHOR_PLAN_ID));
        verify(itemRepository, times(1))
                .updateQuantityAndAmount(anchored.getId(), 9, UNIT_PRICE);
        verify(itemRepository, never()).updateStatus(any(UUID.class), anyString());
        verify(itemRepository, never()).insert(any(SubscriptionItemEntity.class));
        verify(itemRepository, never()).findBySubscriptionId(any(UUID.class));
    }

    // ---------------------------------------------------------------
    // UNIT-03 — missing anchor fails closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-03: no ACTIVE anchored PLAN item — fail closed, nothing written")
    void unit03_missingAnchorFailsClosed() throws Exception {
        stubAnchorRow(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID);
        when(itemRepository.findActiveBySubscriptionIdAndPlanId(SUBSCRIPTION_ID, ANCHOR_PLAN_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                changeService.syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MISSING_ANCHORED_PLAN_ITEM");

        verify(itemRepository, never()).updateQuantityAndAmount(any(UUID.class), anyInt(), any());
    }

    // ---------------------------------------------------------------
    // UNIT-04 — version mismatch fails closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-04: anchored item version differs from the anchor column — fail closed")
    void unit04_versionMismatchFailsClosed() throws Exception {
        stubAnchorRow(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID);
        SubscriptionItemEntity divergent = planItem(ANCHOR_PLAN_ID, OTHER_VERSION_ID, 5);
        when(itemRepository.findActiveBySubscriptionIdAndPlanId(SUBSCRIPTION_ID, ANCHOR_PLAN_ID))
                .thenReturn(Optional.of(divergent));

        assertThatThrownBy(() ->
                changeService.syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANCHOR_PLAN_VERSION_MISMATCH");

        verify(itemRepository, never()).updateQuantityAndAmount(any(UUID.class), anyInt(), any());
    }

    // ---------------------------------------------------------------
    // UNIT-05 — generic PLAN quantity mutation stays rejected (R0C-5)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-05: SubscriptionItemService.updateQuantity on a PLAN item remains rejected")
    void unit05_planGenericQuantityStillRejected() {
        SubscriptionItemRepository repo = mock(SubscriptionItemRepository.class);
        SubscriptionItemService itemService = new SubscriptionItemService(jdbc, repo,
                mock(PlanVersionRepository.class));
        SubscriptionItemEntity anchored = planItem(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID, 5);
        when(repo.findById(anchored.getId())).thenReturn(Optional.of(anchored));

        assertThatThrownBy(() -> itemService.updateQuantity(anchored.getId(), 9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seat-change");

        verify(repo, never()).updateQuantityAndAmount(any(UUID.class), anyInt(), any());
    }

    // ---------------------------------------------------------------
    // UNIT-06 — ADD_ON / METERED quantities are untouched by the sync
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-06: ADD_ON/METERED item quantities are never modified by the seat sync")
    void unit06_addOnAndMeteredQuantitiesUnchanged() throws Exception {
        stubAnchorRow(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID);
        SubscriptionItemEntity anchored = planItem(ANCHOR_PLAN_ID, ANCHOR_VERSION_ID, 5);
        SubscriptionItemEntity addOn = typedItem("ADD_ON");
        SubscriptionItemEntity metered = typedItem("METERED");
        when(itemRepository.findActiveBySubscriptionIdAndPlanId(SUBSCRIPTION_ID, ANCHOR_PLAN_ID))
                .thenReturn(Optional.of(anchored));

        changeService.syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 8);

        ArgumentCaptor<UUID> updatedIds = ArgumentCaptor.forClass(UUID.class);
        verify(itemRepository, times(1))
                .updateQuantityAndAmount(updatedIds.capture(), anyInt(), any());
        // The ONLY written id is the anchored PLAN item's — never the
        // ADD_ON / METERED items, whose quantities are their own.
        assertThat(updatedIds.getAllValues()).containsExactly(anchored.getId());
        assertThat(updatedIds.getAllValues()).doesNotContain(addOn.getId(), metered.getId());
    }

    // ---------------------------------------------------------------
    // UNIT-07 — changeSeats invokes the canonical quantity sync
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-07: changeSeats delegates the mirror to the canonical sync (once, exact args)")
    void unit07_changeSeatsInvokesCanonicalSync() {
        SubscriptionChangeService authority = mock(SubscriptionChangeService.class);
        SaasAdministrationService legacy = new SaasAdministrationService(
                jdbc, mock(PlatformAuditService.class), event -> { }, null, authority);
        stubLegacyReads(5);

        legacy.changeSeats(SUBSCRIPTION_ID, new ChangeSeatsRequest(8, "R0C-6 unit"), null);

        verify(authority, times(1)).syncAnchoredPlanSeatQuantity(SUBSCRIPTION_ID, ANCHOR_PLAN_ID, 8);
    }

    // ---------------------------------------------------------------
    // UNIT-08 — billing / proration math unchanged (seat-based)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-08: seat increase prices (newSeats - oldSeats) x plan price x fraction — math unchanged")
    void unit08_billingMathUnchanged() {
        SubscriptionChangeService authority = mock(SubscriptionChangeService.class);
        SaasAdministrationService legacy = new SaasAdministrationService(
                jdbc, mock(PlatformAuditService.class), event -> { }, null, authority);
        stubLegacyReads(5);

        legacy.changeSeats(SUBSCRIPTION_ID, new ChangeSeatsRequest(8, "R0C-6 unit"), null);

        // The seat update carries newSeats exactly.
        ArgumentCaptor<Object[]> seatParams = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(3)).update(anyString(), seatParams.capture());
        Object[] seatUpdate = seatParams.getAllValues().stream()
                .filter(args -> args.length == 3 && Integer.valueOf(8).equals(args[0]))
                .findFirst().orElseThrow();
        assertThat(seatUpdate[1]).isNotNull(); // updated_at
        assertThat(seatUpdate[2]).isEqualTo(SUBSCRIPTION_ID);

        // The prorated invoice subtotal is (8 - 5) x unit price x the
        // remaining-fraction of the fixed 30-day period (HALF_UP) — the
        // historical seat-based formula, unchanged (±2 for instant drift).
        ArgumentCaptor<Object[]> invoiceParams = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(1)).update(contains("billing_invoices"), invoiceParams.capture());
        long subtotal = ((Number) invoiceParams.getAllValues().get(0)[5]).longValue();
        Instant now = Instant.now();
        long totalSeconds = Duration.between(PERIOD_START, PERIOD_END).getSeconds();
        long remaining = Math.max(0, Duration.between(now, PERIOD_END).getSeconds());
        long expected = java.math.BigDecimal.valueOf(3L * UNIT_PRICE)
                .multiply(java.math.BigDecimal.valueOf(remaining))
                .divide(java.math.BigDecimal.valueOf(totalSeconds), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        assertThat(subtotal).isBetween(expected - 2, expected + 2);
    }

    // ---------------------------------------------------------------
    // UNIT-08b — no-op: nothing at all
    // ---------------------------------------------------------------

    @Test
    @DisplayName("UNIT-08b: no-op seat change performs no writes and no sync")
    void unit08b_noOpWritesNothing() {
        SubscriptionChangeService authority = mock(SubscriptionChangeService.class);
        SaasAdministrationService legacy = new SaasAdministrationService(
                jdbc, mock(PlatformAuditService.class), event -> { }, null, authority);
        stubLegacyReads(5);

        legacy.changeSeats(SUBSCRIPTION_ID, new ChangeSeatsRequest(5, "R0C-6 unit"), null);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(authority, never()).syncAnchoredPlanSeatQuantity(any(UUID.class), any(UUID.class), anyInt());
    }

    // ---------------------------------------------------------------
    // legacy read stubs for UNIT-07/UNIT-08
    // ---------------------------------------------------------------

    private void stubLegacyReads(int currentSeats) {
        SubscriptionResponse subscription = new SubscriptionResponse(
                SUBSCRIPTION_ID, TENANT_ID, "Tenant", ANCHOR_PLAN_ID, "R0C6-A", "Plan A",
                null, null, "ACTIVE", "MONTHLY", null,
                currentSeats, 0L, "SAR",
                PERIOD_START, null, PERIOD_START, PERIOD_END,
                false, null, PERIOD_START, PERIOD_START);
        when(jdbc.query(contains("tenant_subscriptions"), any(RowMapper.class), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of(subscription));
        try {
            stubPlanRow();
        } catch (java.sql.SQLException ignored) {
            // impossible — mocked ResultSet
        }
        when(jdbc.queryForObject(contains("organizations"), eq(Long.class), eq(TENANT_ID)))
                .thenReturn(0L);
    }

    /** getPlan resolves through the REAL mapPlanCore RowMapper over a mocked
     *  ResultSet; the entitlement sub-query returns an empty list. */
    private void stubPlanRow() throws java.sql.SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(ANCHOR_PLAN_ID);
        when(rs.getString("code")).thenReturn("R0C6-A");
        when(rs.getString("name")).thenReturn("Plan A");
        when(rs.getString("description")).thenReturn(null);
        when(rs.getString("status")).thenReturn("ACTIVE");
        when(rs.getString("currency_code")).thenReturn("SAR");
        when(rs.getLong("monthly_price_minor")).thenReturn(UNIT_PRICE);
        when(rs.getLong("annual_price_minor")).thenReturn(UNIT_PRICE * 12);
        when(rs.getInt("trial_days")).thenReturn(0);
        when(rs.getInt("max_users")).thenReturn(50);
        when(rs.getInt("max_organizations")).thenReturn(5);
        when(rs.getLong("storage_mb")).thenReturn(1024L);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(PERIOD_START));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(PERIOD_START));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(contains("saas_plans"), any(RowMapper.class), eq(ANCHOR_PLAN_ID));
        when(jdbc.query(contains("saas_plan_entitlements"), any(RowMapper.class), eq(ANCHOR_PLAN_ID)))
                .thenReturn(List.of());
    }
}
