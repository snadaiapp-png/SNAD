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
 * Supplier application service (v20260816.7).
 *
 * <p>Tenant-scoped CRUD + lifecycle
 * (pending → active → inactive → blocked → archive) for {@code erp_suppliers}.
 * Mirrors the {@code StoreService} patterns.
 */
@Service
public class ErpSupplierService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public ErpSupplierService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse create(UUID tenantId, CreateSupplierRequest request, Authentication auth) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (request.supplierCode() == null || request.supplierCode().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierCode is required");
        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency() : "SAR";
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO erp_suppliers (id, tenant_id, supplier_code, name, status, "
                            + "contact_email, contact_phone, address, tax_number, payment_terms, currency, "
                            + "version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                    id, tenantId, request.supplierCode().trim(), request.name().trim(),
                    request.contactEmail(), request.contactPhone(), request.address(),
                    request.taxNumber(), request.paymentTerms(), currency,
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "supplier_code already exists for this tenant: " + request.supplierCode());
        }
        audit(tenantId, auth, "SUPPLIER.CREATED", id, "code=" + request.supplierCode());
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public SupplierResponse update(UUID tenantId, UUID supplierId, UpdateSupplierRequest request, Authentication auth) {
        SupplierResponse existing = getOrThrow(tenantId, supplierId);
        if (request.expectedVersion() != null && request.expectedVersion() != existing.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "version conflict: expected " + request.expectedVersion() + " but was " + existing.version());
        }
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE erp_suppliers SET name = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.name().trim(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.contactEmail() != null) {
            jdbc.update("UPDATE erp_suppliers SET contact_email = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.contactEmail(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.contactPhone() != null) {
            jdbc.update("UPDATE erp_suppliers SET contact_phone = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.contactPhone(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.address() != null) {
            jdbc.update("UPDATE erp_suppliers SET address = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.address(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.taxNumber() != null) {
            jdbc.update("UPDATE erp_suppliers SET tax_number = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.taxNumber(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.paymentTerms() != null) {
            jdbc.update("UPDATE erp_suppliers SET payment_terms = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.paymentTerms(), Timestamp.from(now), tenantId, supplierId);
        }
        if (request.currency() != null) {
            jdbc.update("UPDATE erp_suppliers SET currency = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.currency(), Timestamp.from(now), tenantId, supplierId);
        }
        audit(tenantId, auth, "SUPPLIER.UPDATED", supplierId, "name=" + existing.name());
        return getOrThrow(tenantId, supplierId);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID tenantId, UUID supplierId) {
        return getOrThrow(tenantId, supplierId);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM erp_suppliers WHERE tenant_id = ? ORDER BY created_at",
                this::mapRow, tenantId);
    }

    @Transactional
    public SupplierResponse activate(UUID tenantId, UUID supplierId, Authentication auth) {
        return transition(tenantId, supplierId, "ACTIVE", "SUPPLIER.ACTIVATED", auth);
    }

    @Transactional
    public SupplierResponse block(UUID tenantId, UUID supplierId, Authentication auth) {
        return transition(tenantId, supplierId, "BLOCKED", "SUPPLIER.BLOCKED", auth);
    }

    @Transactional
    public SupplierResponse archive(UUID tenantId, UUID supplierId, Authentication auth) {
        return transition(tenantId, supplierId, "ARCHIVED", "SUPPLIER.ARCHIVED", auth);
    }

    // ===== Helpers =====
    SupplierResponse getOrThrow(UUID tenantId, UUID supplierId) {
        try {
            return jdbc.queryForObject("SELECT * FROM erp_suppliers WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, supplierId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "supplier not found: " + supplierId);
        }
    }

    private SupplierResponse transition(UUID tenantId, UUID supplierId, String newStatus, String auditAction, Authentication auth) {
        SupplierResponse existing = getOrThrow(tenantId, supplierId);
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_suppliers SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, supplierId);
        audit(tenantId, auth, auditAction, supplierId, "name=" + existing.name() + ",to=" + newStatus);
        return getOrThrow(tenantId, supplierId);
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "SUPPLIER", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private SupplierResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SupplierResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("supplier_code"), rs.getString("name"),
                ErpDomain.SupplierStatus.valueOf(rs.getString("status")),
                rs.getString("contact_email"), rs.getString("contact_phone"),
                rs.getString("address"), rs.getString("tax_number"),
                rs.getString("payment_terms"), rs.getString("currency"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
