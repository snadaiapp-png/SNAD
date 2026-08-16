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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse application service (v20260816.7).
 *
 * <p>Tenant-scoped CRUD + lifecycle (activate / archive) +
 * setPrimary for {@code erp_warehouses}. Mirrors the {@code WebsiteService}
 * patterns.
 */
@Service
public class ErpWarehouseService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public ErpWarehouseService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public WarehouseResponse create(UUID tenantId, CreateWarehouseRequest request, Authentication auth) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (request.code() == null || request.code().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        boolean isPrimary = request.isPrimary() != null && request.isPrimary();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO erp_warehouses (id, tenant_id, code, name, status, location, "
                            + "is_primary, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?)",
                    id, tenantId, request.code().trim(), request.name().trim(),
                    request.location(), isPrimary,
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "warehouse code already exists for this tenant: " + request.code());
        }
        if (isPrimary) setPrimaryInternal(tenantId, id);
        audit(tenantId, auth, "WAREHOUSE.CREATED", id, "code=" + request.code());
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public WarehouseResponse update(UUID tenantId, UUID warehouseId, UpdateWarehouseRequest request, Authentication auth) {
        WarehouseResponse existing = getOrThrow(tenantId, warehouseId);
        if (request.expectedVersion() != null && request.expectedVersion() != existing.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "version conflict: expected " + request.expectedVersion() + " but was " + existing.version());
        }
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE erp_warehouses SET name = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.name().trim(), Timestamp.from(now), tenantId, warehouseId);
        }
        if (request.location() != null) {
            jdbc.update("UPDATE erp_warehouses SET location = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.location(), Timestamp.from(now), tenantId, warehouseId);
        }
        audit(tenantId, auth, "WAREHOUSE.UPDATED", warehouseId, "name=" + existing.name());
        return getOrThrow(tenantId, warehouseId);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID tenantId, UUID warehouseId) {
        return getOrThrow(tenantId, warehouseId);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_warehouses WHERE tenant_id = ? ORDER BY created_at",
                this::mapRow, tenantId);
    }

    @Transactional
    public WarehouseResponse activate(UUID tenantId, UUID warehouseId, Authentication auth) {
        return transition(tenantId, warehouseId, "ACTIVE", "WAREHOUSE.ACTIVATED", auth);
    }

    @Transactional
    public WarehouseResponse archive(UUID tenantId, UUID warehouseId, Authentication auth) {
        return transition(tenantId, warehouseId, "ARCHIVED", "WAREHOUSE.ARCHIVED", auth);
    }

    @Transactional
    public WarehouseResponse setPrimary(UUID tenantId, UUID warehouseId, Authentication auth) {
        WarehouseResponse existing = getOrThrow(tenantId, warehouseId);
        setPrimaryInternal(tenantId, warehouseId);
        audit(tenantId, auth, "WAREHOUSE.SET_PRIMARY", warehouseId, "name=" + existing.name());
        return getOrThrow(tenantId, warehouseId);
    }

    // ===== Helpers =====
    WarehouseResponse getOrThrow(UUID tenantId, UUID warehouseId) {
        try {
            return jdbc.queryForObject("SELECT * FROM erp_warehouses WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, warehouseId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "warehouse not found: " + warehouseId);
        }
    }

    private void setPrimaryInternal(UUID tenantId, UUID warehouseId) {
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_warehouses SET is_primary = FALSE, updated_at = ? "
                        + "WHERE tenant_id = ? AND is_primary = TRUE", Timestamp.from(now), tenantId);
        jdbc.update("UPDATE erp_warehouses SET is_primary = TRUE, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, warehouseId);
    }

    private WarehouseResponse transition(UUID tenantId, UUID warehouseId, String newStatus, String auditAction, Authentication auth) {
        WarehouseResponse existing = getOrThrow(tenantId, warehouseId);
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_warehouses SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, warehouseId);
        audit(tenantId, auth, auditAction, warehouseId, "name=" + existing.name() + ",to=" + newStatus);
        return getOrThrow(tenantId, warehouseId);
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "WAREHOUSE", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private WarehouseResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("code"), rs.getString("name"),
                ErpDomain.WarehouseStatus.valueOf(rs.getString("status")),
                rs.getString("location"), rs.getBoolean("is_primary"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
