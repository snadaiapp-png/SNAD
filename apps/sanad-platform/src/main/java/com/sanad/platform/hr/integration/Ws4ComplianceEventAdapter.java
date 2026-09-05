package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.compliance.application.ComplianceEventPort;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideEventEntry;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WS4 durable adapter for the WS3-facing {@link ComplianceEventPort}
 * (WS4 Task 4).
 *
 * <p>Appends versioned HRM override events
 * ({@code HRM.COMPLIANCE_OVERRIDE.<ACTION>.v1}) to the producer-local HR
 * outbox inside the caller's current transaction, with deterministic event
 * identity (UUIDv3 of {@code tenantId|eventType|requestId}) and a matching
 * idempotency key. Payload redaction happens inside
 * {@link HrDomainEventPublisher}. External delivery is NOT part of this
 * task (WS4 Task 6 workers).</p>
 */
@Service
public class Ws4ComplianceEventAdapter implements ComplianceEventPort {

    private final DataSource dataSource;
    private final HrDomainEventPublisher publisher;

    @Autowired
    public Ws4ComplianceEventAdapter(DataSource dataSource, HrDomainEventPublisher publisher) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void recordOverrideEvent(ComplianceOverrideEventEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Connection connection = requireTransactionalConnection();
        UUID eventId = UUID.nameUUIDFromBytes(
                (entry.tenantId() + "|" + entry.eventType() + "|" + entry.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DomainEventEnvelope envelope = new DomainEventEnvelope(
                eventId,
                entry.eventType(),
                1,
                "COMPLIANCE_OVERRIDE_REQUEST",
                entry.requestId(),
                entry.tenantId(),
                null,
                entry.actorUserId(),
                Instant.now(),
                entry.correlationId(),
                entry.causationId(),
                entry.eventType() + ":" + entry.requestId(),
                "OPERATIONAL",
                entry.payload());
        publisher.publish(connection, envelope);
    }

    private Connection requireTransactionalConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "HRM_OUTBOX_NOT_TRANSACTIONAL: override events must never commit outside the business transaction");
        }
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "HRM_OUTBOX_NOT_TRANSACTIONAL: connection is in auto-commit; refusing to append event evidence");
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OUTBOX_NOT_TRANSACTIONAL: unable to verify transaction state", e);
        }
    }
}
