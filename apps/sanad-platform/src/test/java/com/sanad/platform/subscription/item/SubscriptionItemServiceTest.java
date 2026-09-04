package com.sanad.platform.subscription.item;

import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionItemService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Multi-product subscriptions (a subscription with more than one item)</li>
 *   <li>Type-specific reference validation (PLAN item requires a plan)</li>
 *   <li>Backfill-compatible dual read (effective plan version falls back to
 *       subscription.plan_version_id / active version when the item has none)</li>
 *   <li>Item cancel semantics</li>
 *   <li>Tenant mismatch rejection</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionItemService — multi-item subscriptions")
class SubscriptionItemServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private SubscriptionItemRepository repository;
    @Mock
    private PlanVersionRepository planVersionRepository;

    private SubscriptionItemService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ERP_PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID HRM_PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000002");
    private static final UUID VERSION_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("e1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new SubscriptionItemService(jdbc, repository, planVersionRepository);
    }

    private SubscriptionItemEntity item(String type, UUID planId, UUID planVersionId) {
        SubscriptionItemEntity e = new SubscriptionItemEntity();
        e.setId(ITEM_ID);
        e.setTenantId(TENANT_ID);
        e.setSubscriptionId(SUBSCRIPTION_ID);
        e.setItemType(type);
        e.setPlanId(planId);
        e.setPlanVersionId(planVersionId);
        e.setQuantity(1);
        e.setCurrencyCode("SAR");
        e.setStatus("ACTIVE");
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("addItem: accepts a second PLAN item — subscription becomes multi-product")
    void addItem_allowsMultiplePlanItems() {
        when(jdbc.<UUID>queryForObject(
                eq("SELECT tenant_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(TENANT_ID);
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM saas_plans WHERE id = ?"), eq(Long.class), eq(HRM_PLAN_ID)))
                .thenReturn(1L);

        SubscriptionItemEntity created = service.addItem(SUBSCRIPTION_ID, "PLAN",
                null, null, HRM_PLAN_ID, null, 1, null, "SAR");

        assertThat(created.getItemType()).isEqualTo("PLAN");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(repository).insert(created);
    }

    @Test
    @DisplayName("addItem: PLAN item requires planId")
    void addItem_planItemRequiresPlan() {
        when(jdbc.<UUID>queryForObject(
                eq("SELECT tenant_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(TENANT_ID);

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "PLAN",
                null, null, null, null, 1, null, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planId");
    }

    @Test
    @DisplayName("addItem: rejects unknown subscription")
    void addItem_rejectsUnknownSubscription() {
        when(jdbc.<UUID>queryForObject(
                eq("SELECT tenant_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(null);

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, null, null, null, 1, 500L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscription");
    }

    @Test
    @DisplayName("addItem: rejects when tenant context does not own the subscription")
    void addItem_rejectsTenantMismatch() {
        UUID otherTenant = UUID.fromString("99999999-0000-0000-0000-000000000009");
        when(jdbc.<UUID>queryForObject(
                eq("SELECT tenant_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(otherTenant);

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "PLAN",
                null, null, HRM_PLAN_ID, null, 1, null, "SAR", TENANT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("addItem: PLAN item inherits the plan's ACTIVE version when none given")
    void addItem_pinsActivePlanVersion() {
        when(jdbc.<UUID>queryForObject(
                eq("SELECT tenant_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(TENANT_ID);
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM saas_plans WHERE id = ?"), eq(Long.class), eq(HRM_PLAN_ID)))
                .thenReturn(1L);
        PlanVersionEntity version = new PlanVersionEntity();
        version.setId(VERSION_ID);
        version.setPlanId(HRM_PLAN_ID);
        version.setVersionNumber(1);
        version.setStatus("ACTIVE");
        when(planVersionRepository.findActiveByPlanId(HRM_PLAN_ID)).thenReturn(Optional.of(version));

        SubscriptionItemEntity created = service.addItem(SUBSCRIPTION_ID, "PLAN",
                null, null, HRM_PLAN_ID, null, 1, null, "SAR");

        assertThat(created.getPlanVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    @DisplayName("cancelItem: marks item CANCELLED, never deletes")
    void cancelItem_marksCancelled() {
        SubscriptionItemEntity existing = item("ADD_ON", null, null);
        existing.setUnitAmountMinor(1500L);
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(existing));

        service.cancelItem(ITEM_ID);

        assertThat(existing.getStatus()).isEqualTo("CANCELLED");
        verify(repository).updateStatus(ITEM_ID, "CANCELLED");
    }

    @Test
    @DisplayName("effectivePlanVersionId: prefers the PLAN item's pinned version")
    void effectivePlanVersion_prefersItemPin() {
        SubscriptionItemEntity planItem = item("PLAN", ERP_PLAN_ID, VERSION_ID);
        when(repository.findActiveBySubscriptionIdAndType(SUBSCRIPTION_ID, "PLAN"))
                .thenReturn(Optional.of(planItem));

        Optional<UUID> versionId = service.effectivePlanVersionId(SUBSCRIPTION_ID);

        assertThat(versionId).contains(VERSION_ID);
    }

    @Test
    @DisplayName("effectivePlanVersionId: legacy backfill — falls back to subscription plan version")
    void effectivePlanVersion_fallsBackToSubscriptionPin() {
        when(repository.findActiveBySubscriptionIdAndType(SUBSCRIPTION_ID, "PLAN"))
                .thenReturn(Optional.empty());
        when(jdbc.<UUID>queryForObject(
                eq("SELECT plan_version_id FROM tenant_subscriptions WHERE id = ?"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(VERSION_ID);

        Optional<UUID> versionId = service.effectivePlanVersionId(SUBSCRIPTION_ID);

        assertThat(versionId).contains(VERSION_ID);
    }

    @Test
    @DisplayName("listItems: filters to ACTIVE when requested")
    void listItems_activeOnly() {
        SubscriptionItemEntity active = item("PLAN", ERP_PLAN_ID, VERSION_ID);
        SubscriptionItemEntity cancelled = item("ADD_ON", null, null);
        cancelled.setStatus("CANCELLED");
        when(repository.findBySubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(active, cancelled));

        List<SubscriptionItemEntity> result = service.listItems(SUBSCRIPTION_ID, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }
}
