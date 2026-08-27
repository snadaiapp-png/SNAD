package com.sanad.platform.erp;

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
        var a = createStock(tenantA, "A");
        var b = createStock(tenantB, "B");

        inventoryService.adjustStock(tenantA, a.warehouseId(), a.itemId(), new BigDecimal("20"),
                ErpDomain.MovementType.RECEIPT, "tenant-a", null, null);
        inventoryService.adjustStock(tenantB, b.warehouseId(), b.itemId(), new BigDecimal("30"),
                ErpDomain.MovementType.RECEIPT, "tenant-b", null, null);
        reservationService.reserve(tenantA, a.warehouseId(), a.itemId(), new BigDecimal("2"), "TEST", "A-RES", null);
        reservationService.reserve(tenantB, b.warehouseId(), b.itemId(), new BigDecimal("3"), "TEST", "B-RES", null);

        var aMovements = inventoryService.listMovements(tenantA);
        var bMovements = inventoryService.listMovements(tenantB);
        var aReservations = reservationService.list(tenantA);
        var bReservations = reservationService.list(tenantB);

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

    private UUID createTenant(String suffix) {
        UUID tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "ERP Read " + suffix, "erp-read-" + suffix + "-" + tenantId.toString().substring(0, 8), now, now);
        return tenantId;
    }

    private record StockIds(UUID warehouseId, UUID itemId) {}
}
