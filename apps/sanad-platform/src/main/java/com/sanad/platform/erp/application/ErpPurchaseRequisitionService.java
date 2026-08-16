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
 * Purchase Requisition application service (v20260816.7).
 *
 * <p>Tenant-scoped requisition lifecycle with status-transition approval:
 * <ol>
 *   <li>{@link #create(UUID, CreateRequisitionRequest, Authentication)} —
 *       creates a requisition in {@code DRAFT} with the supplied items.</li>
 *   <li>{@link #addItem(UUID, UUID, CreateRequisitionItem, Authentication)} —
 *       appends an item to a DRAFT requisition.</li>
 *   <li>{@link #submit(UUID, UUID, Authentication)} — DRAFT → SUBMITTED.</li>
 *   <li>{@link #approve(UUID, UUID, Authentication)} — SUBMITTED → APPROVED.
 *       (Approval may be governed externally via the Workflow API — this
 *       service performs only the status transition.)</li>
 *   <li>{@link #reject(UUID, UUID, Authentication)} — SUBMITTED → REJECTED.</li>
 *   <li>{@link #cancel(UUID, UUID, Authentication)} — non-terminal → CANCELLED.</li>
 * </ol>
 */
@Service
public class ErpPurchaseRequisitionService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public ErpPurchaseRequisitionService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public PurchaseRequisitionResponse create(UUID tenantId, CreateRequisitionRequest request, Authentication auth) {
        if (request == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        ErpDomain.RequisitionPriority priority = request.priority() != null
                ? request.priority() : ErpDomain.RequisitionPriority.NORMAL;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String number = generateNumber("PR", tenantId);
        try {
            jdbc.update("INSERT INTO erp_purchase_requisitions (id, tenant_id, requisition_number, "
                            + "requester_id, reason, priority, status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 0, ?, ?)",
                    id, tenantId, number,
                    request.requesterId() != null ? request.requesterId() : actorUserId(auth),
                    request.reason(), priority.name(),
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition_number collision: " + number);
        }
        if (request.items() != null) {
            for (CreateRequisitionItem item : request.items()) {
                insertItem(tenantId, id, item);
            }
        }
        audit(tenantId, auth, "REQUISITION.CREATED", id, "number=" + number);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public PurchaseRequisitionResponse addItem(UUID tenantId, UUID requisitionId,
                                                  CreateRequisitionItem request, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        if (!"DRAFT".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "cannot add items to requisition in state " + existing.status());
        }
        insertItem(tenantId, requisitionId, request);
        audit(tenantId, auth, "REQUISITION.ITEM_ADDED", requisitionId, "item=" + request.itemId());
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional
    public PurchaseRequisitionResponse submit(UUID tenantId, UUID requisitionId, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        if (!"DRAFT".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition cannot be submitted in state " + existing.status());
        }
        if (existing.items() == null || existing.items().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requisition has no items");
        transition(tenantId, requisitionId, "SUBMITTED");
        audit(tenantId, auth, "REQUISITION.SUBMITTED", requisitionId, "items=" + existing.items().size());
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional
    public PurchaseRequisitionResponse approve(UUID tenantId, UUID requisitionId, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        if (!"SUBMITTED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition cannot be approved in state " + existing.status());
        }
        transition(tenantId, requisitionId, "APPROVED");
        audit(tenantId, auth, "REQUISITION.APPROVED", requisitionId, "items=" + existing.items().size());
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional
    public PurchaseRequisitionResponse reject(UUID tenantId, UUID requisitionId, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        if (!"SUBMITTED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition cannot be rejected in state " + existing.status());
        }
        transition(tenantId, requisitionId, "REJECTED");
        audit(tenantId, auth, "REQUISITION.REJECTED", requisitionId, "items=" + existing.items().size());
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional
    public PurchaseRequisitionResponse cancel(UUID tenantId, UUID requisitionId, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        String status = existing.status().name();
        if ("APPROVED".equals(status) || "REJECTED".equals(status)
                || "CANCELLED".equals(status) || "CONVERTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition cannot be cancelled in state " + existing.status());
        }
        transition(tenantId, requisitionId, "CANCELLED");
        audit(tenantId, auth, "REQUISITION.CANCELLED", requisitionId, "from=" + status);
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional
    public PurchaseRequisitionResponse markConverted(UUID tenantId, UUID requisitionId, Authentication auth) {
        PurchaseRequisitionResponse existing = getOrThrow(tenantId, requisitionId);
        if (!"APPROVED".equals(existing.status().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "requisition cannot be converted in state " + existing.status());
        }
        transition(tenantId, requisitionId, "CONVERTED");
        audit(tenantId, auth, "REQUISITION.CONVERTED", requisitionId, "to=PO");
        return getOrThrow(tenantId, requisitionId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequisitionResponse> list(UUID tenantId) {
        return jdbc.query(
                        "SELECT * FROM erp_purchase_requisitions WHERE tenant_id = ? ORDER BY created_at DESC",
                        this::mapHeader, tenantId)
                .stream().map(this::attachItems).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse get(UUID tenantId, UUID requisitionId) {
        return getOrThrow(tenantId, requisitionId);
    }

    // ===== Helpers =====
    private void insertItem(UUID tenantId, UUID requisitionId, CreateRequisitionItem item) {
        if (item == null || item.itemId() == null || item.quantity() == null
                || item.quantity().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "item requires itemId and positive quantity");
        jdbc.update("INSERT INTO erp_purchase_requisition_items (id, tenant_id, requisition_id, "
                        + "item_id, quantity, required_date, estimated_unit_cost, notes, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, requisitionId, item.itemId(), item.quantity(),
                item.requiredDate() != null ? java.sql.Date.valueOf(item.requiredDate()) : null,
                item.estimatedUnitCost(), item.notes(),
                Timestamp.from(Instant.now()));
    }

    private PurchaseRequisitionResponse getOrThrow(UUID tenantId, UUID requisitionId) {
        try {
            PurchaseRequisitionResponse header = jdbc.queryForObject(
                    "SELECT * FROM erp_purchase_requisitions WHERE tenant_id = ? AND id = ?",
                    this::mapHeader, tenantId, requisitionId);
            return attachItems(header);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "requisition not found: " + requisitionId);
        }
    }

    private PurchaseRequisitionResponse attachItems(PurchaseRequisitionResponse header) {
        if (header == null) return null;
        List<RequisitionItemResponse> items = jdbc.query(
                "SELECT pri.*, i.code AS item_code, i.name AS item_name "
                        + "FROM erp_purchase_requisition_items pri "
                        + "JOIN erp_items i ON i.tenant_id = pri.tenant_id AND i.id = pri.item_id "
                        + "WHERE pri.tenant_id = ? AND pri.requisition_id = ? ORDER BY pri.created_at",
                this::mapItemRow, header.tenantId(), header.id());
        return new PurchaseRequisitionResponse(header.id(), header.tenantId(), header.requisitionNumber(),
                header.requesterId(), header.reason(), header.priority(), header.status(), items,
                header.version(), header.createdAt(), header.updatedAt());
    }

    private void transition(UUID tenantId, UUID requisitionId, String newStatus) {
        Instant now = Instant.now();
        jdbc.update("UPDATE erp_purchase_requisitions SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, requisitionId);
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
        try { auditService.success(auth, tenantId, action, "REQUISITION", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private PurchaseRequisitionResponse mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new PurchaseRequisitionResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("requisition_number"),
                rs.getObject("requester_id", UUID.class),
                rs.getString("reason"),
                ErpDomain.RequisitionPriority.valueOf(rs.getString("priority")),
                ErpDomain.RequisitionStatus.valueOf(rs.getString("status")),
                List.of(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private RequisitionItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date rd = rs.getObject("required_date", java.sql.Date.class);
        return new RequisitionItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("requisition_id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getString("item_code"),
                rs.getString("item_name"), rs.getBigDecimal("quantity"),
                rd == null ? null : rd.toLocalDate(),
                rs.getBigDecimal("estimated_unit_cost"), rs.getString("notes"),
                rs.getObject("created_at", Timestamp.class).toInstant());
    }
}
