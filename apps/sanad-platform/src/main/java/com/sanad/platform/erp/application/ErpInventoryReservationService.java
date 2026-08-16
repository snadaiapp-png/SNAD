package com.sanad.platform.erp.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.erp.api.ErpDtos.*;
import com.sanad.platform.erp.domain.ErpDomain;
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
 * Inventory Reservation service (v20260816.7).
 *
 * <p>Tenant-scoped reservations against {@code erp_inventory_reservations} +
 * {@code erp_inventory_balances.reserved} counter. Supports:
 * <ul>
 *   <li>{@link #reserve(UUID, UUID, UUID, BigDecimal, String, String, Authentication)} —
 *       creates a reservation + increases the {@code reserved} count.
 *       Idempotent: if {@code externalReference} already maps to a reservation
 *       in (PENDING, RESERVED, CONFIRMED) status, returns that reservation
 *       without creating a new one.</li>
 *   <li>{@link #release(UUID, Authentication)} — decreases {@code reserved},
 *       marks the reservation RELEASED, creates a RELEASE movement.</li>
 *   <li>{@link #confirm(UUID, Authentication)} — decreases {@code reserved}
 *       AND {@code on_hand} (the reserved quantity is consumed), creates a
 *       FULFILLMENT movement.</li>
 * </ul>
 */
@Service
public class ErpInventoryReservationService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ErpInventoryService inventoryService;

    public ErpInventoryReservationService(JdbcTemplate jdbc, PlatformAuditService auditService,
                                            ErpInventoryService inventoryService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public ReservationResponse reserve(UUID tenantId, UUID warehouseId, UUID itemId,
                                         BigDecimal quantity, String source, String externalReference,
                                         Authentication auth) {
        if (quantity == null || quantity.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be > 0");
        // Idempotency: if externalReference already exists, return that reservation
        if (externalReference != null && !externalReference.isBlank()) {
            try {
                ReservationResponse existing = jdbc.queryForObject(
                        "SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? AND external_reference = ? "
                                + "AND status IN ('PENDING','RESERVED','CONFIRMED') LIMIT 1",
                        this::mapRow, tenantId, externalReference);
                if (existing != null) return existing;
            } catch (EmptyResultDataAccessException ignored) {}
        }
        // Ensure balance exists, then check available stock
        InventoryBalanceResponse balance = inventoryService.getOrCreateBalance(tenantId, warehouseId, itemId);
        BigDecimal available = balance.available();
        if (available.compareTo(quantity) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "insufficient stock for reservation: available=" + available + ",requested=" + quantity);
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO erp_inventory_reservations (id, tenant_id, warehouse_id, item_id, "
                        + "quantity, source, external_reference, status, expires_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?, 0, ?, ?)",
                id, tenantId, warehouseId, itemId, quantity, source, externalReference,
                null, Timestamp.from(now), Timestamp.from(now));
        // Append RESERVATION movement + bump reserved counter (atomic)
        inventoryService.appendMovement(tenantId, warehouseId, itemId, quantity,
                ErpDomain.MovementType.RESERVATION, "RESERVATION", id,
                "reserve source=" + source, actorUserId(auth));
        inventoryService.applyBalanceDelta(tenantId, warehouseId, itemId,
                BigDecimal.ZERO, quantity, BigDecimal.ZERO);
        audit(tenantId, auth, "RESERVATION.CREATED", id,
                "item=" + itemId + ",qty=" + quantity + ",source=" + source);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public ReservationResponse release(UUID tenantId, UUID reservationId, Authentication auth) {
        ReservationResponse existing = getOrThrow(tenantId, reservationId);
        if (!"RESERVED".equals(existing.status().name()) && !"PENDING".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "reservation cannot be released in state " + existing.status());
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_inventory_reservations SET status = 'RELEASED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, reservationId);
        inventoryService.appendMovement(tenantId, existing.warehouseId(), existing.itemId(),
                existing.quantity(), ErpDomain.MovementType.RELEASE, "RESERVATION", reservationId,
                "release reservation=" + reservationId, actorUserId(auth));
        inventoryService.applyBalanceDelta(tenantId, existing.warehouseId(), existing.itemId(),
                BigDecimal.ZERO, existing.quantity().negate(), BigDecimal.ZERO);
        audit(tenantId, auth, "RESERVATION.RELEASED", reservationId,
                "qty=" + existing.quantity());
        return getOrThrow(tenantId, reservationId);
    }

    @Transactional
    public ReservationResponse confirm(UUID tenantId, UUID reservationId, Authentication auth) {
        ReservationResponse existing = getOrThrow(tenantId, reservationId);
        if (!"RESERVED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "reservation cannot be confirmed in state " + existing.status());
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_inventory_reservations SET status = 'CONFIRMED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, reservationId);
        // FULFILLMENT movement: consumes on_hand + reserved
        inventoryService.appendMovement(tenantId, existing.warehouseId(), existing.itemId(),
                existing.quantity(), ErpDomain.MovementType.FULFILLMENT, "RESERVATION", reservationId,
                "confirm reservation=" + reservationId, actorUserId(auth));
        inventoryService.applyBalanceDelta(tenantId, existing.warehouseId(), existing.itemId(),
                existing.quantity().negate(), existing.quantity().negate(), BigDecimal.ZERO);
        audit(tenantId, auth, "RESERVATION.CONFIRMED", reservationId,
                "qty=" + existing.quantity());
        return getOrThrow(tenantId, reservationId);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID tenantId, UUID reservationId) {
        return getOrThrow(tenantId, reservationId);
    }

    // ===== Helpers =====
    private ReservationResponse getOrThrow(UUID tenantId, UUID reservationId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, reservationId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reservation not found: " + reservationId);
        }
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "RESERVATION", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private ReservationResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReservationResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getBigDecimal("quantity"), rs.getString("source"),
                rs.getString("external_reference"),
                ErpDomain.ReservationStatus.valueOf(rs.getString("status")),
                rs.getObject("expires_at", Timestamp.class) == null ? null
                        : rs.getObject("expires_at", Timestamp.class).toInstant(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
