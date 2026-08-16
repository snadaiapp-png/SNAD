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
 * Inventory Transfer service (v20260816.7).
 *
 * <p>Tenant-scoped stock transfers between warehouses:
 * <ol>
 *   <li>{@link #create(UUID, UUID, UUID, List, Authentication)} — creates the
 *       transfer in {@code DRAFT} status (no stock movements yet).</li>
 *   <li>{@link #submit(UUID, UUID, Authentication)} — validates stock
 *       availability at the source warehouse, appends TRANSFER_OUT movements,
 *       decrements source {@code on_hand}, sets status to {@code IN_TRANSIT}.</li>
 *   <li>{@link #receive(UUID, UUID, Authentication)} — appends TRANSFER_IN
 *       movements, increments destination {@code on_hand}, sets status to
 *       {@code RECEIVED}.</li>
 * </ol>
 *
 * <p>All movements are atomic via the inventory service (append + balance
 * delta in a single {@code @Transactional} boundary).
 */
@Service
public class ErpInventoryTransferService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ErpInventoryService inventoryService;

    public ErpInventoryTransferService(JdbcTemplate jdbc, PlatformAuditService auditService,
                                         ErpInventoryService inventoryService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public TransferResponse create(UUID tenantId, UUID fromWarehouseId, UUID toWarehouseId,
                                     List<CreateTransferItem> items, Authentication auth) {
        if (fromWarehouseId == null || toWarehouseId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromWarehouseId and toWarehouseId are required");
        if (fromWarehouseId.equals(toWarehouseId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "from and to warehouses must differ");
        if (items == null || items.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items cannot be empty");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String transferNumber = generateNumber("TRF", tenantId);
        try {
            jdbc.update("INSERT INTO erp_inventory_transfers (id, tenant_id, transfer_number, "
                            + "from_warehouse_id, to_warehouse_id, status, requested_by, version, "
                            + "created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, 0, ?, ?)",
                    id, tenantId, transferNumber, fromWarehouseId, toWarehouseId,
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "transfer_number collision: " + transferNumber);
        }
        for (CreateTransferItem item : items) {
            jdbc.update("INSERT INTO erp_inventory_transfer_items (id, tenant_id, transfer_id, "
                            + "item_id, quantity, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, id, item.itemId(), item.quantity(),
                    Timestamp.from(now));
        }
        audit(tenantId, auth, "TRANSFER.CREATED", id, "from=" + fromWarehouseId + ",to=" + toWarehouseId);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public TransferResponse submit(UUID tenantId, UUID transferId, Authentication auth) {
        TransferResponse existing = getOrThrow(tenantId, transferId);
        if (!"DRAFT".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "transfer cannot be submitted in state " + existing.status());
        }
        Instant now = Instant.now();
        // Validate + decrement source balances atomically
        for (TransferItemResponse item : existing.items()) {
            InventoryBalanceResponse balance = inventoryService.getBalance(
                    tenantId, existing.fromWarehouseId(), item.itemId());
            BigDecimal available = balance.available();
            if (available.compareTo(item.quantity()) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "insufficient available stock at source for item " + item.itemId()
                                + ": available=" + available + ",required=" + item.quantity());
            }
            inventoryService.appendMovement(tenantId, existing.fromWarehouseId(), item.itemId(),
                    item.quantity(), ErpDomain.MovementType.TRANSFER_OUT, "TRANSFER", transferId,
                    "transfer_out=" + transferId, actorUserId(auth));
            inventoryService.applyBalanceDelta(tenantId, existing.fromWarehouseId(), item.itemId(),
                    item.quantity().negate(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        jdbc.update("UPDATE erp_inventory_transfers SET status = 'IN_TRANSIT', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, transferId);
        audit(tenantId, auth, "TRANSFER.SUBMITTED", transferId, "items=" + existing.items().size());
        return getOrThrow(tenantId, transferId);
    }

    @Transactional
    public TransferResponse receive(UUID tenantId, UUID transferId, Authentication auth) {
        TransferResponse existing = getOrThrow(tenantId, transferId);
        if (!"IN_TRANSIT".equals(existing.status().name()) && !"SUBMITTED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "transfer cannot be received in state " + existing.status());
        }
        Instant now = Instant.now();
        // Increment destination balances atomically
        for (TransferItemResponse item : existing.items()) {
            inventoryService.getOrCreateBalance(tenantId, existing.toWarehouseId(), item.itemId());
            inventoryService.appendMovement(tenantId, existing.toWarehouseId(), item.itemId(),
                    item.quantity(), ErpDomain.MovementType.TRANSFER_IN, "TRANSFER", transferId,
                    "transfer_in=" + transferId, actorUserId(auth));
            inventoryService.applyBalanceDelta(tenantId, existing.toWarehouseId(), item.itemId(),
                    item.quantity(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        jdbc.update("UPDATE erp_inventory_transfers SET status = 'RECEIVED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, transferId);
        audit(tenantId, auth, "TRANSFER.RECEIVED", transferId, "items=" + existing.items().size());
        return getOrThrow(tenantId, transferId);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_inventory_transfers WHERE tenant_id = ? ORDER BY created_at",
                this::mapHeader, tenantId).stream().map(this::attachItems).toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID tenantId, UUID transferId) {
        return getOrThrow(tenantId, transferId);
    }

    // ===== Helpers =====
    private TransferResponse getOrThrow(UUID tenantId, UUID transferId) {
        try {
            TransferResponse header = jdbc.queryForObject(
                    "SELECT * FROM erp_inventory_transfers WHERE tenant_id = ? AND id = ?",
                    this::mapHeader, tenantId, transferId);
            return attachItems(header);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transfer not found: " + transferId);
        }
    }

    private TransferResponse attachItems(TransferResponse header) {
        if (header == null) return null;
        List<TransferItemResponse> items = jdbc.query(
                "SELECT iti.*, i.code AS item_code, i.name AS item_name "
                        + "FROM erp_inventory_transfer_items iti "
                        + "JOIN erp_items i ON i.tenant_id = iti.tenant_id AND i.id = iti.item_id "
                        + "WHERE iti.tenant_id = ? AND iti.transfer_id = ? ORDER BY iti.created_at",
                this::mapItemRow, header.tenantId(), header.id());
        String fromCode = warehouseCode(header.tenantId(), header.fromWarehouseId());
        String toCode = warehouseCode(header.tenantId(), header.toWarehouseId());
        return new TransferResponse(header.id(), header.tenantId(), header.transferNumber(),
                header.fromWarehouseId(), header.toWarehouseId(), fromCode, toCode,
                header.status(), header.requestedBy(), items, header.version(),
                header.createdAt(), header.updatedAt());
    }

    private String warehouseCode(UUID tenantId, UUID warehouseId) {
        try {
            return jdbc.queryForObject(
                    "SELECT code FROM erp_warehouses WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, warehouseId);
        } catch (EmptyResultDataAccessException e) { return null; }
    }

    private String generateNumber(String prefix, UUID tenantId) {
        // Sequential-ish: prefix + YYYYMMDD + last-4-UUID hex (uppercased)
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
        try { auditService.success(auth, tenantId, action, "TRANSFER", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private TransferResponse mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new TransferResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("transfer_number"),
                rs.getObject("from_warehouse_id", UUID.class),
                rs.getObject("to_warehouse_id", UUID.class),
                null, null, // warehouse codes resolved in attachItems
                ErpDomain.TransferStatus.valueOf(rs.getString("status")),
                rs.getObject("requested_by", UUID.class),
                List.of(), // items filled in by attachItems
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private TransferItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new TransferItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("transfer_id", UUID.class),
                rs.getObject("item_id", UUID.class),
                rs.getString("item_code"), rs.getString("item_name"),
                rs.getBigDecimal("quantity"),
                rs.getObject("created_at", Timestamp.class).toInstant());
    }
}
