package com.sanad.platform.idempotency.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stage 05 §15 — Computes a deterministic SHA-256 fingerprint of a
 * client request for idempotency matching.
 *
 * <p>The fingerprint is computed from:</p>
 * <ul>
 *   <li>HTTP method (uppercase)</li>
 *   <li>Normalized route (without path variables expanded to UUIDs)</li>
 *   <li>Canonical request body (raw bytes, trimmed)</li>
 *   <li>Relevant query parameters (sorted by key)</li>
 *   <li>Authenticated tenant ID</li>
 *   <li>Operation identifier</li>
 * </ul>
 *
 * <p>The fingerprint deliberately EXCLUDES:</p>
 * <ul>
 *   <li>Authorization header</li>
 *   <li>Tracing headers (X-Request-Id, X-Correlation-Id, traceparent)</li>
 *   <li>Retry count</li>
 *   <li>Request ID</li>
 * </ul>
 *
 * <p>Two requests with the same {@code Idempotency-Key} but different
 * fingerprints indicate a payload mismatch and must be rejected with
 * HTTP 409 {@code SANAD-IDEMP-002}.</p>
 */
@Service
public class RequestFingerprintService {

    /**
     * Computes the SHA-256 fingerprint for the given request components.
     *
     * @param method          the HTTP method (e.g. "POST")
     * @param route           the normalized route (e.g. "/api/v1/organizations")
     * @param body            the raw request body (may be empty)
     * @param queryString     the canonical query string (sorted by key), may be null
     * @param tenantId        the authenticated tenant ID
     * @param operation       the operation identifier
     * @return the 64-character hex SHA-256 hash
     */
    public String compute(String method, String route, String body,
                           String queryString, UUID tenantId, String operation) {
        String canonical = buildCanonical(method, route, body, queryString, tenantId, operation);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String buildCanonical(String method, String route, String body,
                                    String queryString, UUID tenantId, String operation) {
        StringBuilder sb = new StringBuilder();
        sb.append("method=").append(method == null ? "" : method.toUpperCase()).append(";");
        sb.append("route=").append(route == null ? "" : route).append(";");
        sb.append("body=").append(body == null ? "" : body.trim()).append(";");
        sb.append("query=").append(queryString == null ? "" : queryString).append(";");
        sb.append("tenant=").append(tenantId == null ? "" : tenantId.toString()).append(";");
        sb.append("operation=").append(operation == null ? "" : operation).append(";");
        return sb.toString();
    }
}
