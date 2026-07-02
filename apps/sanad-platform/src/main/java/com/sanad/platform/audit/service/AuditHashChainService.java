package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Stage 05A.1 §8-9 — Linear, concurrency-safe hash chain computation.
 *
 * <p>Each audit event's {@code eventHash} is computed as:</p>
 * <pre>
 *   eventHash = SHA-256(
 *     canonicalEventPayload
 *     + previousHash
 *     + tenantId
 *     + occurredAt (normalized to PostgreSQL microsecond precision)
 *     + sequenceNumber
 *   )
 * </pre>
 *
 * <p>The chain is linear: each event's {@code previousHash} equals the
 * preceding event's {@code eventHash}, and {@code sequenceNumber}
 * increments by exactly 1. The {@link AuditService} acquires a
 * pessimistic lock on {@code audit_chain_heads} before computing the
 * next sequence, preventing concurrent forks.</p>
 */
@Service
public class AuditHashChainService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final long MICROS_PER_SECOND = 1_000_000L;

    public String getGenesisHash() {
        return GENESIS_HASH;
    }

    /**
     * Computes the event hash for the given event payload.
     *
     * <p>Stage 05A.2 §6 — Supports schema versioning:
     * <ul>
     *   <li>schemaVersion = 1 (legacy): hash does NOT include sequence_number.
     *       Used for events created before V27 migration.</li>
     *   <li>schemaVersion = 2 (current): hash includes sequence_number for
     *       linear chain enforcement.</li>
     * </ul>
     *
     * @param event the event (must have tenantId, occurredAt, and all
     *              business fields populated; sequenceNumber required for v2)
     * @param previousHash the hash of the preceding event in the same
     *                     tenant's chain, or {@link #getGenesisHash()}
     *                     if this is the first event
     * @return the 64-character hex SHA-256 hash
     */
    public String computeEventHash(AuditEvent event, String previousHash) {
        try {
            String canonical = canonicalize(event);
            String occurredAtMicros = toDatabaseMicros(event.getOccurredAt());
            int schemaVersion = event.getSchemaVersion() != null ? event.getSchemaVersion() : 2;

            StringBuilder input = new StringBuilder(canonical)
                    .append("|previousHash=").append(nullToEmpty(previousHash))
                    .append("|tenantId=").append(nullToEmpty(
                            event.getTenantId() == null ? null : event.getTenantId().toString()))
                    .append("|occurredAt=").append(occurredAtMicros);

            if (schemaVersion >= 2) {
                input.append("|sequenceNumber=")
                        .append(event.getSequenceNumber() == null ? "" : event.getSequenceNumber().toString());
            }

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Normalizes an instant exactly as PostgreSQL/JDBC persists timestamp
     * values: nearest microsecond, including carry into the next second.
     * Hashing and persistence must use the same value or a reload can appear
     * tampered even when no data changed.
     */
    public Instant normalizeToDatabasePrecision(Instant instant) {
        if (instant == null) return null;

        long roundedMicrosWithinSecond = (instant.getNano() + 500L) / 1_000L;
        long secondCarry = roundedMicrosWithinSecond / MICROS_PER_SECOND;
        long microsWithinSecond = roundedMicrosWithinSecond % MICROS_PER_SECOND;
        return Instant.ofEpochSecond(
                instant.getEpochSecond() + secondCarry,
                microsWithinSecond * 1_000L);
    }

    /**
     * Returns the normalized PostgreSQL epoch-microsecond representation.
     * Kept package-visible for deterministic tests.
     */
    String truncateToMicros(Instant instant) {
        return toDatabaseMicros(instant);
    }

    private String toDatabaseMicros(Instant instant) {
        if (instant == null) return "";
        Instant normalized = normalizeToDatabasePrecision(instant);
        long micros = normalized.getEpochSecond() * MICROS_PER_SECOND
                + normalized.getNano() / 1_000L;
        return Long.toString(micros);
    }

    /**
     * Verifies that the stored {@code eventHash} matches a recomputed hash.
     */
    public boolean verifyEventHash(AuditEvent event, String expectedPreviousHash) {
        String recomputed = computeEventHash(event, expectedPreviousHash);
        return recomputed.equals(event.getEventHash());
    }

    private String canonicalize(AuditEvent event) {
        StringBuilder sb = new StringBuilder();
        appendSorted(sb, "action", event.getAction());
        appendSorted(sb, "actorDisplayName", event.getActorDisplayName());
        appendSorted(sb, "actorService", event.getActorService());
        appendSorted(sb, "actorType", event.getActorType() == null ? null : event.getActorType().name());
        appendSorted(sb, "actorUserId", event.getActorUserId() == null ? null : event.getActorUserId().toString());
        appendSorted(sb, "afterState", event.getAfterState());
        appendSorted(sb, "beforeState", event.getBeforeState());
        appendSorted(sb, "category", event.getCategory());
        appendSorted(sb, "changedFields", event.getChangedFields());
        appendSorted(sb, "channel", event.getChannel());
        appendSorted(sb, "correlationId", event.getCorrelationId());
        appendSorted(sb, "errorCode", event.getErrorCode());
        appendSorted(sb, "failureReason", event.getFailureReason());
        appendSorted(sb, "httpStatus", event.getHttpStatus() == null ? null : event.getHttpStatus().toString());
        appendSorted(sb, "jwtId", event.getJwtId());
        appendSorted(sb, "metadata", event.getMetadata());
        appendSorted(sb, "operation", event.getOperation());
        appendSorted(sb, "outcome", event.getOutcome() == null ? null : event.getOutcome().name());
        appendSorted(sb, "requestId", event.getRequestId());
        appendSorted(sb, "resourceId", event.getResourceId());
        appendSorted(sb, "resourceType", event.getResourceType());
        appendSorted(sb, "sessionId", event.getSessionId());
        appendSorted(sb, "sourceIp", event.getSourceIp());
        appendSorted(sb, "traceId", event.getTraceId());
        appendSorted(sb, "userAgent", event.getUserAgent());
        return sb.toString();
    }

    private void appendSorted(StringBuilder sb, String key, String value) {
        sb.append(key).append("=").append(nullToEmpty(value)).append(";");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
