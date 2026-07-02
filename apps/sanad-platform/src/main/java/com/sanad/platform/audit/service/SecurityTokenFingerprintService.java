package com.sanad.platform.audit.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stage 05A.2.9 §6 — Computes a safe SHA-256 fingerprint of a token
 * for storage in platform security audit events.
 *
 * <p>Never stores the raw token, token prefix, or token suffix.
 * The fingerprint is a 64-character lowercase hex string.</p>
 */
@Component
public class SecurityTokenFingerprintService {

    /**
     * Computes SHA-256(token) as a 64-char lowercase hex string.
     *
     * @param rawToken the raw token (may be null)
     * @return the fingerprint, or null if the input is null/blank
     */
    public String fingerprint(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
