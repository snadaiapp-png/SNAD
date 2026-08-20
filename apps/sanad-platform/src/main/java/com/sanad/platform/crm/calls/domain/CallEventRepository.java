package com.sanad.platform.crm.calls.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the call aggregate (G8-03 §18).
 */
public interface CallEventRepository {

    /** Bounded page size for list operations. */
    int MAX_LIST_LIMIT = 100;

    CallEvent create(UUID tenantId, UUID actorId, CallEvent event, Instant now);

    Optional<CallEvent> get(UUID tenantId, UUID callId);

    Optional<CallEvent> findByProviderCallId(UUID tenantId, String provider, String providerCallId);

    /**
     * Applies a monotonic state transition with optimistic concurrency
     * ({@code version} is part of the predicate). Provider ingestion is
     * atomic/idempotent/state-aware — never client If-Match.
     */
    CallEvent transition(UUID tenantId, UUID callId, long expectedVersion, UUID actorId,
                         CallStatus toStatus, Instant occurredAt, Instant now);

    /** Marks the call terminal: ended_at/duration/disposition. */
    CallEvent complete(UUID tenantId, UUID callId, long expectedVersion, UUID actorId,
                       CallStatus terminalStatus, Instant endedAt, int durationSeconds,
                       CallDisposition disposition, Instant now);

    /** Bounded, cursor-stable listing (cursor = base64url of created_at ms:id). */
    List<CallEvent> list(UUID tenantId, String status, long cursorMs, UUID cursorId,
                         int limit);
}
