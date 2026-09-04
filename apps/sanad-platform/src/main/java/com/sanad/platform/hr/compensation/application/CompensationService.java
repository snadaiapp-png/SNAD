package com.sanad.platform.hr.compensation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.SensitiveReadAuditService;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
import com.sanad.platform.hr.compensation.domain.CompensationRepository;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compensation service (WS6 Task 3).
 *
 * <p>Structural rules ONLY (one BASE_SALARY max, amount XOR percentage,
 * positive values, no overlapping ACTIVE packages, immutable history).
 * Any statutory minimum/maximum/contribution treatment belongs to a reviewed
 * Country Rule handler — never here.</p>
 *
 * <p>Restricted reads of component amounts MUST pass the fail-closed
 * sensitive-read audit ({@link SensitiveReadAuditService#recordOrThrow})
 * before amounts are returned. The generic change event
 * {@code HRM.COMPENSATION.CHANGED.v1} carries IDs / component codes / types /
 * effective dates / provenance — NEVER amounts.</p>
 */
@Service
public class CompensationService {

    public static final String EVENT_COMPENSATION_CHANGED = "HRM.COMPENSATION.CHANGED.v1";
    public static final String SENSITIVE_READ_ACTION = "HR.SENSITIVE_READ.COMPENSATION";

    private final CompensationRepository repository;
    private final CompensationAuthorizationPort authorizationPort;
    private final SensitiveReadAuditService sensitiveReadAuditService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompensationService(
            CompensationRepository repository,
            CompensationAuthorizationPort authorizationPort,
            SensitiveReadAuditService sensitiveReadAuditService,
            DataSource dataSource,
            ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.sensitiveReadAuditService = Objects.requireNonNull(sensitiveReadAuditService, "sensitiveReadAuditService");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public record CreateCompensationCommand(
            UUID employmentId, String currencyCode, String payFrequency,
            LocalDate effectiveFrom, List<CompensationComponent> components) {
    }

    public record ReviseCompensationCommand(
            String currencyCode, String payFrequency, LocalDate effectiveFrom,
            List<CompensationComponent> components, String reasonCode) {
    }

    public CompensationPackage createPackage(HrCommandContext ctx, CreateCompensationCommand command) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(command, "command");
        CompensationPackage pkg = newPackage(UUID.randomUUID(), ctx.tenantId(), command.employmentId(),
                command.currencyCode(), command.payFrequency(), command.effectiveFrom(), null,
                CompensationPackage.STATUS_ACTIVE, null, command.components(), 1);
        authorizationPort.requireManage(ctx, pkg.id());
        repository.createPackageWithEvidence(pkg,
                auditRecord(ctx, "HRM.COMPENSATION.CREATE", pkg.employmentId(), "CREATE"),
                envelope(ctx, pkg));
        return pkg;
    }

    public CompensationPackage revisePackage(HrCommandContext ctx, UUID currentPackageId,
                                             ReviseCompensationCommand command) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(currentPackageId, "currentPackageId");
        CompensationPackage current = repository.findPackage(ctx.tenantId(), currentPackageId)
                .orElseThrow(() -> new IllegalStateException("HRM_COMPENSATION_NOT_FOUND: " + currentPackageId));
        authorizationPort.requireManage(ctx, currentPackageId);

        CompensationPackage successor = newPackage(UUID.randomUUID(), ctx.tenantId(), current.employmentId(),
                command.currencyCode(), command.payFrequency(), command.effectiveFrom(), null,
                CompensationPackage.STATUS_ACTIVE, currentPackageId, command.components(), current.version() + 1);

        repository.revisePackageWithEvidence(ctx.tenantId(), currentPackageId, successor,
                command.effectiveFrom().minusDays(1),
                auditRecord(ctx, "HRM.COMPENSATION.REVISE", current.employmentId(), command.reasonCode()),
                envelope(ctx, successor));
        return successor;
    }

    public CompensationPackage endPackage(HrCommandContext ctx, UUID packageId, LocalDate effectiveTo, String reason) {
        Objects.requireNonNull(ctx, "ctx");
        authorizationPort.requireManage(ctx, packageId);
        CompensationPackage pkg = repository.findPackage(ctx.tenantId(), packageId)
                .orElseThrow(() -> new IllegalStateException("HRM_COMPENSATION_NOT_FOUND: " + packageId));
        repository.endPackageWithEvidence(ctx.tenantId(), packageId, effectiveTo,
                auditRecord(ctx, "HRM.COMPENSATION.END", pkg.employmentId(), reason),
                envelope(ctx, pkg));
        return pkg;
    }

    /**
     * Sensitive read: returns the ACTIVE package WITH component amounts only
     * after the fail-closed sensitive-read audit succeeds. The audit row
     * carries identifiers/classification/reason — never the amounts.
     */
    public CompensationPackage readActivePackageWithAudit(HrCommandContext ctx, UUID employmentId,
                                                          LocalDate asOf, String reason) {
        Objects.requireNonNull(ctx, "ctx");
        authorizationPort.requireView(ctx, employmentId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, ctx.tenantId().toString());
                    ps.execute();
                }
                sensitiveReadAuditService.recordOrThrow(connection,
                        new com.sanad.platform.hr.audit.HrAuthenticatedContext(ctx.tenantId(), ctx.actorUserId(),
                                ctx.correlationId(), null),
                        SENSITIVE_READ_ACTION, "HR_COMPENSATION_PACKAGE", employmentId,
                        "COMPENSATION", reason);
                CompensationPackage pkg = repository.findActivePackage(ctx.tenantId(), employmentId, asOf)
                        .orElseThrow(() -> new IllegalStateException("HRM_COMPENSATION_NOT_FOUND: no ACTIVE package "
                                + "for employment " + employmentId + " at " + asOf));
                connection.commit();
                return pkg;
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_COMPENSATION_SENSITIVE_READ_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_COMPENSATION_SENSITIVE_READ_FAILED: " + e.getMessage(), e);
        }
    }

    /** History WITHOUT component amounts — safe for directory/projection views. */
    public List<CompensationPackage> readHistoryWithoutAmounts(HrCommandContext ctx, UUID employmentId) {
        Objects.requireNonNull(ctx, "ctx");
        return repository.findPackageHistory(ctx.tenantId(), employmentId).stream()
                .map(p -> new CompensationPackage(p.id(), p.tenantId(), p.employmentId(), p.currencyCode(),
                        p.payFrequency(), p.effectiveFrom(), p.effectiveTo(), p.status(),
                        p.predecessorPackageId(), List.of(), p.version(), p.createdAt()))
                .toList();
    }

    // ==================== helpers ====================

    private CompensationPackage newPackage(UUID id, UUID tenantId, UUID employmentId, String currencyCode,
                                           String payFrequency, LocalDate effectiveFrom, LocalDate effectiveTo,
                                           String status, UUID predecessorId, List<CompensationComponent> components,
                                           long version) {
        List<CompensationComponent> bound = new ArrayList<>();
        for (CompensationComponent c : components == null ? List.<CompensationComponent>of() : components) {
            bound.add(new CompensationComponent(c.id() == null ? UUID.randomUUID() : c.id(), tenantId, id,
                    c.componentType(), c.code(), c.amount(), c.percentage()));
        }
        return new CompensationPackage(id, tenantId, employmentId, currencyCode, payFrequency, effectiveFrom,
                effectiveTo, status, predecessorId, bound, version, Instant.now());
    }

    private HrAuditRecord auditRecord(HrCommandContext ctx, String action, UUID employmentId, String reason) {
        // Metadata only — compensation amounts NEVER enter the audit ledger.
        ObjectNode after = objectMapper.createObjectNode();
        after.put("employmentId", employmentId.toString());
        return new HrAuditRecord(ctx.tenantId(), ctx.actorUserId(), action, "HR_COMPENSATION_PACKAGE",
                employmentId, null, null, "OPERATIONAL", reason, null, after, "SUCCESS",
                ctx.correlationId(), null, Instant.now());
    }

    private DomainEventEnvelope envelope(HrCommandContext ctx, CompensationPackage pkg) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("packageId", pkg.id().toString());
        payload.put("employmentId", pkg.employmentId().toString());
        payload.put("effectiveFrom", pkg.effectiveFrom().toString());
        payload.put("status", pkg.status());
        payload.put("currencyCode", pkg.currencyCode());
        var componentCodes = payload.putArray("componentCodes");
        for (CompensationComponent c : pkg.components()) {
            componentCodes.add(c.code() + ":" + c.componentType().name());
        }
        // Amounts are deliberately ABSENT from the generic event payload.
        UUID eventId = UUID.nameUUIDFromBytes(
                (EVENT_COMPENSATION_CHANGED + ":" + pkg.id() + ":" + pkg.effectiveFrom())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new DomainEventEnvelope(eventId, EVENT_COMPENSATION_CHANGED, 1, "HR_COMPENSATION_PACKAGE",
                pkg.id(), ctx.tenantId(), null, ctx.actorUserId(), Instant.now(), ctx.correlationId(), null,
                EVENT_COMPENSATION_CHANGED + ":" + pkg.id() + ":" + pkg.effectiveFrom(), "OPERATIONAL", payload);
    }
}
