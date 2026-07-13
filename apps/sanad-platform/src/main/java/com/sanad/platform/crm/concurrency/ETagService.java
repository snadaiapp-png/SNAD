package com.sanad.platform.crm.concurrency;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * CRM API Contract — ETag + If-Match Concurrency Service.
 * <p>
 * ETags are derived as the SHA-256 of {@code "<entityType>:<uuid>:<version>"}.
 * The {@code entityType} prefix prevents ETag reuse across entity types
 * (an Account ETag cannot be used to update an Opportunity).
 * <p>
 * Contract:
 *   - Single-record GET endpoints emit an {@code ETag} header.
 *   - PATCH endpoints accept an {@code If-Match} header and reject the
 *     request with {@code 412 Precondition Failed}
 *     ({@link CrmErrorCode#CRM_CONCURRENCY_CONFLICT}) when the supplied
 *     ETag does not match the current row's version.
 *   - When the client omits {@code If-Match} on a PATCH, the request is
 *     rejected with {@code VALIDATION_ERROR} — concurrency protection is
 *     mandatory, not opt-in.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@Component
public class ETagService {

    /**
     * Compute the strong ETag for the given entity. The version is the
     * row's {@code version} column (a monotonically increasing long).
     */
    public String etag(String entityType, UUID id, long version) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        String material = entityType.toLowerCase() + ":" + id + ":" + version;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest, 0, 8); // 8 bytes = 16 hex chars
            return "\"" + entityType.toLowerCase() + "-" + id + "-v" + version + "-" + hex + "\"";
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JCA spec; this should never happen.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * Validate the {@code If-Match} header against the current ETag. Throws
     * {@link CrmContractException} with {@link CrmErrorCode#VALIDATION_ERROR}
     * if the header is missing, or {@link CrmErrorCode#CRM_CONCURRENCY_CONFLICT}
     * (HTTP 412) if it does not match.
     */
    public void validateIfMatch(String ifMatchHeader, String entityType, UUID id, long currentVersion) {
        if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
            throw new CrmContractException(CrmErrorCode.CRM_PRECONDITION_REQUIRED);
        }
        // RFC 7232 allows a comma-separated list; treat any match as success.
        String[] candidates = ifMatchHeader.split(",");
        String current = etag(entityType, id, currentVersion);
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed) || current.equals(trimmed)) {
                return;
            }
        }
        throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT,
                "The resource was modified by another operation.",
                null);
    }
}
