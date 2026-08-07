package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast guard that prevents the application from starting in the
 * {@code prod} profile when any intelligence provider is configured to use
 * mock data.
 *
 * <p>Mock adapters return fabricated financial, commerce, ERP, HRM, and POS
 * data.  Using them in production would surface fake data to real users and
 * could mask integration failures.</p>
 *
 * <p>This guard checks all five {@code sanad.intelligence.*.provider}
 * properties and throws {@link IllegalStateException} if any is set to
 * {@code "mock"}.</p>
 */
public class ProductionMockGuard implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProductionMockGuard.class);

    private static final String[] INTELLIGENCE_PROVIDER_PROPERTIES = {
            "sanad.intelligence.accounting.provider",
            "sanad.intelligence.commerce.provider",
            "sanad.intelligence.erp.provider",
            "sanad.intelligence.hrm.provider",
            "sanad.intelligence.pos.provider"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProdProfile(environment)) {
            return;
        }

        // Allow skipping the guard via env var (for emergency startup)
        String skipGuard = environment.getProperty("SKIP_MOCK_GUARD");
        if ("true".equalsIgnoreCase(skipGuard)) {
            log.warn("Production mock guard SKIPPED (SKIP_MOCK_GUARD=true)");
            return;
        }

        StringBuilder errors = new StringBuilder();

        for (String property : INTELLIGENCE_PROVIDER_PROPERTIES) {
            String value = environment.getProperty(property);
            if ("mock".equalsIgnoreCase(value)) {
                errors.append("  - ").append(property).append(" = mock\n");
            }
        }

        if (errors.length() > 0) {
            throw new IllegalStateException(
                    "FATAL: Intelligence provider(s) configured to use mock data in production:\n"
                    + errors
                    + "Set these properties to 'http' (or another real provider) via environment variables:\n"
                    + "  SANAD_INTELLIGENCE_ACCOUNTING_PROVIDER=http\n"
                    + "  SANAD_INTELLIGENCE_COMMERCE_PROVIDER=http\n"
                    + "  SANAD_INTELLIGENCE_ERP_PROVIDER=http\n"
                    + "  SANAD_INTELLIGENCE_HRM_PROVIDER=http\n"
                    + "  SANAD_INTELLIGENCE_POS_PROVIDER=http\n"
                    + "Or set SKIP_MOCK_GUARD=true to bypass this check (NOT recommended).");
        }

        log.info("Production mock guard: PASS — no mock intelligence providers detected");
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
