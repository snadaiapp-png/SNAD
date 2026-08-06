package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast guard that prevents the application from starting in the
 * {@code prod} profile when critical security configuration is missing
 * or insecure.
 *
 * <p>This guard addresses CRM-032 HIGH-01 and HIGH-02 findings:</p>
 *
 * <h3>HIGH-01: Test Encryption Key</h3>
 * <p>Blocks startup if {@code sanad.crm.custom-field-encryption-key}
 * is set to the test default value {@code AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}
 * (32 zero bytes encoded in base64). Production must configure a real
 * encryption key via the {@code CRM_CUSTOM_FIELD_ENCRYPTION_KEY}
 * environment variable.</p>
 *
 * <h3>HIGH-02: Production Security Features</h3>
 * <p>Blocks startup if critical security features are misconfigured:</p>
 * <ul>
 *   <li>Row-Level Security ({@code snad.rls.enabled}) is disabled</li>
 *   <li>Actuator endpoints expose sensitive information ({@code env}, {@code beans})</li>
 * </ul>
 */
public class ProductionSecurityGuard implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityGuard.class);

    /**
     * The test/default encryption key value (32 zero bytes encoded in base64).
     * This key MUST NOT be used in production.
     */
    private static final String TEST_ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProdProfile(environment)) {
            return;
        }

        // Allow skipping the guard via env var (for emergency startup)
        String skipGuard = environment.getProperty("SKIP_SECURITY_GUARD");
        if ("true".equalsIgnoreCase(skipGuard)) {
            log.warn("Production security guard SKIPPED (SKIP_SECURITY_GUARD=true)");
            return;
        }

        // Validate HIGH-01: Test Encryption Key
        validateEncryptionKey(environment);

        // Validate HIGH-02: Production Security Features
        validateRlsEnabled(environment);
        validateActuatorEndpoints(environment);

        log.info("Production security guard: PASS");
    }

    /**
     * HIGH-01: Validate that the encryption key is not the test default.
     */
    private void validateEncryptionKey(ConfigurableEnvironment environment) {
        String encryptionKey = environment.getProperty("sanad.crm.custom-field-encryption-key");

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "FATAL: CRM custom-field encryption key is not configured. "
                    + "Set CRM_CUSTOM_FIELD_ENCRYPTION_KEY environment variable to a valid "
                    + "base64-encoded AES-128/192/256 key. "
                    + "This key is required for encrypting sensitive custom field values.");
        }

        if (TEST_ENCRYPTION_KEY.equals(encryptionKey)) {
            throw new IllegalStateException(
                    "FATAL: CRM custom-field encryption key is set to the test/default value "
                    + "(AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=). "
                    + "This key MUST NOT be used in production. "
                    + "Set CRM_CUSTOM_FIELD_ENCRYPTION_KEY environment variable to a unique, "
                    + "secure key generated for this deployment. "
                    + "See: docs/security/ENCRYPTION-KEY-MANAGEMENT.md");
        }

        // Validate key length (AES-128/192/256 requires 16/24/32 bytes)
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(encryptionKey);
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalStateException(
                        "FATAL: CRM custom-field encryption key has invalid length. "
                        + "Expected 16, 24, or 32 bytes (AES-128/192/256), got " + keyBytes.length + " bytes. "
                        + "Set CRM_CUSTOM_FIELD_ENCRYPTION_KEY to a valid base64-encoded AES key.");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "FATAL: CRM custom-field encryption key is not valid base64. "
                    + "Set CRM_CUSTOM_FIELD_ENCRYPTION_KEY to a valid base64-encoded AES key.", e);
        }

        log.info("Production security guard: Encryption key validated (not test default, valid length)");
    }

    /**
     * HIGH-02: Validate that Row-Level Security is enabled.
     */
    private void validateRlsEnabled(ConfigurableEnvironment environment) {
        String rlsEnabled = environment.getProperty("snad.rls.enabled");

        if ("false".equalsIgnoreCase(rlsEnabled)) {
            throw new IllegalStateException(
                    "FATAL: Row-Level Security (RLS) is disabled in production. "
                    + "Set snad.rls.enabled=true to enable multi-tenant isolation at the database level. "
                    + "RLS is a critical security control for tenant data isolation. "
                    + "Disabling RLS in production exposes all tenants to cross-tenant data leakage.");
        }

        log.info("Production security guard: RLS enabled");
    }

    /**
     * HIGH-02: Validate that actuator endpoints are not over-exposed.
     */
    private void validateActuatorEndpoints(ConfigurableEnvironment environment) {
        String exposure = environment.getProperty("management.endpoints.web.exposure.include");

        if (exposure == null || exposure.isBlank()) {
            // Default is typically just health, which is safe
            log.info("Production security guard: Actuator endpoints using default (safe)");
            return;
        }

        // Check for sensitive endpoints that should never be exposed in production
        String lowerExposure = exposure.toLowerCase();
        String[] sensitiveEndpoints = {"env", "beans", "configprops", "heapdump", "threaddump", "shutdown"};

        for (String endpoint : sensitiveEndpoints) {
            if (lowerExposure.contains(endpoint)) {
                throw new IllegalStateException(
                        "FATAL: Actuator endpoint '" + endpoint + "' is exposed in production. "
                        + "This endpoint can leak sensitive configuration, environment variables, or internal state. "
                        + "Set management.endpoints.web.exposure.include to a safe value "
                        + "(e.g., 'health,info'). "
                        + "Current value: " + exposure);
            }
        }

        log.info("Production security guard: Actuator endpoints validated (no sensitive endpoints exposed)");
    }

    private boolean isProdProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        String profilesEnv = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        if (profilesEnv.isBlank()) {
            return false;
        }
        for (String p : profilesEnv.split(",")) {
            if ("prod".equals(p.trim())) {
                return true;
            }
        }
        return false;
    }
}
