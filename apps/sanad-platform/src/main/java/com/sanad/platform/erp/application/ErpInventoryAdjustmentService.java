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
 * Inventory Adjustment service (v20260816.7).
 *
 * <p>Tenant-scoped stock adjustments with an approval workflow:
 * <ol>
 *   <li>{@link #create(UUID, UUID, UUID, BigDecimal, String, String, Authentication)} —
 *       creates an adjustment in {@code PENDING} status.</li>
 *   <li>{@link #approve(UUID, Authentication)} — posts the adjustment
 *       atomically: creates an {@code ADJUSTMENT_IN} or {@code ADJUSTMENT_OUT}
 *       movement based on the sign of {@code quantity_delta}, applies the
 *       delta to the balance, sets status to {@code POSTED}.</li>
 *   <li>{@link #reject(UUID, Authentication)} — sets status to {@code REJECTED}
 *       (no movements applied).</li>
 * </ol>
 */
@Service
public class ErpInventoryAdjustmentService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ErpInventoryService inventoryService;

    public ErpInventoryAdjustmentService(JdbcTemplate jdbc, PlatformAuditService auditService,
                                            ErpInventoryService inventoryService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public AdjustmentResponse create(UUID tenantId, UUID warehouseId, UUID itemId,
                                      BigDecimal quantityDelta, String reasonCode, String notes,
                                      Authentication auth) {
        if (warehouseId == null || itemId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "warehouseId and itemId are required");
        if (quantityDelta == null || quantityDelta.signum() == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quantityDelta must be non-zero");
        // Validate the warehouse + item exist
        inventoryService.getOrCreateBalance(tenantId, warehouseId, itemId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String adjustmentNumber = generateNumber("ADJ", tenantId);
        try {
            jdbc.update("INSERT INTO erp_inventory_adjustments (id, tenant_id, adjustment_number, "
                            + "warehouse_id, item_id, quantity_delta, reason_code, notes, requested_by, "
                            + "approved_by, status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'PENDING', 0, ?, ?)",
                    id, tenantId, adjustmentNumber, warehouseId, itemId, quantityDelta,
                    reasonCode, notes, actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "adjustment_number collision: " + adjustmentNumber);
        }
        audit(tenantId, auth, "ADJUSTMENT.CREATED", id,
                "wh=" + warehouseId + ",item=" + itemId + ",delta=" + quantityDelta);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public AdjustmentResponse approve(UUID tenantId, UUID adjustmentId, Authentication auth) {
        AdjustmentResponse existing = getOrThrow(tenantId, adjustmentId);
        if (!"PENDING".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "adjustment cannot be approved in state " + existing.status());
        }
        BigDecimal delta = existing.quantityDelta();
        ErpDomain.MovementType type = delta.signum() >= 0
                ? ErpDomain.MovementType.ADJUSTMENT_IN
                : ErpDomain.MovementType.ADJUSTMENT_OUT;
        // Append movement + apply delta atomically
        inventoryService.appendMovement(tenantId, existing.warehouseId(), existing.itemId(),
                delta.abs(), type, "ADJUSTMENT", adjustmentId,
                "adjustment=" + adjustmentId + ",reason=" + existing.reasonCode(),
                actorUserId(auth));
        inventoryService.applyBalanceDelta(tenantId, existing.warehouseId(), existing.itemId(),
                delta, BigDecimal.ZERO, BigDecimal.ZERO);
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_inventory_adjustments SET status = 'POSTED', approved_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                actorUserId(auth), Timestamp.from(now), tenantId, adjustmentId);
        audit(tenantId, auth, "ADJUSTMENT.APPROVED", adjustmentId, "delta=" + delta);
        return getOrThrow(tenantId, adjustmentId);
    }

    @Transactional
    public AdjustmentResponse reject(UUID tenantId, UUID adjustmentId, Authentication auth) {
        AdjustmentResponse existing = getOrThrow(tenantId, adjustmentId);
        if (!"PENDING".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "adjustment cannot be rejected in state " + existing.status());
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_inventory_adjustments SET status = 'REJECTED', approved_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                actorUserId(auth), Timestamp.from(now), tenantId, adjustmentId);
        audit(tenantId, auth, "ADJUSTMENT.REJECTED", adjustmentId, "delta=" + existing.quantityDelta());
        return getOrThrow(tenantId, adjustmentId);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> list(UUID tenantId) {
        return jdbc.query("SELECT a.*, i.code AS item_code, i.name AS item_name, "
                        + "w.code AS warehouse_code FROM erp_inventory_adjustments a "
                        + "JOIN erp_items i ON i.tenant_id = a.tenant_id AND i.id = a.item_id "
                        + "JOIN erp_warehouses w ON w.tenant_id = a.tenant_id AND w.id = a.warehouse_id "
                        + "WHERE a.tenant_id = ? ORDER BY a.created_at DESC",
                this::mapRow, tenantId);
    }

    @Transactional(readOnly = true)
    public AdjustmentResponse get(UUID tenantId, UUID adjustmentId) {
        return getOrThrow(tenantId, adjustmentId);
    }

    // ===== Helpers =====
    private AdjustmentResponse getOrThrow(UUID tenantId, UUID adjustmentId) {
        try {
            return jdbc.queryForObject(
                    "SELECT a.*, i.code AS item_code, i.name AS item_name, "
                            + "w.code AS warehouse_code FROM erp_inventory_adjustments a "
                            + "JOIN erp_items i ON i.tenant_id = a.tenant_id AND i.id = a.item_id "
                            + "JOIN erp_warehouses w ON w.tenant_id = a.tenant_id AND w.id = a.warehouse_id "
                            + "WHERE a.tenant_id = ? AND a.id = ?",
                    this::mapRow, tenantId, adjustmentId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "adjustment not found: " + adjustmentId);
        }
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
        try { auditService.success(auth, tenantId, action, "ADJUSTMENT", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private AdjustmentResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdjustmentResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("adjustment_number"),
                rs.getObject("warehouse_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getString("item_code"), rs.getString("item_name"), rs.getString("warehouse_code"),
                rs.getBigDecimal("quantity_delta"),
                rs.getString("reason_code"), rs.getString("notes"),
                rs.getObject("requested_by", UUID.class), rs.getObject("approved_by", UUID.class),
                ErpDomain.AdjustmentStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
