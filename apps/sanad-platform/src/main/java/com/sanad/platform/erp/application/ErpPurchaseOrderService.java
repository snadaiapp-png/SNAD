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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Purchase Order application service (v20260816.7).
 *
 * <p>Tenant-scoped PO lifecycle:
 * <ol>
 *   <li>{@link #create(UUID, CreatePurchaseOrderRequest, Authentication)} —
 *       creates a PO in {@code DRAFT} with line items; calculates
 *       {@code subtotal}, {@code tax_total} (zero — finance module owns tax),
 *       and {@code total}. Can be linked to an approved requisition via
 *       {@code requisitionId} — the requisition is marked {@code CONVERTED}
 *       upon PO creation.</li>
 *   <li>{@link #submit(UUID, UUID, Authentication)} — DRAFT → SUBMITTED.</li>
 *   <li>{@link #approve(UUID, UUID, Authentication)} — SUBMITTED → APPROVED.</li>
 *   <li>{@link #cancel(UUID, UUID, Authentication)} — non-terminal → CANCELLED.</li>
 *   <li>{@link #close(UUID, UUID, Authentication)} — APPROVED/RECEIVED → CLOSED.</li>
 * </ol>
 */
@Service
public class ErpPurchaseOrderService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ErpSupplierService supplierService;
    private final ErpPurchaseRequisitionService requisitionService;

    public ErpPurchaseOrderService(JdbcTemplate jdbc, PlatformAuditService auditService,
                                     ErpSupplierService supplierService,
                                     ErpPurchaseRequisitionService requisitionService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.supplierService = supplierService;
        this.requisitionService = requisitionService;
    }

    @Transactional
    public PurchaseOrderResponse create(UUID tenantId, CreatePurchaseOrderRequest request, Authentication auth) {
        if (request == null || request.items() == null || request.items().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items cannot be empty");
        // Validate supplier (if provided)
        String supplierName = null;
        if (request.supplierId() != null) {
            SupplierResponse supplier = supplierService.getOrThrow(tenantId, request.supplierId());
            supplierName = supplier.name();
        }
        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency() : "SAR";
        // Compute subtotal from items
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreatePurchaseOrderItem item : request.items()) {
            if (item.quantity() == null || item.unitCost() == null
                    || item.quantity().signum() <= 0 || item.unitCost().signum() < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "item requires positive quantity and non-negative unitCost");
            subtotal = subtotal.add(item.quantity().multiply(item.unitCost()));
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(taxTotal);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String poNumber = generateNumber("PO", tenantId);
        try {
            jdbc.update("INSERT INTO erp_purchase_orders (id, tenant_id, po_number, supplier_id, "
                            + "currency, status, subtotal, tax_total, total, expected_date, created_by, "
                            + "approved_by, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, NULL, 0, ?, ?)",
                    id, tenantId, poNumber, request.supplierId(), currency,
                    subtotal, taxTotal, total,
                    request.expectedDate() != null ? java.sql.Date.valueOf(request.expectedDate()) : null,
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "po_number collision: " + poNumber);
        }
        // Insert items + compute line totals
        for (CreatePurchaseOrderItem item : request.items()) {
            BigDecimal lineTotal = item.quantity().multiply(item.unitCost());
            jdbc.update("INSERT INTO erp_purchase_order_items (id, tenant_id, po_id, item_id, "
                            + "quantity, unit_cost, received_quantity, line_total, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    UUID.randomUUID(), tenantId, id, item.itemId(),
                    item.quantity(), item.unitCost(), lineTotal, Timestamp.from(now));
        }
        // Link to approved requisition (convert it)
        if (request.requisitionId() != null) {
            try {
                requisitionService.markConverted(tenantId, request.requisitionId(), auth);
            } catch (ResponseStatusException e) {
                // propagate — caller sees the conflict
                throw e;
            }
        }
        audit(tenantId, auth, "PURCHASE_ORDER.CREATED", id,
                "po=" + poNumber + ",supplier=" + request.supplierId());
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public PurchaseOrderResponse submit(UUID tenantId, UUID poId, Authentication auth) {
        PurchaseOrderResponse existing = getOrThrow(tenantId, poId);
        if (!"DRAFT".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PO cannot be submitted in state " + existing.status());
        }
        transition(tenantId, poId, "SUBMITTED");
        audit(tenantId, auth, "PURCHASE_ORDER.SUBMITTED", poId, "supplier=" + existing.supplierId());
        return getOrThrow(tenantId, poId);
    }

    @Transactional
    public PurchaseOrderResponse approve(UUID tenantId, UUID poId, Authentication auth) {
        PurchaseOrderResponse existing = getOrThrow(tenantId, poId);
        if (!"SUBMITTED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PO cannot be approved in state " + existing.status());
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_purchase_orders SET status = 'APPROVED', approved_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                actorUserId(auth), Timestamp.from(now), tenantId, poId);
        audit(tenantId, auth, "PURCHASE_ORDER.APPROVED", poId, "total=" + existing.total());
        return getOrThrow(tenantId, poId);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID tenantId, UUID poId, Authentication auth) {
        PurchaseOrderResponse existing = getOrThrow(tenantId, poId);
        String status = existing.status().name();
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PO cannot be cancelled in state " + existing.status());
        }
        transition(tenantId, poId, "CANCELLED");
        audit(tenantId, auth, "PURCHASE_ORDER.CANCELLED", poId, "from=" + status);
        return getOrThrow(tenantId, poId);
    }

    @Transactional
    public PurchaseOrderResponse close(UUID tenantId, UUID poId, Authentication auth) {
        PurchaseOrderResponse existing = getOrThrow(tenantId, poId);
        String status = existing.status().name();
        if (!"APPROVED".equals(status) && !"RECEIVED".equals(status)
                && !"PARTIALLY_RECEIVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PO cannot be closed in state " + existing.status());
        }
        transition(tenantId, poId, "CLOSED");
        audit(tenantId, auth, "PURCHASE_ORDER.CLOSED", poId, "from=" + status);
        return getOrThrow(tenantId, poId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_purchase_orders WHERE tenant_id = ? ORDER BY created_at DESC",
                        this::mapHeader, tenantId)
                .stream().map(this::attachItems).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID tenantId, UUID poId) {
        return getOrThrow(tenantId, poId);
    }

    /** Used by goods receipt service to update received_quantity + status. */
    @Transactional
    public void recordReceipt(UUID tenantId, UUID poId, UUID poItemId, BigDecimal quantityReceived,
                               Authentication auth) {
        jdbc.update("UPDATE erp_purchase_order_items SET received_quantity = received_quantity + ? "
                        + "WHERE tenant_id = ? AND po_id = ? AND id = ?",
                quantityReceived, tenantId, poId, poItemId);
        // Recompute PO status based on received vs ordered
        BigDecimal totalOrdered = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM erp_purchase_order_items WHERE tenant_id = ? AND po_id = ?",
                BigDecimal.class, tenantId, poId);
        BigDecimal totalReceived = jdbc.queryForObject(
                "SELECT COALESCE(SUM(received_quantity), 0) FROM erp_purchase_order_items WHERE tenant_id = ? AND po_id = ?",
                BigDecimal.class, tenantId, poId);
        if (totalOrdered != null && totalReceived != null) {
            String newStatus;
            if (totalReceived.compareTo(totalOrdered) >= 0) newStatus = "RECEIVED";
            else if (totalReceived.signum() > 0) newStatus = "PARTIALLY_RECEIVED";
            else newStatus = null;
            if (newStatus != null) {
                jdbc.update("UPDATE erp_purchase_orders SET status = ?, updated_at = ?, version = version + 1 "
                                + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(Instant.now()),
                        tenantId, poId);
            }
        }
        audit(tenantId, auth, "PURCHASE_ORDER.RECEIPT_RECORDED", poId,
                "poItem=" + poItemId + ",qty=" + quantityReceived);
    }

    // ===== Helpers =====
    PurchaseOrderResponse getOrThrow(UUID tenantId, UUID poId) {
        try {
            PurchaseOrderResponse header = jdbc.queryForObject(
                    "SELECT * FROM erp_purchase_orders WHERE tenant_id = ? AND id = ?",
                    this::mapHeader, tenantId, poId);
            return attachItems(header);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "purchase order not found: " + poId);
        }
    }

    private PurchaseOrderResponse attachItems(PurchaseOrderResponse header) {
        if (header == null) return null;
        List<PurchaseOrderItemResponse> items = jdbc.query(
                "SELECT poi.*, i.code AS item_code, i.name AS item_name "
                        + "FROM erp_purchase_order_items poi "
                        + "JOIN erp_items i ON i.tenant_id = poi.tenant_id AND i.id = poi.item_id "
                        + "WHERE poi.tenant_id = ? AND poi.po_id = ? ORDER BY poi.created_at",
                this::mapItemRow, header.tenantId(), header.id());
        String supplierName = null;
        if (header.supplierId() != null) {
            try {
                supplierName = jdbc.queryForObject(
                        "SELECT name FROM erp_suppliers WHERE tenant_id = ? AND id = ?",
                        String.class, header.tenantId(), header.supplierId());
            } catch (EmptyResultDataAccessException ignored) {}
        }
        return new PurchaseOrderResponse(header.id(), header.tenantId(), header.poNumber(),
                header.supplierId(), supplierName, header.currency(), header.status(),
                header.subtotal(), header.taxTotal(), header.total(), header.expectedDate(),
                header.createdBy(), header.approvedBy(), items, header.version(),
                header.createdAt(), header.updatedAt());
    }

    private void transition(UUID tenantId, UUID poId, String newStatus) {
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_purchase_orders SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, poId);
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
        try { auditService.success(auth, tenantId, action, "PURCHASE_ORDER", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private PurchaseOrderResponse mapHeader(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date ed = rs.getObject("expected_date", java.sql.Date.class);
        return new PurchaseOrderResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("po_number"),
                rs.getObject("supplier_id", UUID.class),
                null, // supplier name resolved in attachItems
                rs.getString("currency"),
                ErpDomain.PurchaseOrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"),
                rs.getBigDecimal("total"),
                ed == null ? null : ed.toLocalDate(),
                rs.getObject("created_by", UUID.class),
                rs.getObject("approved_by", UUID.class),
                List.of(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private PurchaseOrderItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new PurchaseOrderItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("po_id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("received_quantity"), rs.getBigDecimal("line_total"),
                rs.getObject("created_at", Timestamp.class).toInstant());
    }
}
