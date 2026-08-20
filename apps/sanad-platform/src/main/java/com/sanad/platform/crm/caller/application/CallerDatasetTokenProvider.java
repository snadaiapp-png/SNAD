package com.sanad.platform.crm.caller.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Secure caller-dataset lookup tokens (G8-03 §33–§35, G8-ADR-004).
 *
 * <p>{@code lookupToken = HMAC-SHA256(normalizedE164, tenantDatasetKey)} where
 * {@code tenantDatasetKey = HMAC-SHA256(tenantId, masterKey)}. The master key
 * NEVER leaves the server (env {@code CALLER_DATASET_MASTER_KEY}); the derived
 * per-tenant key is shipped to an authenticated device once (SecureStore) so
 * the device can re-derive tokens for incoming numbers. Unsalted SHA-256 is
 * forbidden (guessable phone space). Fails CLOSED when the master key is not
 * configured.
 */
@Component
public class CallerDatasetTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CallerDatasetTokenProvider.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String masterKey;

    public CallerDatasetTokenProvider(
            @Value("${sanad.caller-dataset.master-key:}") String masterKey) {
        this.masterKey = masterKey;
    }

    /** Per-tenant dataset key (hex) — TENANT-BOUND and deterministic. */
    public String tenantDatasetKey(UUID tenantId) {
        requireMasterKey();
        return hmac(masterKey.getBytes(StandardCharsets.UTF_8), tenantId.toString());
    }

    /** Deterministic, practically non-reversible lookup token for a normalized E.164. */
    public String lookupToken(UUID tenantId, String normalizedE164) {
        String tenantKey = tenantDatasetKey(tenantId);
        return hmac(tenantKey.getBytes(StandardCharsets.UTF_8), normalizedE164);
    }

    public boolean isConfigured() {
        return masterKey != null && !masterKey.isBlank();
    }

    private void requireMasterKey() {
        if (!isConfigured()) {
            log.error("CALLER_DATASET_MASTER_KEY is not configured — caller dataset sync is disabled (fail closed).");
            throw new IllegalStateException(
                    "Caller dataset HMAC master key is not configured. Set CALLER_DATASET_MASTER_KEY.");
        }
    }

    private static String hmac(byte[] keyBytes, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute caller dataset token.", exception);
        }
    }
}
