package com.sanad.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProductionSecurityGuard}.
 *
 * <p>Verifies that the guard blocks startup when critical security
 * configuration is missing or insecure in the prod profile.</p>
 */
class ProductionSecurityGuardTest {

    private final ProductionSecurityGuard guard = new ProductionSecurityGuard();

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String VALID_KEY = buildValidKey();

    private static String buildValidKey() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void skipsGuardForNonProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("sanad.crm.custom-field-encryption-key", VALID_KEY);

        // Should not throw - guard only activates for prod profile
        guard.postProcessEnvironment(env, null);
    }

    @Test
    void blocksOnTestEncryptionKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("sanad.crm.custom-field-encryption-key", TEST_KEY);
        env.setProperty("snad.rls.enabled", "true");

        assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test/default value")
                .hasMessageContaining(TEST_KEY);
    }

    @Test
    void blocksOnMissingEncryptionKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("snad.rls.enabled", "true");

        assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void blocksOnInvalidEncryptionKeyLength() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("sanad.crm.custom-field-encryption-key", "dGVzdA=="); // 4 bytes - invalid
        env.setProperty("snad.rls.enabled", "true");

        assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid length");
    }

    @Test
    void blocksOnRlsDisabled() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("sanad.crm.custom-field-encryption-key", VALID_KEY);
        env.setProperty("snad.rls.enabled", "false");

        assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Row-Level Security (RLS) is disabled");
    }

    @Test
    void blocksOnSensitiveActuatorEndpoint() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("sanad.crm.custom-field-encryption-key", VALID_KEY);
        env.setProperty("snad.rls.enabled", "true");
        env.setProperty("management.endpoints.web.exposure.include", "health,env,beans");

        assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("env")
                .hasMessageContaining("exposed in production");
    }

    @Test
    void passesWithValidConfiguration() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("sanad.crm.custom-field-encryption-key", VALID_KEY);
        env.setProperty("snad.rls.enabled", "true");
        env.setProperty("management.endpoints.web.exposure.include", "health,info");

        // Should not throw
        guard.postProcessEnvironment(env, null);
    }

    @Test
    void allowsSkipGuard() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("SKIP_SECURITY_GUARD", "true");
        // Invalid config should be ignored when guard is skipped
        env.setProperty("sanad.crm.custom-field-encryption-key", TEST_KEY);

        // Should not throw
        guard.postProcessEnvironment(env, null);
    }
}
