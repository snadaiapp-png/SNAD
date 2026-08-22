package com.sanad.platform.crm.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Port for the durable CRM transactional event outbox.
 *
 * <p>The outbox stores domain events that originate inside a CRM mutation
 * transaction so they can be delivered asynchronously by a separate worker.
 * The state machine is {@code PENDING -> PROCESSING -> PUBLISHED} or
 * {@code PROCESSING -> FAILED -> (retry) PROCESSING}.
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>Not mutate the {@code app.tenant_id} GUC.</li>
 *   <li>Not read {@link org.springframework.security.core.context.SecurityContextHolder}.</li>
 *   <li>Scope every read/update by the explicitly supplied {@code tenantId}.</li>
 *   <li>Rely on RLS (FORCE RLS + fail-closed policy on {@code crm_event_outbox})
 *       for tenant isolation — wrong tenant context must never defeat RLS.</li>
 * </ul>
 *
 * <p>Tests establish transaction-local GUC explicitly so behaviour can be
 * reasoned about deterministically. Missing GUC must remain fail-closed.
 */
public interface CrmEventOutboxPort {

    /**
     * Append a single event to the outbox. The row is persisted with
     * {@code status='PENDING'}, {@code attempt_count=0},
     * {@code updated_at=createdAt}. The caller is responsible for ensuring
     * the active transaction has the correct tenant GUC set so RLS WITH CHECK
     * accepts the row.
     *
     * @param event the envelope to append (must be non-null and pass
     *              {@link CrmEventEnvelope} validation)
     */
    void append(CrmEventEnvelope event);

    /**
     * Claim up to {@code limit} due rows for the supplied tenant. Due rows
     * are those with {@code status IN ('PENDING','FAILED')} and
     * {@code available_at <= now}. Selected rows are atomically transitioned
     * to {@code status='PROCESSING'} with {@code claimed_at=now} and
     * {@code updated_at=now} using {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     *
     * <p>{@code attempt_count} is NOT incremented on claim — only on
     * {@link #markFailed(UUID, UUID, Instant, String)}.
     *
     * @param tenantId the tenant scope (must be non-null)
     * @param now      the cutoff instant — only rows with available_at <= now are returned
     * @param limit    max rows to claim — must be in {@code [1, 100]}
     * @return the claimed envelopes in deterministic {@code (available_at ASC, created_at ASC, id ASC)}
     *         order; empty list if none due
     */
    List<CrmEventEnvelope> claimDue(UUID tenantId, Instant now, int limit);

    /**
     * Atomically transition a claimed row from {@code PROCESSING} to
     * {@code PUBLISHED} with {@code published_at=publishedAt} and
     * {@code updated_at=publishedAt}.
     *
     * @return {@code true} if exactly one row was updated; {@code false} if
     *         no row matched (wrong tenant, wrong id, or status != PROCESSING)
     */
    boolean markPublished(UUID tenantId, UUID eventId, Instant publishedAt);

    /**
     * Atomically transition a claimed row from {@code PROCESSING} to
     * {@code FAILED} with {@code attempt_count=attempt_count+1},
     * {@code claimed_at=NULL}, {@code available_at=nextAttemptAt},
     * {@code last_error=error} (truncated to first 2000 Java chars),
     * and {@code updated_at=CURRENT_TIMESTAMP}.
     *
     * @return {@code true} if exactly one row was updated; {@code false} if
     *         no row matched
     */
    boolean markFailed(UUID tenantId, UUID eventId, Instant nextAttemptAt, String error);

    /**
     * Domain envelope for a CRM event outbox row.
     *
     * <p>Required non-null fields: {@code id, tenantId, eventType,
     * aggregateType, aggregateId, correlationId, payload, availableAt,
     * createdAt}. {@code schemaVersion} must be {@code >= 1}.
     *
     * <p>{@code causationId} may be null.
     */
    record CrmEventEnvelope(
            UUID id,
            UUID tenantId,
            String eventType,
            int schemaVersion,
            String aggregateType,
            UUID aggregateId,
            String correlationId,
            String causationId,
            JsonNode payload,
            Instant availableAt,
            Instant createdAt) {

        public CrmEventEnvelope {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(aggregateType, "aggregateType must not be null");
            Objects.requireNonNull(aggregateId, "aggregateId must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (schemaVersion < 1) {
                throw new IllegalArgumentException(
                        "schemaVersion must be >= 1, got " + schemaVersion);
            }
            // causationId may be null
        }
    }
}
