package com.sanad.platform.crm.legacy.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class LegacyEncryptionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final SecretKeySpec customFieldKey;

    public LegacyEncryptionService(
            @Value("${sanad.crm.custom-field-encryption-key:}") String encryptionKey,
            Environment environment) {
        this.customFieldKey = CrmEncryptionKeyValidator.resolve(environment, encryptionKey);
    }

    public String encryptSensitive(String plaintext) {
        if (customFieldKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CRM custom-field encryption key is not configured");
        }
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, customFieldKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return "enc:v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt CRM custom field", exception);
        }
    }

    public String decryptSensitive(String encoded) {
        if (customFieldKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CRM custom-field encryption key is not configured");
        }
        if (!encoded.startsWith("enc:v1:")) {
            throw new IllegalStateException("Unsupported CRM custom-field ciphertext");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring("enc:v1:".length()));
            if (payload.length < 13) throw new GeneralSecurityException("ciphertext too short");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, customFieldKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt CRM custom field", exception);
        }
    }
}
