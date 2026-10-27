package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidStockMirrorRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Inventory mirror maintenance. PULL-ONLY by design: the Zid stock
 * data verified for this integration lives inside the products payload
 * ({@code stocks[]} per product — verified schema), so inventory
 * synchronization is derived from the same pull as the product sync
 * and never calls any undocumented write path. No write-back to Zid is
 * performed anywhere in this integration (explicitly out of scope until
 * Zid documents and authorizes inventory write APIs for the app).
 *
 * <p>Concurrent-update protection: the UNIQUE natural key plus
 * ON CONFLICT upsert makes the mirror converge to the last committed
 * pull; every run is audited with the authoritative source (Zid pull).</p>
 */
@Service
public class ZidInventorySyncService {

    private final ZidConnectionRepository connections;
    private final ZidProductSyncService productSyncService;
    private final ZidStockMirrorRepository stockMirror;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidInventorySyncService(ZidConnectionRepository connections,
                                   ZidProductSyncService productSyncService,
                                   ZidStockMirrorRepository stockMirror,
                                   ZidSyncAuditService auditService,
                                   TenantRlsTransactionContext tenantRlsContext) {
        this.connections = connections;
        this.productSyncService = productSyncService;
        this.stockMirror = stockMirror;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record InventoryResult(int stocksMirrored, int productsProcessed, int failed, Instant completedAt) {
    }

    /** Runs the product pull (which carries the verified stocks[] data) and reports the stock mirror size. */
    @Transactional
    public InventoryResult syncInventory(UUID connectionId) {
        ZidConnectionRecord connection = connections.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());

        ZidProductSyncService.SyncResult result = productSyncService.sync(connection);
        int mirrorSize = stockMirror.countByConnection(connection.id());
        auditService.record(connection.tenantId(), connection.id(),
                ZidSyncAuditService.SyncType.INVENTORY, "product_stocks",
                ZidSyncAuditService.Direction.PULL,
                result.failed() == 0 ? ZidSyncAuditService.Status.SUCCESS : ZidSyncAuditService.Status.PARTIAL,
                result.processed(), result.failed(),
                result.failed() == 0 ? null : "inventory derived from product pull with failures", null);
        return new InventoryResult(mirrorSize, result.processed(), result.failed(), Instant.now());
    }
}
