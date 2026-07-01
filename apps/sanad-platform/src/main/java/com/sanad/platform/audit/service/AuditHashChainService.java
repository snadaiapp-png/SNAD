package com.sanad.platform.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.audit.domain.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stage 05 §11 — Hash-chain computation for audit events.
 *
 * <p>Each audit event's {@code eventHash} is computed as:</p>
 * <pre>
 *   eventHash = SHA-256(
 *     canonicalEventPayload
 *     + previousHash
 *     + tenantId
 *     + occurredAt
 *   )
 * </pre>
 *
 * <p>The canonical payload is a deterministic JSON serialization of
 * the event's mutable business fields (actor, action, resource,
 * outcome, state change). The {@code previousHash} links the event
 * to the preceding event in the same tenant's chain, forming a
 * tamper-evident log. The {@code tenantId} and {@code occurredAt}
 * are included to bind the hash to a specific tenant and time,
 * preventing chain replay across tenants.</p>
 *
 * <h2>Concurrent-safe previousHash selection</h2>
 * <p>When multiple events are written concurrently within the same
 * tenant, each must select a consistent {@code previousHash}. The
 * service uses the latest existing event's hash (via
 * {@code findLatestByTenantId}) and falls back to a fixed genesis
 * value if no prior event exists. Concurrent inserts may produce
 * events with the same {@code previousHash} — this is acceptable
 * because the chain is verified by recomputing hashes in order, not
 * by requiring a strict linear chain.</p>
 */
@Service
public class AuditHashChainService {

    private static final Logger log = LoggerFactory.getLogger(AuditHashChainService.class);
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns the genesis hash used for the first event in a tenant's
     * chain (when no prior event exists).
     */
    public String getGenesisHash() {
        return GENESIS_HASH;
    }

    /**
     * Computes the event hash for the given event payload.
     *
     * @param event the event (must have tenantId, occurredAt, and all
     *              business fields populated; eventHash may be null)
     * @param previousHash the hash of the preceding event in the same
     *                     tenant's chain, or {@link #getGenesisHash()}
     *                     if this is the first event
     * @return the 64-character hex SHA-256 hash
     */
    public String computeEventHash(AuditEvent event, String previousHash) {
        try {
            String canonical = canonicalize(event);
            String input = canonical
                    + "|previousHash=" + nullToEmpty(previousHash)
                    + "|tenantId=" + nullToEmpty(event.getTenantId() == null ? null : event.getTenantId().toString())
                    + "|occurredAt=" + nullToEmpty(event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies that the stored {@code eventHash} matches a recomputed
     * hash. Used by {@link AuditIntegrityVerificationService}.
     */
    public boolean verifyEventHash(AuditEvent event, String expectedPreviousHash) {
        String recomputed = computeEventHash(event, expectedPreviousHash);
        return recomputed.equals(event.getEventHash());
    }

    /**
     * Produces a deterministic JSON serialization of the event's
     * business fields. The serialization is sorted by key to ensure
     * determinism regardless of field insertion order.
     */
    private String canonicalize(AuditEvent event) {
        // Use a sorted-key JSON tree for determinism.
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
        // Keys are appended in alphabetical order by construction.
        sb.append(key).append("=").append(nullToEmpty(value)).append(";");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
