package com.sanad.platform.hr.payroll.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.integration.HrDomainEventPublisher;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository.IdempotencyAdmission;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository.PayrollRunRow;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * G4-T5 governed payroll lifecycle.
 *
 * <p>Mutation, immutable audit evidence, producer-local outbox evidence and
 * durable idempotency completion are committed on one PostgreSQL transaction.
 * No authorization shortcuts or accounting integration belong here; those are
 * T6/T7 boundaries.</p>
 */
@Service
public class PayrollLifecycleService {

    private static final String AGGREGATE_TYPE = "HR_PAYROLL_RUN";

    private static final Map<PayrollRunStatus, Set<PayrollRunStatus>> ALLOWED =
            allowedTransitions();

    private final DataSource dataSource;
    private final JdbcPayrollLifecycleRepository repository;
    private final JdbcHrAuditRepository auditRepository;
    private final HrDomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PayrollLifecycleService(
            DataSource dataSource,
            JdbcPayrollLifecycleRepository repository,
            JdbcHrAuditRepository auditRepository,
            HrDomainEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public LifecycleResult transition(
            HrCommandContext context,
            UUID runId,
            PayrollRunStatus target,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint) {
        Objects.requireNonNull(target, "target");
        return execute(
                context,
                runId,
                target,
                expectedVersion,
                idempotencyKey,
                requestFingerprint,
                false);
    }

    public LifecycleResult recalculate(
            HrCommandContext context,
            UUID runId,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint) {
        return execute(
                context,
                runId,
                PayrollRunStatus.CALCULATED,
                expectedVersion,
                idempotencyKey,
                requestFingerprint,
                true);
    }

    private LifecycleResult execute(
            HrCommandContext context,
            UUID runId,
            PayrollRunStatus target,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint,
            boolean recalculation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(context.tenantId(), "context.tenantId");
        Objects.requireNonNull(context.actorUserId(), "context.actorUserId");
        Objects.requireNonNull(runId, "runId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(requestFingerprint, "requestFingerprint");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("HRM_PAYROLL_VERSION_INVALID");
        }

        String operation = recalculation
                ? "HRM.PAYROLL.RECALCULATE"
                : "HRM.PAYROLL.TRANSITION." + target.name();
        String canonicalFingerprint = canonicalFingerprint(
                runId, operation, expectedVersion, requestFingerprint);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, context.tenantId());

                IdempotencyAdmission admission = repository.admit(
                        connection,
                        context.tenantId(),
                        context.actorUserId(),
                        operation,
                        idempotencyKey,
                        canonicalFingerprint);

                if (admission.replay()) {
                    LifecycleResult replay = objectMapper.readValue(
                            admission.responseBody(), LifecycleResult.class);
                    connection.rollback();
                    return replay;
                }

                PayrollRunRow run = repository.lockRun(
                        connection, context.tenantId(), runId);
                if (run == null) {
                    throw new IllegalStateException(
                            "HRM_PAYROLL_RUN_NOT_FOUND: " + runId);
                }
                if (run.version() != expectedVersion) {
                    throw new IllegalStateException(
                            "HRM_PAYROLL_VERSION_CONFLICT: expected "
                                    + expectedVersion + " but was " + run.version());
                }

                PayrollRunStatus current = parseStatus(run.status());
                if (recalculation) {
                    if (current != PayrollRunStatus.CALCULATED) {
                        throw new IllegalStateException(
                                "HRM_PAYROLL_RECALCULATION_NOT_ALLOWED: current status " + current);
                    }
                } else {
                    requireAllowed(current, target);
                }

                long newVersion = recalculation
                        ? repository.incrementForRecalculation(
                                connection, context.tenantId(), runId, expectedVersion)
                        : repository.updateRunStatus(
                                connection, context.tenantId(), runId,
                                target.name(), expectedVersion, context.actorUserId());

                if (!recalculation) {
                    repository.projectItemStatus(
                            connection, context.tenantId(), runId, target.name());
                }

                LifecycleResult result = new LifecycleResult(
                        run.id(),
                        recalculation ? PayrollRunStatus.CALCULATED : target,
                        newVersion,
                        recalculation);

                appendEvidence(
                        connection,
                        context,
                        run,
                        current,
                        result.status(),
                        expectedVersion,
                        newVersion,
                        idempotencyKey,
                        recalculation);

                repository.completeIdempotency(
                        connection,
                        admission.id(),
                        runId,
                        objectMapper.writeValueAsString(result));

                connection.commit();
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(
                        "HRM_PAYROLL_LIFECYCLE_FAILED: " + e.getMessage(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connection close will finish cleanup
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_LIFECYCLE_FAILED: " + e.getMessage(), e);
        }
    }

    private void appendEvidence(
            Connection connection,
            HrCommandContext context,
            PayrollRunRow run,
            PayrollRunStatus before,
            PayrollRunStatus after,
            long beforeVersion,
            long afterVersion,
            String idempotencyKey,
            boolean recalculation) throws SQLException {

        ObjectNode beforeState = objectMapper.createObjectNode()
                .put("status", before.name())
                .put("version", beforeVersion);
        ObjectNode afterState = objectMapper.createObjectNode()
                .put("status", after.name())
                .put("version", afterVersion);

        String auditAction = recalculation
                ? "HR.PAYROLL.RECALCULATED"
                : "HR.PAYROLL." + after.name();

        HrAuditRecord audit = new HrAuditRecord(
                context.tenantId(),
                context.actorUserId(),
                auditAction,
                AGGREGATE_TYPE,
                run.id(),
                null,
                run.legalEntityId(),
                "RESTRICTED",
                recalculation ? "PAYROLL_RECALCULATION" : "PAYROLL_LIFECYCLE_TRANSITION",
                beforeState,
                afterState,
                "SUCCESS",
                context.correlationId(),
                null,
                Instant.now());

        UUID auditId = auditRepository.insertLedgerRow(connection, audit);
        auditRepository.insertDeliveryRow(connection, auditId, context.tenantId());

        ObjectNode payload = objectMapper.createObjectNode()
                .put("payrollRunId", run.id().toString())
                .put("periodStart", run.periodStart().toString())
                .put("periodEnd", run.periodEnd().toString())
                .put("status", after.name())
                .put("version", afterVersion);

        String eventType = "HRM.PAYROLL." + (
                recalculation ? "CALCULATED" : after.name()) + ".v1";

        eventPublisher.publish(
                connection,
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        eventType,
                        1,
                        AGGREGATE_TYPE,
                        run.id(),
                        context.tenantId(),
                        null,
                        context.actorUserId(),
                        Instant.now(),
                        context.correlationId(),
                        null,
                        idempotencyKey,
                        "OPERATIONAL",
                        payload));
    }

