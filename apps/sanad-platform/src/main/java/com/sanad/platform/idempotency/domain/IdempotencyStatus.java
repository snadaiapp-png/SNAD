package com.sanad.platform.idempotency.domain;

/**
 * Stage 05 §13 — Lifecycle status of an idempotency record.
 */
public enum IdempotencyStatus {

    /** The request is currently being processed by the business layer. */
    PROCESSING,

    /** The request completed successfully and the response is stored for replay. */
    COMPLETED,

    /** The request failed in a retryable way (e.g. timeout, transient error). */
    FAILED_RETRYABLE,

    /** The request failed permanently and cannot be retried with the same key. */
    FAILED_FINAL,

    /** The record has passed its expiration time and is eligible for cleanup. */
    EXPIRED
}
