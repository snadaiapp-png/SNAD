package com.sanad.platform.erp;

import com.sanad.platform.crm.test.RlsTestSupport;
import com.sanad.platform.erp.api.ErpDtos.CreateItemRequest;
import com.sanad.platform.erp.api.ErpDtos.CreateWarehouseRequest;
import com.sanad.platform.erp.application.ErpInventoryReservationService;
import com.sanad.platform.erp.application.ErpInventoryService;
import com.sanad.platform.erp.application.ErpItemService;
import com.sanad.platform.erp.application.ErpWarehouseService;
import com.sanad.platform.erp.domain.ErpDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ErpInventoryReadIsolationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ErpItemService itemService;
    @Autowired private ErpWarehouseService warehouseService;
    @Autowired private ErpInventoryService inventoryService;
    @Autowired private ErpInventoryReservationService reservationService;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = createTenant("a");
        tenantB = createTenant("b");
    }

    @Test
    void movementAndReservationReadsNeverCrossTenantBoundary() {
        // V20261029_1 FORCE-RLS: each tenant's service calls run under that
        // tenant's SecurityContext binding (TenantRlsConnectionHandler applies
        // SET LOCAL app.tenant_id in every Spring-managed service transaction);
        // fail-closed predicate unchanged, not a bypass. Tenant A and B stay
        // strictly isolated — neither side is ever bound to the other.
        var a = withTenantResult(tenantA, () -> createStock(tenantA, "A"));
        var b = withTenantResult(tenantB, () -> createStock(tenantB, "B"));

        withTenant(tenantA, () -> inventoryService.adjustStock(tenantA, a.warehouseId(), a.itemId(), new BigDecimal("20"),
                ErpDomain.MovementType.RECEIPT, "tenant-a", null, null));
        withTenant(tenantB, () -> inventoryService.adjustStock(tenantB, b.warehouseId(), b.itemId(), new BigDecimal("30"),
                ErpDomain.MovementType.RECEIPT, "tenant-b", null, null));
        withTenant(tenantA, () -> reservationService.reserve(tenantA, a.warehouseId(), a.itemId(), new BigDecimal("2"), "TEST", "A-RES", null));
        withTenant(tenantB, () -> reservationService.reserve(tenantB, b.warehouseId(), b.itemId(), new BigDecimal("3"), "TEST", "B-RES", null));

        var aMovements = withTenantResult(tenantA, () -> inventoryService.listMovements(tenantA));
        var bMovements = withTenantResult(tenantB, () -> inventoryService.listMovements(tenantB));
        var aReservations = withTenantResult(tenantA, () -> reservationService.list(tenantA));
        var bReservations = withTenantResult(tenantB, () -> reservationService.list(tenantB));

        assertThat(aMovements).isNotEmpty().allMatch(row -> tenantA.equals(row.tenantId()));
        assertThat(bMovements).isNotEmpty().allMatch(row -> tenantB.equals(row.tenantId()));
        assertThat(aMovements).noneMatch(row -> tenantB.equals(row.tenantId()));
        assertThat(bMovements).noneMatch(row -> tenantA.equals(row.tenantId()));
        assertThat(aReservations).hasSize(1).allMatch(row -> tenantA.equals(row.tenantId()));
        assertThat(bReservations).hasSize(1).allMatch(row -> tenantB.equals(row.tenantId()));
    }

    private StockIds createStock(UUID tenantId, String suffix) {
        var warehouse = warehouseService.create(tenantId,
                new CreateWarehouseRequest("WH-" + suffix, "Warehouse " + suffix, "Jeddah", false), null);
        var item = itemService.create(tenantId,
                new CreateItemRequest("ITEM-" + suffix, "SKU-" + suffix, "Item " + suffix, null,
                        ErpDomain.ItemType.GOODS, ErpDomain.UnitOfMeasure.EACH, true,
                        BigDecimal.ZERO, BigDecimal.ZERO), null);
        itemService.activate(tenantId, item.id(), null);
        return new StockIds(warehouse.id(), item.id());
    }

    /**
     * Runs tenant-scoped service writes with the SecurityContext identity that
     * {@code TenantRlsConnectionHandler} needs to apply
     * {@code SET LOCAL app.tenant_id} inside {@code @Transactional} service
     * boundaries — erp_* tables are FORCE RLS (V20261029_1), so every
     * service write without this context is rejected fail-closed.
     */
    private void withTenant(UUID tenantId, Runnable action) {
        RlsTestSupport.setSecurityContext(tenantId, UUID.randomUUID());
        try {
            action.run();
        } finally {
            RlsTestSupport.clearSecurityContext();
        }
    }

    private <T> T withTenantResult(UUID tenantId, java.util.function.Supplier<T> action) {
        RlsTestSupport.setSecurityContext(tenantId, UUID.randomUUID());
        try {
            return action.get();
        } finally {
            RlsTestSupport.clearSecurityContext();
        }
    }

    private UUID createTenant(String suffix) {
        UUID tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "ERP Read " + suffix, "erp-read-" + suffix + "-" + tenantId.toString().substring(0, 8), now, now);
        return tenantId;
    }

    private record StockIds(UUID warehouseId, UUID itemId) {}
}
