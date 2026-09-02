package com.sanad.platform.security.crypto;

/**
 * Provides key material for encryption and blind-index operations.
 *
 * <p>Implementations must NOT store keys in source code, database tables,
 * logs, or error output. Production must fail-closed if required material
 * is absent.</p>
 */
public interface KeyMaterialProvider {

    /**
     * Returns the base64-encoded 32-byte AES-256 encryption key for the given version.
     *
     * @throws IllegalStateException if the key is not configured (fail-closed)
     */
    String getEncryptionKey(String keyVersion);

    /**
     * Returns the base64-encoded 32-byte HMAC-SHA-256 blind-index key for the given version.
     *
     * @throws IllegalStateException if the key is not configured (fail-closed)
     */
    String getBlindIndexKey(String keyVersion);

    /**
     * Returns the current encryption key version.
     */
    String getCurrentEncryptionKeyVersion();

    /**
     * Returns the current blind-index key version.
     */
    String getCurrentBlindIndexKeyVersion();
}
