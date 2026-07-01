package com.sanad.platform.idempotency.service;

/**
 * Stage 05A.2.1 §7 — Thrown when a worker attempts to complete or fail
 * an idempotency record whose lease is no longer owned by that worker.
 *
 * <p>This happens when:
 * <ul>
 *   <li>Worker A reserved the record (lease_version = 1)</li>
 *   <li>Worker A's lease expired</li>
 *   <li>Worker B took over (lease_version = 2)</li>
 *   <li>Worker A attempts to complete → rejected because lease_version
 *       doesn't match</li>
 * </ul>
 *
 * <p>The fencing token (lease_version) prevents stale workers from
 * corrupting the idempotency state.</p>
 */
public class StaleIdempotencyLeaseException extends RuntimeException {

    private final java.util.UUID recordId;
    private final long expectedLeaseVersion;
    private final long actualLeaseVersion;

    public StaleIdempotencyLeaseException(java.util.UUID recordId,
                                            long expectedLeaseVersion,
                                            long actualLeaseVersion) {
        super("Stale idempotency lease: recordId=" + recordId
                + " expectedLeaseVersion=" + expectedLeaseVersion
                + " actualLeaseVersion=" + actualLeaseVersion);
        this.recordId = recordId;
        this.expectedLeaseVersion = expectedLeaseVersion;
        this.actualLeaseVersion = actualLeaseVersion;
    }

    public java.util.UUID getRecordId() { return recordId; }
    public long getExpectedLeaseVersion() { return expectedLeaseVersion; }
    public long getActualLeaseVersion() { return actualLeaseVersion; }
}
