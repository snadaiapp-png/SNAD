package com.sanad.platform.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Stage 05A.2.1 §11 — Canonical response serialization for idempotency replay.
 *
 * <p>Serializes the approved response DTO using Jackson ObjectMapper to
 * produce a canonical JSON string. This is stored in the idempotency
 * record and used for replay — NOT the Servlet response capture.</p>
 */
@Component
public class IdempotencyReplaySerializer {

    private final ObjectMapper objectMapper;

    public IdempotencyReplaySerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a response DTO to canonical JSON.
     */
    public String serializeResponse(Object responseDto) {
        if (responseDto == null) return null;
        try {
            return objectMapper.writeValueAsString(responseDto);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    /**
     * Builds the approved response headers string from an allowlist.
     * Only headers in the allowlist are stored for replay.
     */
    public String serializeHeaders(java.util.Map<String, String> approvedHeaders) {
        if (approvedHeaders == null || approvedHeaders.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (var entry : approvedHeaders.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }
}
