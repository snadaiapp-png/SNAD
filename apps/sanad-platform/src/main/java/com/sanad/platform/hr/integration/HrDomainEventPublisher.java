package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * HR domain event publisher (WS4 Task 4).
 *
 * <p>Appends the typed, versioned event envelope to the producer-local HR
 * outbox in the SAME transaction as the canonical mutation. The payload
 * passes the central {@link HrRedactionGuard} immediately before
 * persistence. Like the audit path, publishing fails closed when there is
 * no active transaction or the connection is in auto-commit — event
 * evidence must never commit independently of the business mutation.</p>
 */
@Service
public class HrDomainEventPublisher {

    private final DataSource dataSource;
    private final HrRedactionGuard redactionGuard;
    private final JdbcHrOutboxRepository outboxRepository;

    @Autowired
    public HrDomainEventPublisher(
            DataSource dataSource,
            HrRedactionGuard redactionGuard,
            JdbcHrOutboxRepository outboxRepository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.redactionGuard = Objects.requireNonNull(redactionGuard, "redactionGuard");
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
    }

    public void publish(Connection connection, DomainEventEnvelope envelope) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(envelope, "envelope");
        DomainEventEnvelope safe = withRedactedPayload(envelope);
        try {
            outboxRepository.append(connection, safe);
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OUTBOX_APPEND_FAILED: " + e.getMessage(), e);
        }
    }

    public void publish(DomainEventEnvelope envelope) {
        requireTransactionalConnection();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        publish(connection, envelope);
    }

    private DomainEventEnvelope withRedactedPayload(DomainEventEnvelope envelope) {
        var redacted = redactionGuard.redact(envelope.payload());
        if (redacted == envelope.payload()) {
            return envelope;
        }
        return new DomainEventEnvelope(
                envelope.eventId(), envelope.eventType(), envelope.eventVersion(),
                envelope.aggregateType(), envelope.aggregateId(), envelope.tenantId(),
                envelope.organizationId(), envelope.actorUserId(), envelope.occurredAt(),
                envelope.correlationId(), envelope.causationId(), envelope.idempotencyKey(),
                envelope.dataClassification(), redacted);
    }

    private void requireTransactionalConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "HRM_OUTBOX_NOT_TRANSACTIONAL: event evidence must never commit outside the business transaction");
        }
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "HRM_OUTBOX_NOT_TRANSACTIONAL: connection is in auto-commit; refusing to append event evidence");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OUTBOX_NOT_TRANSACTIONAL: unable to verify transaction state", e);
        }
    }
}