    private static void requireAllowed(
            PayrollRunStatus current,
            PayrollRunStatus target) {
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_LIFECYCLE_INVALID: "
                            + current + " -> " + target + " is not allowed");
        }
    }

    private static PayrollRunStatus parseStatus(String status) {
        try {
            return PayrollRunStatus.valueOf(status);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_LIFECYCLE_INVALID: unknown status " + status, invalid);
        }
    }

    private static Map<PayrollRunStatus, Set<PayrollRunStatus>> allowedTransitions() {
        EnumMap<PayrollRunStatus, Set<PayrollRunStatus>> transitions =
                new EnumMap<>(PayrollRunStatus.class);
        transitions.put(
                PayrollRunStatus.DRAFT,
                EnumSet.of(PayrollRunStatus.CALCULATED, PayrollRunStatus.CANCELLED));
        transitions.put(
                PayrollRunStatus.CALCULATED,
                EnumSet.of(PayrollRunStatus.REVIEWED, PayrollRunStatus.CANCELLED));
        transitions.put(
                PayrollRunStatus.REVIEWED,
                EnumSet.of(PayrollRunStatus.APPROVED, PayrollRunStatus.CANCELLED));
        transitions.put(
                PayrollRunStatus.APPROVED,
                EnumSet.of(PayrollRunStatus.EXPORTED));
        transitions.put(PayrollRunStatus.EXPORTED, EnumSet.noneOf(PayrollRunStatus.class));
        transitions.put(PayrollRunStatus.CANCELLED, EnumSet.noneOf(PayrollRunStatus.class));
        return Map.copyOf(transitions);
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static String canonicalFingerprint(
            UUID runId,
            String operation,
            long expectedVersion,
            String callerFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = runId + "|" + operation + "|" + expectedVersion + "|" + callerFingerprint;
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "HRM_PAYROLL_LIFECYCLE_INVALID: " + name + " is required");
        }
    }

    public enum PayrollRunStatus {
        DRAFT,
        CALCULATED,
        REVIEWED,
        APPROVED,
        EXPORTED,
        CANCELLED
    }

    public record LifecycleResult(
            UUID runId,
            PayrollRunStatus status,
            long version,
            boolean recalculated) {
    }
}
