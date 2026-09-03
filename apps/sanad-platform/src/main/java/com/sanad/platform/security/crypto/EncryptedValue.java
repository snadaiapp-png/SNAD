package com.sanad.platform.security.crypto;

/**
 * Encrypted value with key version and algorithm metadata.
 *
 * <p>The ciphertext field contains a versioned payload encoding
 * (e.g., {@code enc:v1:<base64 nonce+ciphertext+tag>}).</p>
 */
public record EncryptedValue(
        String ciphertext,
        String keyVersion,
        String algorithm
) {}
