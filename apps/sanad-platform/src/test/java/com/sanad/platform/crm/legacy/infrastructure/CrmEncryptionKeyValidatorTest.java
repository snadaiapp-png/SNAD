package com.sanad.platform.crm.legacy.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

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

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String VALID_KEY = Base64.getEncoder().encodeToString(sequenceBytes(32));

    private static byte[] sequenceBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }

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

        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test default key");
    }

    @Test
    void rejectsTriviallyWeakKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        byte[] weakBytes = new byte[32];
        Arrays.fill(weakBytes, (byte) 1);
        String weakKey = Base64.getEncoder().encodeToString(weakBytes);

        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, weakKey))
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

        String invalidLengthKey = Base64.getEncoder().encodeToString(sequenceBytes(20));
        assertThatThrownBy(() -> CrmEncryptionKeyValidator.resolve(env, invalidLengthKey))
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
