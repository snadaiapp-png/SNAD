package com.sanad.platform.security.crypto;

/**
 * Deterministic blind index for searchable encrypted fields.
 *
 * <p>Uses a separate HMAC-SHA-256 key path — never the encryption key.
 * The index is tenant+purpose-scoped.</p>
 */
public record BlindIndex(
        String value,
        String keyVersion,
        String algorithm
) {}
