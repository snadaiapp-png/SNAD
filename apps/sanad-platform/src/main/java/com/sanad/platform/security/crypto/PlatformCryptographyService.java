package com.sanad.platform.security.crypto;

import java.util.UUID;

/**
 * Platform cryptography service for tenant-scoped, purpose-bound encryption
 * and deterministic blind indexing.
 *
 * <p>Security properties:
 * <ul>
 *   <li>AES-256-GCM authenticated encryption</li>
 *   <li>Random 12-byte nonce per encryption (ciphertext is randomized)</li>
 *   <li>AAD binds tenant ID + purpose + key version</li>
 *   <li>Separate HMAC-SHA-256 key path for blind indexes</li>
 *   <li>Key version metadata in encrypted output</li>
 *   <li>Tenant separation via AAD + key derivation context</li>
 *   <li>Purpose binding prevents cross-purpose decryption</li>
 * </ul>
 */
public interface PlatformCryptographyService {

    /**
     * Encrypts plaintext with AES-256-GCM using tenant+purpose+keyVersion as AAD.
     *
     * @return EncryptedValue with versioned payload
     */
    EncryptedValue encrypt(UUID tenantId, String purpose, String plaintext);

    /**
     * Decrypts an EncryptedValue, verifying tenant+purpose+keyVersion AAD.
     *
     * @throws IllegalStateException if AAD mismatch or decryption fails
     */
    String decrypt(UUID tenantId, String purpose, EncryptedValue value);

    /**
     * Produces a deterministic blind index using HMAC-SHA-256 with a separate key.
     *
     * <p>Same tenant+purpose+normalizedValue produces the same index.
     * Different tenants produce different indexes.</p>
     */
    BlindIndex blindIndex(UUID tenantId, String purpose, String normalizedValue);
}
