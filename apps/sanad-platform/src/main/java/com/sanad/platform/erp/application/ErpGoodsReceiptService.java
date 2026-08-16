package com.sanad.platform.erp.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.erp.api.ErpDtos.*;
import com.sanad.platform.erp.domain.ErpDomain;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Goods Receipt application service (v20260816.7).
 *
 * <p>Tenant-scoped goods-receipt lifecycle:
 * <ol>
 *   <li>{@link #create(UUID, CreateGoodsReceiptRequest, Authentication)} —
 *       creates a goods receipt in {@code DRAFT} status, optionally linked
 *       to a purchase order.</li>
 *   <li>{@link #post(UUID, UUID, Authentication)} — <b>atomic</b> posting:
 *       validates the PO exists (if linked); validates that no over-receipt
 *       occurs (received_quantity + new quantity ≤ ordered quantity, unless
 *       the system property {@code sanad.erp.allowOverReceipt=true});
 *       appends RECEIPT movements, increments {@code erp_inventory_balances.on_hand},
 *       updates {@code erp_purchase_order_items.received_quantity} +
 *       the PO status (PARTIALLY_RECEIVED or RECEIVED), and sets the goods
 *       receipt to POSTED.</li>
 * </ol>
 */
@Service
public class ErpGoodsReceiptService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ErpInventoryService inventoryService;
    private final ErpWarehouseService warehouseService;
    private final ErpPurchaseOrderService purchaseOrderService;

    public ErpGoodsReceiptService(JdbcTemplate jdbc, PlatformAuditService auditService,
                                    ErpInventoryService inventoryService,
                                    ErpWarehouseService warehouseService,
                                    ErpPurchaseOrderService purchaseOrderService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Transactional
    public GoodsReceiptResponse create(UUID tenantId, CreateGoodsReceiptRequest request, Authentication auth) {
        if (request == null || request.warehouseId() == null || request.items() == null
                || request.items().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "warehouseId and items are required");
        warehouseService.getOrThrow(tenantId, request.warehouseId());
        if (request.poId() != null) {
            purchaseOrderService.getOrThrow(tenantId, request.poId());
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String receiptNumber = generateNumber("GR", tenantId);
        try {
            jdbc.update("INSERT INTO erp_goods_receipts (id, tenant_id, receipt_number, po_id, "
                            + "warehouse_id, status, received_by, posted_at, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, NULL, 0, ?, ?)",
                    id, tenantId, receiptNumber, request.poId(), request.warehouseId(),
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "receipt_number collision: " + receiptNumber);
        }
        for (CreateGoodsReceiptItem item : request.items()) {
            jdbc.update("INSERT INTO erp_goods_receipt_items (id, tenant_id, receipt_id, po_item_id, "
                            + "item_id, quantity, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, id, item.poItemId(), item.itemId(),
                    item.quantity(), Timestamp.from(now));
        }
        audit(tenantId, auth, "GOODS_RECEIPT.CREATED", id, "po=" + request.poId());
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public GoodsReceiptResponse post(UUID tenantId, UUID receiptId, Authentication auth) {
        GoodsReceiptResponse existing = getOrThrow(tenantId, receiptId);
        if (!"DRAFT".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "goods receipt cannot be posted in state " + existing.status());
        }
        Instant now = Instant.now();
        // Atomic: append RECEIPT movements + bump balances + update PO line items + PO status
        for (GoodsReceiptItemResponse item : existing.items()) {
            BigDecimal qty = item.quantity();
            if (item.poItemId() != null) {
                // Validate against PO ordered quantity (no over-receipt unless allowed)
                if (!allowOverReceipt()) {
                    BigDecimal ordered = BigDecimal.ZERO;
                    BigDecimal alreadyReceived = BigDecimal.ZERO;
                    try {
                        ordered = jdbc.queryForObject(
                                "SELECT quantity FROM erp_purchase_order_items WHERE tenant_id = ? AND id = ?",
                                BigDecimal.class, tenantId, item.poItemId());
                        alreadyReceived = jdbc.queryForObject(
                                "SELECT received_quantity FROM erp_purchase_order_items WHERE tenant_id = ? AND id = ?",
                                BigDecimal.class, tenantId, item.poItemId());
                    } catch (EmptyResultDataAccessException e) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "PO item not found: " + item.poItemId());
                    }
                    if (ordered == null) ordered = BigDecimal.ZERO;
                    if (alreadyReceived == null) alreadyReceived = BigDecimal.ZERO;
                    if (alreadyReceived.add(qty).compareTo(ordered) > 0) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "over-receipt not allowed for PO item " + item.poItemId()
                                        + ": ordered=" + ordered + ",already=" + alreadyReceived + ",new=" + qty);
                    }
                }
            }
            // Append RECEIPT movement + increment balance atomically
            inventoryService.appendMovement(tenantId, existing.warehouseId(), item.itemId(),
                    qty, ErpDomain.MovementType.RECEIPT, "GOODS_RECEIPT", receiptId,
                    "goods_receipt=" + receiptId, actorUserId(auth));
            inventoryService.applyBalanceDelta(tenantId, existing.warehouseId(), item.itemId(),
                    qty, BigDecimal.ZERO, BigDecimal.ZERO);
            // Update PO line received_quantity + PO status
            if (item.poItemId() != null && existing.poId() != null) {
                purchaseOrderService.recordReceipt(tenantId, existing.poId(), item.poItemId(),
                        qty, auth);
            }
        }
        // Mark receipt POSTED
        jdbc.update("UPDATE erp_goods_receipts SET status = 'POSTED', posted_at = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), Timestamp.from(now), tenantId, receiptId);
        audit(tenantId, auth, "GOODS_RECEIPT.POSTED", receiptId,
                "items=" + existing.items().size());
        return getOrThrow(tenantId, receiptId);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_goods_receipts WHERE tenant_id = ? ORDER BY created_at DESC",
                        this::mapHeader, tenantId)
                .stream().map(this::attachItems).toList();
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(UUID tenantId, UUID receiptId) {
        return getOrThrow(tenantId, receiptId);
    }

    // ===== Helpers =====
    private boolean allowOverReceipt() {
        return Boolean.getBoolean("sanad.erp.allowOverReceipt");
    }

    private GoodsReceiptResponse getOrThrow(UUID tenantId, UUID receiptId) {
        try {
            GoodsReceiptResponse header = jdbc.queryForObject(
                    "SELECT * FROM erp_goods_receipts WHERE tenant_id = ? AND id = ?",
                    this::mapHeader, tenantId, receiptId);
            return attachItems(header);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "goods receipt not found: " + receiptId);
        }
    }

    private GoodsReceiptResponse attachItems(GoodsReceiptResponse header) {
        if (header == null) return null;
        List<GoodsReceiptItemResponse> items = jdbc.query(
                "SELECT gri.*, i.code AS item_code, i.name AS item_name "
                        + "FROM erp_goods_receipt_items gri "
                        + "JOIN erp_items i ON i.tenant_id = gri.tenant_id AND i.id = gri.item_id "
                        + "WHERE gri.tenant_id = ? AND gri.receipt_id = ? ORDER BY gri.created_at",
                this::mapItemRow, header.tenantId(), header.id());
        String warehouseCode = null;
        try {
            warehouseCode = jdbc.queryForObject(
                    "SELECT code FROM erp_warehouses WHERE tenant_id = ? AND id = ?",
                    String.class, header.tenantId(), header.warehouseId());
        } catch (EmptyResultDataAccessException ignored) {}
        return new GoodsReceiptResponse(header.id(), header.tenantId(), header.receiptNumber(),
                header.poId(), header.warehouseId(), warehouseCode, header.status(),
                header.receivedBy(), header.postedAt(), items, header.version(),
                header.createdAt(), header.updatedAt());
    }

    private String generateNumber(String prefix, UUID tenantId) {
        String date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString().replace("-", "");
        String suffix = Integer.toHexString(Math.abs(tenantId.hashCode())).toUpperCase();
        if (suffix.length() > 4) suffix = suffix.substring(0, 4);
        while (suffix.length() < 4) suffix = "0" + suffix;
        return prefix + "-" + date + "-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "GOODS_RECEIPT", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private GoodsReceiptResponse mapHeader(ResultSet rs, int rowNum) throws SQLException {
        Timestamp postedAt = rs.getObject("posted_at", Timestamp.class);
        return new GoodsReceiptResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("receipt_number"),
                rs.getObject("po_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                null, // warehouse code resolved in attachItems
                ErpDomain.GoodsReceiptStatus.valueOf(rs.getString("status")),
                rs.getObject("received_by", UUID.class),
                postedAt == null ? null : postedAt.toInstant(),
                List.of(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private GoodsReceiptItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new GoodsReceiptItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("receipt_id", UUID.class),
                rs.getObject("po_item_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getString("item_code"), rs.getString("item_name"),
                rs.getBigDecimal("quantity"),
                rs.getObject("created_at", Timestamp.class).toInstant());
    }
}
