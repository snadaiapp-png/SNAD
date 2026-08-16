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
 * Item application service (v20260816.7).
 *
 * <p>Tenant-scoped CRUD + lifecycle (activate / inactivate / archive) +
 * low-stock listing for {@code erp_items}. Mirrors the {@code WebsiteService}
 * patterns: JdbcTemplate-based, audited via {@link PlatformAuditService},
 * {@code @Transactional} on mutations, optimistic version via {@code version+1}.
 */
@Service
public class ErpItemService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public ErpItemService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public ItemResponse create(UUID tenantId, CreateItemRequest request, Authentication auth) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (request.code() == null || request.code().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        String sku = request.sku();
        ErpDomain.ItemType type = request.itemType() != null ? request.itemType() : ErpDomain.ItemType.GOODS;
        ErpDomain.UnitOfMeasure uom = request.unitOfMeasure() != null
                ? request.unitOfMeasure() : ErpDomain.UnitOfMeasure.EACH;
        boolean trackInv = request.trackInventory() == null || request.trackInventory();
        BigDecimal reorderLevel = request.reorderLevel() != null ? request.reorderLevel() : BigDecimal.ZERO;
        BigDecimal reorderQty = request.reorderQuantity() != null ? request.reorderQuantity() : BigDecimal.ZERO;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO erp_items (id, tenant_id, code, sku, name, description, item_type, "
                            + "unit_of_measure, status, track_inventory, reorder_level, reorder_quantity, "
                            + "version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, 0, ?, ?, ?)",
                    id, tenantId, request.code().trim(), sku, request.name().trim(),
                    request.description(), type.name(), uom.name(), trackInv, reorderLevel, reorderQty,
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "item code already exists for this tenant: " + request.code());
        }
        audit(tenantId, auth, "ITEM.CREATED", id, "code=" + request.code());
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public ItemResponse update(UUID tenantId, UUID itemId, UpdateItemRequest request, Authentication auth) {
        ItemResponse existing = getOrThrow(tenantId, itemId);
        if (request.expectedVersion() != null && request.expectedVersion() != existing.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "version conflict: expected " + request.expectedVersion() + " but was " + existing.version());
        }
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE erp_items SET name = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.name().trim(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.sku() != null) {
            jdbc.update("UPDATE erp_items SET sku = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.sku(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.description() != null) {
            jdbc.update("UPDATE erp_items SET description = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.description(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.itemType() != null) {
            jdbc.update("UPDATE erp_items SET item_type = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.itemType().name(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.unitOfMeasure() != null) {
            jdbc.update("UPDATE erp_items SET unit_of_measure = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.unitOfMeasure().name(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.trackInventory() != null) {
            jdbc.update("UPDATE erp_items SET track_inventory = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.trackInventory(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.reorderLevel() != null) {
            jdbc.update("UPDATE erp_items SET reorder_level = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.reorderLevel(), Timestamp.from(now), tenantId, itemId);
        }
        if (request.reorderQuantity() != null) {
            jdbc.update("UPDATE erp_items SET reorder_quantity = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.reorderQuantity(), Timestamp.from(now), tenantId, itemId);
        }
        audit(tenantId, auth, "ITEM.UPDATED", itemId, "name=" + existing.name());
        return getOrThrow(tenantId, itemId);
    }

    @Transactional(readOnly = true)
    public ItemResponse get(UUID tenantId, UUID itemId) {
        return getOrThrow(tenantId, itemId);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_items WHERE tenant_id = ? ORDER BY created_at",
                this::mapRow, tenantId);
    }

    @Transactional
    public ItemResponse activate(UUID tenantId, UUID itemId, Authentication auth) {
        return transition(tenantId, itemId, "ACTIVE", "ITEM.ACTIVATED", auth);
    }

    @Transactional
    public ItemResponse inactivate(UUID tenantId, UUID itemId, Authentication auth) {
        return transition(tenantId, itemId, "INACTIVE", "ITEM.INACTIVATED", auth);
    }

    @Transactional
    public ItemResponse archive(UUID tenantId, UUID itemId, Authentication auth) {
        return transition(tenantId, itemId, "ARCHIVED", "ITEM.ARCHIVED", auth);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> getLowStockItems(UUID tenantId) {
        return jdbc.query("SELECT i.* FROM erp_items i "
                        + "JOIN erp_inventory_balances b ON b.tenant_id = i.tenant_id AND b.item_id = i.id "
                        + "WHERE i.tenant_id = ? AND i.track_inventory = TRUE "
                        + "AND i.reorder_level > 0 "
                        + "AND (b.on_hand - b.reserved) <= i.reorder_level "
                        + "ORDER BY i.code",
                this::mapRow, tenantId);
    }

    // ===== Helpers =====
    ItemResponse getOrThrow(UUID tenantId, UUID itemId) {
        try {
            return jdbc.queryForObject("SELECT * FROM erp_items WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, itemId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found: " + itemId);
        }
    }

    private ItemResponse transition(UUID tenantId, UUID itemId, String newStatus, String auditAction, Authentication auth) {
        ItemResponse existing = getOrThrow(tenantId, itemId);
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_items SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, itemId);
        audit(tenantId, auth, auditAction, itemId, "name=" + existing.name() + ",to=" + newStatus);
        return getOrThrow(tenantId, itemId);
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "ITEM", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private ItemResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("code"), rs.getString("sku"), rs.getString("name"),
                rs.getString("description"),
                ErpDomain.ItemType.valueOf(rs.getString("item_type")),
                ErpDomain.UnitOfMeasure.valueOf(rs.getString("unit_of_measure")),
                ErpDomain.ItemStatus.valueOf(rs.getString("status")),
                rs.getBoolean("track_inventory"),
                rs.getBigDecimal("reorder_level"),
                rs.getBigDecimal("reorder_quantity"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
