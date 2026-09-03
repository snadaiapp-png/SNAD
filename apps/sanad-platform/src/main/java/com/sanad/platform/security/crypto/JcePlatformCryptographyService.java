package com.sanad.platform.security.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * JCE-based implementation of PlatformCryptographyService.
 *
 * <p>Uses AES/GCM/NoPadding with 256-bit key, 12-byte random nonce, 128-bit auth tag.
 * AAD = tenantId|purpose|keyVersion (bound to ciphertext).</p>
 *
 * <p>Blind index uses HmacSHA256 with a SEPARATE key (never the encryption key).
 * Index input = tenantId|purpose|normalizedValue.</p>
 *
 * <p>Encrypted payload format: {@code enc:<keyVersion>:<base64(nonce+ciphertext+tag)>}</p>
 */
@Service
public class JcePlatformCryptographyService implements PlatformCryptographyService {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String BLIND_INDEX_ALGORITHM = "HmacSHA256";
    private static final int NONCE_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 128;

    private final KeyMaterialProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public JcePlatformCryptographyService(KeyMaterialProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public EncryptedValue encrypt(UUID tenantId, String purpose, String plaintext) {
        String keyVersion = keyProvider.getCurrentEncryptionKeyVersion();
        String base64Key = keyProvider.getEncryptionKey(keyVersion);
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        byte[] aad = buildAad(tenantId, purpose, keyVersion);

        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(AUTH_TAG_LENGTH, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine nonce + ciphertext+tag
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);

            String encoded = "enc:" + keyVersion + ":" + Base64.getEncoder().encodeToString(payload);
            return new EncryptedValue(encoded, keyVersion, ENCRYPTION_ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(UUID tenantId, String purpose, EncryptedValue value) {
        // Parse the versioned payload: enc:<keyVersion>:<base64>
        String[] parts = value.ciphertext().split(":", 3);
        if (parts.length != 3 || !"enc".equals(parts[0])) {
            throw new IllegalStateException("Invalid encrypted value format");
        }
        String payloadKeyVersion = parts[1];
        String base64Payload = parts[2];

        String base64Key = keyProvider.getEncryptionKey(payloadKeyVersion);
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        byte[] payload = Base64.getDecoder().decode(base64Payload);

        // Split nonce + ciphertext
        byte[] nonce = new byte[NONCE_LENGTH];
        byte[] ciphertext = new byte[payload.length - NONCE_LENGTH];
        System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH);
        System.arraycopy(payload, NONCE_LENGTH, ciphertext, 0, ciphertext.length);

        // AAD must match what was used during encryption
        byte[] aad = buildAad(tenantId, purpose, payloadKeyVersion);

        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(AUTH_TAG_LENGTH, nonce));
            cipher.updateAAD(aad);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed (AAD mismatch or corrupt data)", e);
        }
    }

    @Override
    public BlindIndex blindIndex(UUID tenantId, String purpose, String normalizedValue) {
        String keyVersion = keyProvider.getCurrentBlindIndexKeyVersion();
        String base64Key = keyProvider.getBlindIndexKey(keyVersion);
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        String input = tenantId + "|" + purpose + "|" + normalizedValue;
        try {
            Mac mac = Mac.getInstance(BLIND_INDEX_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, BLIND_INDEX_ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            String value = Base64.getEncoder().encodeToString(digest);
            return new BlindIndex(value, keyVersion, BLIND_INDEX_ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Blind index computation failed", e);
        }
    }

    private byte[] buildAad(UUID tenantId, String purpose, String keyVersion) {
        return (tenantId + "|" + purpose + "|" + keyVersion).getBytes(StandardCharsets.UTF_8);
    }
}
