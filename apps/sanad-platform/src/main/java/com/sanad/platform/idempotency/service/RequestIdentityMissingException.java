package com.sanad.platform.idempotency.service;

/**
 * Stage 05A.2.6 §4 — Thrown when no verified request identity is available.
 */
public class RequestIdentityMissingException extends RuntimeException {
    public RequestIdentityMissingException(String message) {
        super(message);
    }
}
