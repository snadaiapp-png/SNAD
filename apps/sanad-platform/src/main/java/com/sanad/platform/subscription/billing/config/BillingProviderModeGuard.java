package com.sanad.platform.subscription.billing.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Locale;

/**
 * Early fail-closed configuration guard for R0C13 payment-provider modes.
 *
 * <p>R0C13 engineering does not grant live-payment authority. Therefore LIVE
 * is rejected at startup in every profile. TEST is allowed only outside prod
 * and only when selected explicitly. DISABLED is the production-safe default.</p>
 */
public class BillingProviderModeGuard implements EnvironmentPostProcessor {

    public static final String MODE_PROPERTY =
            "sanad.subscription.billing.provider.mode";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        String mode = environment.getProperty(MODE_PROPERTY, "DISABLED");
        mode = mode == null ? "DISABLED" : mode.trim().toUpperCase(Locale.ROOT);

        switch (mode) {
            case "DISABLED" -> {
                return;
            }
            case "TEST" -> {
                if (isProd(environment)) {
                    throw new IllegalStateException(
                            "R0C13 billing provider TEST mode is forbidden in the prod profile");
                }
            }
            case "LIVE" -> throw new IllegalStateException(
                    "R0C13 billing provider LIVE mode is not authorized. "
                            + "Live payment collection requires a separate human activation gate "
                            + "outside R0C13 engineering closure.");
            default -> throw new IllegalStateException(
                    "Unsupported R0C13 billing provider mode: " + mode
                            + ". Allowed engineering modes are DISABLED or explicit non-prod TEST.");
        }
    }

    private static boolean isProd(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        String configured = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        for (String profile : configured.split(",")) {
            if ("prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
