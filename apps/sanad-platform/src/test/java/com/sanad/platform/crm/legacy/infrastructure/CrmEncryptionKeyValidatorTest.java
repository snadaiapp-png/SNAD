package com.sanad.platform.crm.legacy.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CrmEncryptionKeyValidator} (CRM-032).
 *
 * <p>Verifies the well-known test key and trivially weak keys are rejected
 * unconditionally, production requires a configured key, and non-production
 * environments receive an ephemeral key when unconfigured.</p>
 */
class CrmEncryptionKeyValidatorTest {

    // Well-known test/default key previously hardcoded in application-local.yml
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    // Valid 32-byte AES-256 key (base64): "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnop"
    private static final String VALID_KEY = "QUJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";

    @Test
    void rejectsKnownTestKeyInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test default key");
    }

    @Test
    void rejectsKnownTestKeyInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        // Rejection is unconditional — a known key must never protect data,
        // even outside production.
        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test default key");
    }

    @Test
    void rejectsTriviallyWeakKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        // Base64 of 32 identical bytes (0x01) — trivially weak.
        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trivially weak");
    }

    @Test
    void rejectsInvalidBase64() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, "not-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void rejectsInvalidKeyLength() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        // "12345678901234567890" is 20 bytes — not AES-128/192/256.
        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("20 bytes");
    }

    @Test
    void rejectsMissingKeyInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void generatesEphemeralKeyWhenMissingInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        SecretKeySpec key = CrmEncryptionKeyValidator.resolve(env, "  ");

        assertThat(key).isNotNull();
        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    void acceptsValidKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecretKeySpec key = CrmEncryptionKeyValidator.resolve(env, VALID_KEY);

        assertThat(key).isNotNull();
        assertThat(key.getEncoded()).hasSize(32);
    }
}
