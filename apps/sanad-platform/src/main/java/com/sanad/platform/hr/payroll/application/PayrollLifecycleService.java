package com.sanad.platform.hr.payroll.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.idempotency.JdbcHrRequestIdempotencyService;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import com.sanad.platform.idempotency.IdempotencyBeginResult;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PayrollLifecycleService {

    private static final String AGGREGATE_TYPE = "HR_PAYROLL_RUN";
    private static final String CLASSIFICATION = "RESTRICTED";

    private final DataSource dataSource;
    private final JdbcPayrollLifecycleRepository repository;
    private final HrTransactionalEvidenceWriter evidenceWriter;
    private final JdbcHrRequestIdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PayrollLifecycleService(
            DataSource dataSource,
            JdbcPayrollLifecycleRepository repository,
            HrTransactionalEvidenceWriter evidenceWriter,
            JdbcHrRequestIdempotencyService idempotency,
            ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.evidenceWriter = Objects.requireNonNull(evidenceWriter, "evidenceWriter");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public TransitionResult transition(
            HrAuthenticatedContext actor,
            UUID payrollRunId,
            PayrollLifecycle target,
            long expectedVersion,
            String reason,
            String idempotencyKey,
            String requestFingerprint) {

        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(payrollRunId, "payrollRunId");
        Objects.requireNonNull(target, "target");
        requireText(reason, "reason");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(requestFingerprint, "requestFingerprint");

        String operation = "HR.PAYROLL.TRANSITION." + target.name();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, actor.tenantId());

                IdempotencyBeginResult begin = idempotency.begin(
                        connection,
                        actor.tenantId(),
                        actor.actorUserId(),
                        operation,
                        idempotencyKey,
                        requestFingerprint);

                if (begin.alreadyExists()) {
                    if (begin.priorStatus() == null || begin.priorResponse() == null) {
                        throw new IllegalStateException(
                                "HRM_IDEMPOTENCY_CONFLICT: payroll transition is still in flight");
                    }
                    TransitionResult replay = objectMapper.readValue(
                            begin.priorResponse(), TransitionResult.class);
                    connection.commit();
                    return new TransitionResult(
                            replay.payrollRunId(),
                            replay.fromStatus(),
                            replay.toStatus(),
                            replay.version(),
                            true);
                }

                JdbcPayrollLifecycleRepository.RunState current = repository
                        .loadForUpdate(connection, actor.tenantId(), payrollRunId)
                        .orElseThrow(() -> new IllegalStateException(
                                "HRM_PAYROLL_RUN_NOT_FOUND: " + payrollRunId));

                if (current.version() != expectedVersion) {
                    throw new IllegalStateException(
                            "HRM_PAYROLL_VERSION_CONFLICT: expected="
                                    + expectedVersion + ", actual=" + current.version());
                }

                PayrollLifecycle.requireTransition(current.status(), target);

                JdbcPayrollLifecycleRepository.RunState updated =
                        repository.transition(connection, current, target, actor.actorUserId());

                Instant occurredAt = Instant.now();

                ObjectNode before = objectMapper.createObjectNode();
                before.put("status", current.status().name());
                before.put("version", current.version());

                ObjectNode after = objectMapper.createObjectNode();
                after.put("status", updated.status().name());
                after.put("version", updated.version());

                HrAuditRecord audit = new HrAuditRecord(
                        actor.tenantId(),
                        actor.actorUserId(),
                        actionFor(target),
                        AGGREGATE_TYPE,
                        payrollRunId,
                        null,
                        current.legalEntityId(),
                        CLASSIFICATION,
                        reason,
                        before,
                        after,
                        "SUCCESS",
                        actor.correlationId(),
                        actor.requestId(),
                        occurredAt);

                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("payrollRunId", payrollRunId.toString());
                payload.put("fromStatus", current.status().name());
                payload.put("toStatus", updated.status().name());
                payload.put("version", updated.version());

                DomainEventEnvelope event = new DomainEventEnvelope(
                        deterministicEventId(actor.tenantId(), eventTypeFor(target), payrollRunId, idempotencyKey),
                        eventTypeFor(target),
                        1,
                        AGGREGATE_TYPE,
                        payrollRunId,
                        actor.tenantId(),
                        null,
                        actor.actorUserId(),
                        occurredAt,
                        actor.correlationId(),
                        actor.requestId(),
                        idempotencyKey,
                        "OPERATIONAL",
                        payload);

                evidenceWriter.writeEvidence(connection, audit, event);

                TransitionResult result = new TransitionResult(
                        payrollRunId,
                        current.status(),
                        updated.status(),
                        updated.version(),
                        false);

                idempotency.complete(
                        connection,
                        begin.operationId(),
                        200,
                        objectMapper.writeValueAsString(result));

                connection.commit();
                return result;
            } catch (Exception failure) {
                rollbackQuietly(connection);
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(
                        "HRM_PAYROLL_LIFECYCLE_FAILED: " + failure.getMessage(), failure);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connection close cleanup
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_LIFECYCLE_FAILED: " + e.getMessage(), e);
        }
    }

    private static UUID deterministicEventId(
            UUID tenantId, String eventType, UUID payrollRunId, String idempotencyKey) {
        String material = tenantId + "|" + eventType + "|" + payrollRunId + "|" + idempotencyKey;
        return UUID.nameUUIDFromBytes(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String actionFor(PayrollLifecycle target) {
        return "HRM.PAYROLL." + target.name();
    }

    private static String eventTypeFor(PayrollLifecycle target) {
        return "HRM.PAYROLL." + target.name() + ".v1";
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // preserve original failure
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "HRM_PAYROLL_LIFECYCLE_INVALID: " + field + " is required");
        }
    }

    public record TransitionResult(
            UUID payrollRunId,
            PayrollLifecycle fromStatus,
            PayrollLifecycle toStatus,
            long version,
            boolean replayed) {
    }
}
