package com.sanad.platform.crm.legacy.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Single source of truth for CRM custom-field encryption key resolution
 * (CRM-032 remediation of HIGH-01 / HIGH-02).
 *
 * <p>The well-known test default key and any trivially weak key (all bytes
 * identical) are rejected unconditionally, regardless of profile, so a known
 * key can never protect real data. When no key is configured, non-production
 * environments receive an ephemeral random AES-256 key — the same pattern as
 * {@code JwtTokenProvider}. Production must provide
 * {@code CRM_CUSTOM_FIELD_ENCRYPTION_KEY} or startup is refused by
 * {@code ProductionSecurityGuard}.</p>
 */
public final class CrmEncryptionKeyValidator {

    /** Well-known test/default key previously hardcoded in application-local.yml. */
    public static final String TEST_ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final Logger log = LoggerFactory.getLogger(CrmEncryptionKeyValidator.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<Integer> ALLOWED_KEY_LENGTHS = Set.of(16, 24, 32);

    private CrmEncryptionKeyValidator() {
    }

    /**
     * Resolves the configured key into an AES {@link SecretKeySpec}.
     *
     * @return the configured key, or an ephemeral random key when unconfigured
     *         in a non-production environment
     * @throws IllegalStateException when the key is the known test default, a
     *         trivially weak key, invalid base64, an unsupported length, or
     *         missing in a production environment
     */
    public static SecretKeySpec resolve(Environment environment, String configured) {
        if (configured == null || configured.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException(
                        "FATAL: sanad.crm.custom-field-encryption-key is not configured. "
                                + "Production requires CRM_CUSTOM_FIELD_ENCRYPTION_KEY (CRM-032).");
            }
            byte[] generated = new byte[32];
            SECURE_RANDOM.nextBytes(generated);
            log.warn("CRM custom-field encryption key is empty; generated an ephemeral non-production key.");
            return new SecretKeySpec(generated, "AES");
        }

        String trimmed = configured.trim();
        if (TEST_ENCRYPTION_KEY.equals(trimmed)) {
            throw new IllegalStateException(
                    "sanad.crm.custom-field-encryption-key must not use the known test default key (CRM-032 HIGH-01)");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "sanad.crm.custom-field-encryption-key must be base64 AES-128/192/256 (CRM-032)",
                    exception);
        }
        if (!ALLOWED_KEY_LENGTHS.contains(key.length)) {
            throw new IllegalStateException(
                    "sanad.crm.custom-field-encryption-key must be base64 AES-128/192/256, got "
                            + key.length + " bytes (CRM-032)");
        }
        if (isTriviallyWeakKey(key)) {
            throw new IllegalStateException(
                    "sanad.crm.custom-field-encryption-key is trivially weak "
                            + "(all bytes identical) and is rejected (CRM-032)");
        }
        return new SecretKeySpec(key, "AES");
    }

    private static boolean isTriviallyWeakKey(byte[] key) {
        for (byte value : key) {
            if (value != key[0]) {
                return false;
            }
        }
        return true;
    }
}
