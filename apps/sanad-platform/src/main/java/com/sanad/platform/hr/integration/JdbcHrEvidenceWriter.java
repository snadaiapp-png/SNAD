package com.sanad.platform.hr.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrAuditService;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Default transactional evidence writer (WS4 Task 4).
 *
 * <p>Composes {@link HrAuditService} and {@link HrDomainEventPublisher} to
 * append the audit fact (ledger + delivery) and the outbox event on the
 * supplied {@link java.sql.Connection} — inside the caller's transaction.
 * Wired into the concrete mutation boundaries (JdbcEmploymentRepository,
 * JdbcHrAssignmentRepository). No REQUIRES_NEW: any append failure rolls
 * back the entire business transaction.</p>
 */
@Service
public class JdbcHrEvidenceWriter implements HrTransactionalEvidenceWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HrAuditService auditService;
    private final HrDomainEventPublisher eventPublisher;

    @Autowired
    public JdbcHrEvidenceWriter(DataSource dataSource) {
        HrRedactionGuard guard = new HrRedactionGuard();
        this.auditService = new HrAuditService(
                Objects.requireNonNull(dataSource, "dataSource"), guard, new com.sanad.platform.hr.audit.JdbcHrAuditRepository(guard));
        this.eventPublisher = new HrDomainEventPublisher(
                dataSource, guard, new JdbcHrOutboxRepository());
    }

    @Override
    public void writeEvidence(java.sql.Connection connection, HrAuditRecord auditRecord, DomainEventEnvelope event) {
        auditService.appendMutationAudit(connection, auditRecord);
        eventPublisher.publish(connection, event);
    }

    /**
     * Deterministic event identity: UUIDv3 of
     * {@code tenantId|eventType|aggregateId|distinguisher}. A retried
     * transaction that regenerates the same business event deterministically
     * re-derives the same event id, preventing duplicate event facts.
     */
    public static UUID deterministicEventId(UUID tenantId, String eventType, UUID aggregateId, UUID distinguisher) {
        return UUID.nameUUIDFromBytes((tenantId + "|" + eventType + "|" + aggregateId + "|" + distinguisher)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static ObjectNode eventPayload(String... keyValues) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("eventPayload requires key/value pairs");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            payload.put(keyValues[i], keyValues[i + 1]);
        }
        return payload;
    }
}
