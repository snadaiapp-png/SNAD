package com.sanad.platform.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads key material from environment variables.
 *
 * <p>Configuration keys (read from application.yml / env vars):
 * <ul>
 *   <li>{@code sanad.security.crypto.encryption-key-version} (default: v1)</li>
 *   <li>{@code sanad.security.crypto.encryption-key} (HRM_PII_ENCRYPTION_KEY)</li>
 *   <li>{@code sanad.security.crypto.blind-index-key-version} (default: v1)</li>
 *   <li>{@code sanad.security.crypto.blind-index-key} (HRM_PII_BLIND_INDEX_KEY)</li>
 * </ul>
 *
 * <p>Production fails-closed: if the key is empty, operations throw IllegalStateException.
 * Local/test may provide ephemeral test keys via env vars.</p>
 */
@Component
public class EnvironmentKeyMaterialProvider implements KeyMaterialProvider {

    private final String encryptionKeyVersion;
    private final String encryptionKey;
    private final String blindIndexKeyVersion;
    private final String blindIndexKey;

    public EnvironmentKeyMaterialProvider(
            @Value("${sanad.security.crypto.encryption-key-version:v1}") String encryptionKeyVersion,
            @Value("${sanad.security.crypto.encryption-key:}") String encryptionKey,
            @Value("${sanad.security.crypto.blind-index-key-version:v1}") String blindIndexKeyVersion,
            @Value("${sanad.security.crypto.blind-index-key:}") String blindIndexKey) {
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.encryptionKey = encryptionKey != null ? encryptionKey.trim() : "";
        this.blindIndexKeyVersion = blindIndexKeyVersion;
        this.blindIndexKey = blindIndexKey != null ? blindIndexKey.trim() : "";
    }

    @Override
    public String getEncryptionKey(String keyVersion) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "Platform encryption key is not configured (HRM_PII_ENCRYPTION_KEY). "
                    + "Production must fail-closed when key material is absent.");
        }
        return encryptionKey;
    }

    @Override
    public String getBlindIndexKey(String keyVersion) {
        if (blindIndexKey == null || blindIndexKey.isBlank()) {
            throw new IllegalStateException(
                    "Platform blind-index key is not configured (HRM_PII_BLIND_INDEX_KEY). "
                    + "Production must fail-closed when key material is absent.");
        }
        return blindIndexKey;
    }

    @Override
    public String getCurrentEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    @Override
    public String getCurrentBlindIndexKeyVersion() {
        return blindIndexKeyVersion;
    }
}
