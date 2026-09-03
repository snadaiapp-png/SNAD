package com.sanad.platform.hr.integration;

import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * HR producer-local outbox persistence (WS4 Task 4).
 *
 * <p>Appends {@link DomainEventEnvelope} rows to
 * {@code hr_domain_event_outbox} on the caller's {@link Connection} — the
 * same transaction as the canonical mutation. The envelope is tenant-bound
 * by construction, carries deterministic event identity (event id +
 * idempotency key), and the payload has already passed the central
 * redaction guard (defense in depth: the DB-level no_raw_secrets CHECK also
 * rejects raw sensitive keys). External delivery belongs to later tasks
 * (WS4 Task 6 workers).</p>
 */
@Repository
public class JdbcHrOutboxRepository {

    public void append(Connection connection, DomainEventEnvelope envelope) throws SQLException {
        String sql = "INSERT INTO hr_domain_event_outbox " +
                "(event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, " +
                "organization_id, actor_user_id, occurred_at, correlation_id, causation_id, " +
                "idempotency_key, data_classification, payload, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'READY')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, envelope.eventId());
            ps.setObject(2, envelope.tenantId());
            ps.setString(3, envelope.eventType());
            ps.setInt(4, envelope.eventVersion());
            ps.setString(5, envelope.aggregateType());
            setNullableUuid(ps, 6, envelope.aggregateId());
            setNullableUuid(ps, 7, envelope.organizationId());
            setNullableUuid(ps, 8, envelope.actorUserId());
            ps.setObject(9, OffsetDateTime.ofInstant(envelope.occurredAt(), ZoneOffset.UTC));
            setNullableUuid(ps, 10, envelope.correlationId());
            setNullableUuid(ps, 11, envelope.causationId());
            if (envelope.idempotencyKey() == null) {
                ps.setNull(12, Types.VARCHAR);
            } else {
                ps.setString(12, envelope.idempotencyKey());
            }
            ps.setString(13, envelope.dataClassification() == null ? "OPERATIONAL" : envelope.dataClassification());
            ps.setString(14, envelope.payload() == null ? "{}" : envelope.payload().toString());
            ps.executeUpdate();
        }
    }

    private void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
