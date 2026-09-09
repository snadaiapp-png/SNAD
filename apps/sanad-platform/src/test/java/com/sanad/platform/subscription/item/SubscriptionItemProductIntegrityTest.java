package com.sanad.platform.subscription.item;

import com.sanad.platform.subscription.catalog.ProductEntity;
import com.sanad.platform.subscription.catalog.ProductRepository;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R0C-11 — Subscription item product integrity.
 *
 * <p>For NEW product-backed items the referenced product must exist and be
 * type-compatible and ACTIVE where the contract requires it (ADD_ON → active
 * ADD_ON product; METERED → active METERED product). Historical references
 * survive later INACTIVE/ARCHIVED transitions: existing items are never
 * cancelled or mutated, and the recorded name snapshot stays.</p>
 *
 * <p>R0C-5/R0C-10 semantics are preserved exactly: PLAN anchor rules,
 * quantity rules and multiplicity behavior are untouched (validation fires
 * only for product-backed items).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionItemService — R0C-11 product-backed item integrity")
class SubscriptionItemProductIntegrityTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private SubscriptionItemRepository repository;
    @Mock
    private PlanVersionRepository planVersionRepository;
    @Mock
    private ProductRepository productRepository;

    private SubscriptionItemService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ITEM_ID = UUID.fromString("e1000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new SubscriptionItemService(jdbc, repository, planVersionRepository,
                productRepository);
        lenient().when(jdbc.queryForObject(contains("FROM tenant_subscriptions"),
                eq(UUID.class), eq(SUBSCRIPTION_ID))).thenReturn(TENANT_ID);
        lenient().when(jdbc.queryForObject(contains("FROM products"),
                eq(String.class), eq(PRODUCT_ID))).thenReturn("HRM Add-On Pack");
    }

    private ProductEntity product(String type, String status) {
        ProductEntity e = new ProductEntity();
        e.setId(PRODUCT_ID);
        e.setCode("HRM_ADDON");
        e.setName("HRM Add-On Pack");
        e.setProductType(type);
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    // ---------------------------------------------------------------
    // existence
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ADD_ON item with unknown product is rejected fail-closed")
    void addOn_unknownProductRejected() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product");
        verify(repository, never()).insert(any(SubscriptionItemEntity.class));
    }

    // ---------------------------------------------------------------
    // type compatibility
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ADD_ON item referencing an APPLICATION product is rejected")
    void addOn_incompatibleTypeRejected() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("APPLICATION", "ACTIVE")));

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADD_ON");
        verify(repository, never()).insert(any(SubscriptionItemEntity.class));
    }

    @Test
    @DisplayName("ADD_ON item referencing a METERED product is rejected")
    void addOn_meteredTypeRejected() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("METERED", "ACTIVE")));

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("METERED item referencing an ADD_ON product is rejected")
    void metered_incompatibleTypeRejected() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("ADD_ON", "ACTIVE")));

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "METERED",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("METERED");
    }

    // ---------------------------------------------------------------
    // active contract
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ADD_ON item referencing an INACTIVE ADD_ON product is rejected as a new sale")
    void addOn_inactiveRejected() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("ADD_ON", "INACTIVE")));

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("METERED item referencing an ARCHIVED METERED product is rejected as a new sale")
    void metered_archivedRejected() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("METERED", "ARCHIVED")));

        assertThatThrownBy(() -> service.addItem(SUBSCRIPTION_ID, "METERED",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    // ---------------------------------------------------------------
    // valid attachments
    // ---------------------------------------------------------------

    @Test
    @DisplayName("valid ADD_ON attachment succeeds and snapshots the product name")
    void addOn_validAttachment() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("ADD_ON", "ACTIVE")));

        SubscriptionItemEntity item = service.addItem(SUBSCRIPTION_ID, "ADD_ON",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR");

        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getNameSnapshot()).isEqualTo("HRM Add-On Pack");
        assertThat(item.getStatus()).isEqualTo("ACTIVE");
        verify(repository).insert(item);
    }

    @Test
    @DisplayName("valid METERED attachment succeeds")
    void metered_validAttachment() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("METERED", "ACTIVE")));

        SubscriptionItemEntity item = service.addItem(SUBSCRIPTION_ID, "METERED",
                null, PRODUCT_ID, null, null, 1, 100L, "SAR");

        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        verify(repository).insert(item);
    }

    // ---------------------------------------------------------------
    // historical preservation + unaffected paths
    // ---------------------------------------------------------------

    @Test
    @DisplayName("historical references survive: list/cancel are unaffected by later INACTIVE")
    void historicalReferencesPreserved() {
        SubscriptionItemEntity historical = new SubscriptionItemEntity();
        historical.setId(ITEM_ID);
        historical.setTenantId(TENANT_ID);
        historical.setSubscriptionId(SUBSCRIPTION_ID);
        historical.setItemType("ADD_ON");
        historical.setProductId(PRODUCT_ID);
        historical.setNameSnapshot("HRM Add-On Pack");
        historical.setQuantity(1);
        historical.setCurrencyCode("SAR");
        historical.setStatus("ACTIVE");
        historical.setCreatedAt(Instant.now());
        historical.setUpdatedAt(Instant.now());
        when(repository.findBySubscriptionId(SUBSCRIPTION_ID)).thenReturn(List.of(historical));
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(historical));

        // The product later goes INACTIVE/ARCHIVED — no runtime path may
        // cancel, delete, or "fix" the historical item.
        List<SubscriptionItemEntity> items = service.listItems(SUBSCRIPTION_ID, true);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(PRODUCT_ID);

        service.cancelItem(ITEM_ID);
        assertThat(historical.getStatus()).isEqualTo("CANCELLED");

        // no product-driven mutation of historical rows exists — the
        // list/cancel paths never consult the product catalog at all
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("PLAN anchor semantics preserved: PLAN item ignores productId validation")
    void planAnchorSemanticsPreserved() {
        when(jdbc.queryForObject(contains("FROM saas_plans"), eq(Long.class),
                eq(PLAN_ID))).thenReturn(1L);
        when(jdbc.queryForObject(contains("FROM saas_plans"), eq(String.class),
                eq(PLAN_ID))).thenReturn("ERP Plan");
        when(planVersionRepository.findActiveByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        SubscriptionItemEntity item = service.addItem(SUBSCRIPTION_ID, "PLAN",
                null, null, PLAN_ID, null, 5, 1000L, "SAR");

        assertThat(item.getItemType()).isEqualTo("PLAN");
        assertThat(item.getPlanId()).isEqualTo(PLAN_ID);
        verify(repository).insert(item);
    }

}
